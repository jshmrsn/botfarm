package botfarm.engine.simulation

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.putObject
import aws.smithy.kotlin.runtime.content.ByteStream
import botfarmshared.engine.apidata.EntityId
import botfarmshared.engine.apidata.SimulationId
import botfarmshared.misc.DynamicSerialization
import botfarmshared.misc.buildShortRandomIdentifier
import botfarmshared.misc.getCurrentUnixTimeSeconds
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.writeText
import kotlin.math.roundToInt

@JvmInline
@Serializable
value class UserId(val value: String)

@JvmInline
@Serializable
value class UserSecret(val value: String)

@JvmInline
@Serializable
value class ClientId(val value: String)

class Client(
   val webSocketSession: DefaultWebSocketServerSession,
   val clientId: ClientId,
   val userId: UserId,
   val userSecret: UserSecret
) {
   var lastReceivedMessageUnixTime = getCurrentUnixTimeSeconds()
      private set

   var lastReceivedInteractionUnixTime = getCurrentUnixTimeSeconds()
      private set

   fun notifyMessageReceived() {
      this.lastReceivedMessageUnixTime = getCurrentUnixTimeSeconds()
   }


   fun notifyInteractionReceived() {
      this.lastReceivedInteractionUnixTime = getCurrentUnixTimeSeconds()
   }
}

@Serializable
class ReplaySentMessage(
   val message: JsonElement,
   val simulationTime: Double
)


@Serializable
class SimulationInfo(
   val simulationId: SimulationId,
   val scenarioInfo: ScenarioInfo,
   val isTerminated: Boolean,
   val startedAtUnixTime: Double,
   val belongsToUser: Boolean,
   val wasCreatedByAdmin: Boolean
)

@Serializable
class ReplayData(
   val compatibilityVersion: Int = 1,
   val simulationStartedAtUnixTime: Double,
   val replayGeneratedAtSimulationTime: Double,
   val scenarioInfo: ScenarioInfo,
   val configs: List<JsonElement>,
   val simulationId: SimulationId,
   val sentMessages: List<ReplaySentMessage>
)

class PendingReplayData {
   val sentMessages = mutableListOf<ReplaySentMessage>()
}

class SimulationContext(
   val coroutineScope: CoroutineScope,
   val clientReceiveInteractionTimeoutSeconds: Double?,
   val clientReceiveMessageTimeoutSeconds: Double?,
   val noClientsConnectedTerminationTimeoutSeconds: Double?,
   val wasCreatedByAdmin: Boolean,
   val simulationContainer: SimulationContainer,
   val createdByUserSecret: UserSecret,
   val scenario: Scenario,
   val shouldMinimizeSleep: Boolean
)

open class Simulation(
   val context: SimulationContext,
   val systems: Systems = Systems.default,
   val data: SimulationData
) {
   private var lastAnyClientsConnectedUnixTime = getCurrentUnixTimeSeconds()

   val simulationThread = Thread.currentThread()

   companion object {
      val replayS3UploadBucket = System.getenv()["BOTFARM_S3_REPLAY_BUCKET"]
      val replayS3UploadRegion = System.getenv()["BOTFARM_S3_REPLAY_REGION"] ?: "us-west-2"

      val replayDirectory = if (this.replayS3UploadBucket != null) {
         Path("/tmp/botfarm-simulation-replays")
      } else {
         Paths.get("replays")
      }
   }

   val wasCreatedByAdmin = this.context.wasCreatedByAdmin
   val simulationContainer = this.context.simulationContainer

   val startedAtUnixTime = data.simulationStartedAtUnixTime
   var simulationTime = 0.0
      private set

   var lastTickUnixTime = getCurrentUnixTimeSeconds()

   val scenario = this.context.scenario
   val scenarioInfo: ScenarioInfo = data.scenarioInfo
   val simulationId = data.simulationId
   val configs = data.configs

   val latencyBufferTime = 0.2

   private val mutableEntities: MutableList<Entity>
   val entities: List<Entity>

   private val mutableEntitiesById = mutableMapOf<EntityId, Entity>()
   private val mutableDestroyedEntitiesById = mutableMapOf<EntityId, Entity>()

   private val entitiesById: Map<EntityId, Entity> = this.mutableEntitiesById
   private val destroyedEntitiesById: Map<EntityId, Entity> = this.mutableDestroyedEntitiesById

   private val queuedCallbacks = QueuedCallbacks()

   private val pendingReplayData = PendingReplayData()

   private class RequestFromBackgroundThread(
      val task: () -> Unit,
      val handleException: (Exception) -> Unit
   )

   private val requestsFromBackgroundThread = mutableListOf<RequestFromBackgroundThread>()

   fun addRequestFromBackgroundThread(
      handleException: (Exception) -> Unit = { println("addRequestFromBackgroundThread: Exception while executing task") },
      task: () -> Unit
   ) {
      synchronized(this) {
         this.requestsFromBackgroundThread.add(RequestFromBackgroundThread(
            task = task,
            handleException = handleException
         ))
      }
   }

   fun queueCallbackAfterSimulationTimeDelay(
      simulationTimeDelay: Double,
      isValid: () -> Boolean = { true },
      logic: () -> Unit
   ) {
      this.queueCallbackAtSimulationTime(
         simulationTime = this.getCurrentSimulationTime() + simulationTimeDelay,
         isValid = isValid,
         logic = logic
      )
   }

   fun queueCallbackWithoutDelay(
      isValid: () -> Boolean = { true },
      logic: () -> Unit
   ) {
      this.queuedCallbacks.queueCallback(
         condition = { true },
         isValid = isValid,
         logic = logic
      )
   }

   fun queueCallback(
      condition: () -> Boolean,
      isValid: () -> Boolean = { true },
      logic: () -> Unit
   ) {
      this.queuedCallbacks.queueCallback(
         condition = condition,
         isValid = isValid,
         logic = logic
      )
   }

   fun queueCallbackAtSimulationTime(
      simulationTime: Double,
      isValid: () -> Boolean = { true },
      logic: () -> Unit
   ) {
      this.queuedCallbacks.queueCallback(
         condition = {
            this.getCurrentSimulationTime() >= simulationTime
         },
         isValid = isValid,
         logic = logic
      )
   }

   private val tickSystems = this.systems.registeredTickSystems.map {
      TickSystem(
         registeredTickSystem = it,
         simulation = this
      )
   }

   private val coroutineSystems = this.systems.registeredCoroutineSystems.map {
      CoroutineSystem(
         simulation = this,
         registeredCoroutineSystem = it
      )
   }

   class TickResult(
      val shouldTerminate: Boolean
   )

   private val clients = mutableListOf<Client>()

   init {
      this.mutableEntities = data.entities.map {
         Entity(
            data = it,
            simulation = this
         )
      }.toMutableList()

      this.entities = this.mutableEntities
   }

   open fun onStart() {

   }

   suspend fun startTickingInBackground(
      backgroundTickIntervalMillis: Int = 100
   ) {
      var lastTickUnixTime = getCurrentUnixTimeSeconds()
      while (true) {
         val currentUnixTime = getCurrentUnixTimeSeconds()
         val deltaTime = Math.max(0.0001, currentUnixTime - lastTickUnixTime)
         lastTickUnixTime = currentUnixTime
         val tickResult = this.tick(deltaTime)

         if (tickResult.shouldTerminate) {
            if (!this.isTerminated) {
               this.handleTermination()
               synchronized(this.simulationContainer) {
                  this.simulationContainer.handleSimulationTerminated(
                     simulation = this
                  )
               }
            }

            break
         } else {
            delay(backgroundTickIntervalMillis.toLong())
         }
      }
   }

   private fun sendWebSocketMessage(
      client: Client,
      message: WebSocketMessage
   ) {
      val messageString = DynamicSerialization.serializeToString(message)

      this.sendWebSocketMessage(
         client = client,
         messageString = messageString
      )
   }

   class PendingWebSocketMessage(
      val client: Client,
      val messageString: String
   )

   private val webSocketMessagesToRetry = mutableListOf<PendingWebSocketMessage>()

   private fun sendWebSocketMessage(
      client: Client,
      messageString: String
   ) {
      val webSocketSession = client.webSocketSession

      if (!webSocketSession.isActive) {
         println("sendWebSocketMessage: Web socket session is already closed")
         // jshmrsn: Not removing client here because that can modify clients list while it's being iterated
         return
      }

      try {
         val result = webSocketSession.outgoing.trySend(Frame.Text(messageString))

         if (result.isFailure) {
            println("sendWebSocketMessage: Failed to send web socket message: isActive = ${webSocketSession.isActive}, length = ${messageString.length}")

            if (webSocketSession.isActive) {
               this.webSocketMessagesToRetry.add(
                  PendingWebSocketMessage(
                     messageString = messageString,
                     client = client
                  )
               )
            }
         }
      } catch (exception: Exception) {
         println("sendWebSocketMessage: Exception while sending web socket message: $messageString\n${exception.message}\n${exception.stackTraceToString()}")
         webSocketSession.outgoing.close(Exception("Exception while sending web socket message"))
      }
   }

   private fun sendSnapshotMessage(client: Client) {
      println("Sending snapshot message to client: " + client.clientId)
      this.sendWebSocketMessage(
         client = client,
         message = SimulationSnapshotWebSocketMessage(
            simulationData = this.buildClientData()
         )
      )
   }

   open fun onEntityCreated(entity: Entity) {

   }

   open fun onEntityDestroyed(entity: Entity) {

   }

   fun createEntity(
      components: List<EntityComponentData>,
      entityId: EntityId = EntityId(buildShortRandomIdentifier())
   ): Entity {
      synchronized(this) {
         if (this.mutableEntitiesById.containsKey(entityId)) {
            throw Exception("Entity already exists for entityId: $entityId")
         }

         val initialEntityData = EntityData(
            components = components,
            entityId = entityId
         )

         val entity = Entity(
            data = initialEntityData,
            simulation = this
         )

         this.broadcastMessage(
            EntityCreatedWebSocketMessage(
               entityData = entity.buildData(),
               simulationTime = this.getCurrentSimulationTime()
            )
         )

         this.mutableEntities.add(entity)
         this.mutableEntitiesById[entityId] = entity

         this.activateSystemsForEntity(entity)

         this.onEntityCreated(entity)

         return entity
      }
   }

   fun handleEntityDestroyed(entity: Entity) {
      synchronized(this) {
         this.broadcastMessage(
            EntityDestroyedWebSocketMessage(
               entityId = entity.entityId,
               simulationTime = this.getCurrentSimulationTime()
            )
         )

         this.tickSystems.forEach { system ->
            system.deactivateForEntity(entity)
         }

         this.coroutineSystems.forEach { system ->
            system.deactivateForEntity(
               entity = entity
            )
         }

         this.mutableEntities.remove(entity)
         this.mutableEntitiesById.remove(entity.entityId)

         this.mutableDestroyedEntitiesById[entity.entityId] = entity

         this.onEntityDestroyed(entity)
      }
   }

   private fun broadcastMessage(message: WebSocketMessage) {
      synchronized(this) {
         val serializedMessage = DynamicSerialization.serialize(message)
         val messageString = Json.encodeToString(serializedMessage)

         this.pendingReplayData.sentMessages.add(
            ReplaySentMessage(
               simulationTime = this.getCurrentSimulationTime(),
               message = serializedMessage
            )
         )

         this.clients.forEach { client ->
            this.sendWebSocketMessage(
               client = client,
               messageString = messageString
            )
         }
      }
   }

   private val excludeDefaultsJsonFormat = Json {
      encodeDefaults = false
   }

   fun broadcastEntityComponentMessage(
      entityId: EntityId,
      componentData: EntityComponentData,
      previousBroadcastData: EntityComponentData
   ) {
      val diff = DynamicSerialization.serializeDiff(
         previous = previousBroadcastData,
         new = componentData
      )

      if (diff != null) {
//         println("Broadcasting entity component message: entityId = $entityId, ${componentData::class}")
         // jshmrsn: Pre-serialize so we can exclude defaults for less network data
         val serializedDiff = this.excludeDefaultsJsonFormat.encodeToJsonElement(diff)

         this.broadcastMessage(
            EntityComponentWebSocketMessage(
               entityId = entityId,
               componentTypeName = DynamicSerialization.getSerializationNameForClass(componentData::class),
               diff = serializedDiff,
               simulationTime = this.getCurrentSimulationTime()
            )
         )
      }
   }

   fun getCurrentSimulationTime(): Double {
      return this.simulationTime
   }

   fun handleNewWebSocketClient(
      clientId: ClientId,
      userId: UserId,
      userSecret: UserSecret,
      webSocketSession: DefaultWebSocketServerSession
   ) {
      synchronized(this) {
         println("handleNewWebSocketClient: $clientId, $webSocketSession")

         val client = Client(
            clientId = clientId,
            webSocketSession = webSocketSession,
            userId = userId,
            userSecret = userSecret
         )

         this.clients.add(client)

         this.sendSnapshotMessage(client)

         this.handleNewClient(client)
      }
   }

   open fun handleNewClient(client: Client) {}

   fun handleWebSocketClose(webSocketSession: DefaultWebSocketServerSession) {
      synchronized(this) {
         println("handleWebSocketClose")
         this.clients.removeIf {
            if (it.webSocketSession == webSocketSession) {
               println("handleWebSocketClose: removing client ${it.clientId}")
               true
            } else {
               false
            }
         }
      }
   }

   fun buildReplayData(): ReplayData {
      return ReplayData(
         simulationStartedAtUnixTime = this.startedAtUnixTime,
         replayGeneratedAtSimulationTime = this.getCurrentSimulationTime(),
         scenarioInfo = this.scenarioInfo,
         configs = this.configs.map {
            DynamicSerialization.serialize(it)
         },
         simulationId = this.simulationId,
         sentMessages = this.pendingReplayData.sentMessages
      )
   }

   @OptIn(DelicateCoroutinesApi::class)
   fun saveReplay(): Boolean {
      val replayUploadBucket = Companion.replayS3UploadBucket

      val replayData = this.buildReplayData()
      val replayDataJson = try {
         Json.encodeToString(replayData)
      } catch (exception: Exception) {
         println("Failed to serialize replay data as JSON: " + exception.stackTraceToString())
         return false
      }

      val replayFileName = "replay-" + this.simulationId.value + ".json"

      if (replayUploadBucket != null) {
         GlobalScope.launch {
            try {
               println("Uploading replay to S3: replayFileName = $replayFileName, bucket = $replayUploadBucket, region = ${Companion.replayS3UploadRegion}")
               S3Client
                  .fromEnvironment { region = Companion.replayS3UploadRegion }
                  .use { s3 ->
                     s3.putObject {
                        bucket = replayUploadBucket
                        key = "replay-data/$replayFileName"
                        body = ByteStream.fromString(replayDataJson)
                     }
                  }
            } catch (exception: Exception) {
               println("Exception while uploading replay to S3: ${exception.stackTraceToString()}")
            }
         }
      } else {
         val replayDirectory = Companion.replayDirectory

         Files.createDirectories(replayDirectory)

         val replayFilePath = replayDirectory.resolve(replayFileName)
         println("Writing replay to file: " + replayFilePath.absolutePathString())

         replayFilePath.writeText(replayDataJson)
      }

      return true
   }

   open fun onTick(deltaTime: Double) {

   }

   fun tick(deltaTime: Double): TickResult {
      if (this.isTerminated) {
         return TickResult(
            shouldTerminate = false
         )
      }

      if (this.shouldTerminate) {
         this.handleTermination()
         return TickResult(
            shouldTerminate = false
         )
      }

      val requestsFromBackgroundThread = synchronized(this) {
         val result = this.requestsFromBackgroundThread.toList()
         this.requestsFromBackgroundThread.clear()
         result
      }

      for (requestFromBackgroundThread in requestsFromBackgroundThread) {
         try {
            requestFromBackgroundThread.task()
         } catch (exception: Exception) {
            println("Exception while performing background thread requested: " + exception.stackTraceToString())

            try {
               requestFromBackgroundThread.handleException(exception)
            } catch (exception: Exception) {
               println("requestFromBackgroundThread.handleException threw another exception. Discarding exception to protect simulation thread: " + exception.stackTraceToString())
            }
         }
      }

      val currentUnixTimeSeconds = getCurrentUnixTimeSeconds()
      this.simulationTime += deltaTime
      this.lastTickUnixTime = currentUnixTimeSeconds

      this.queuedCallbacks.update()

      val webSocketMessagesToRetrySnapshot = this.webSocketMessagesToRetry.map { it }
      this.webSocketMessagesToRetry.clear()

      this.clients.toList().forEach {
         if (!it.webSocketSession.isActive) {
            println("Found inactive web socket session, calling handleWebSocketClose")
            this.handleWebSocketClose(it.webSocketSession)
         }
      }

      webSocketMessagesToRetrySnapshot.forEach {
         if (it.client.webSocketSession.isActive) {
            println("Re-trying failed message for client: " + it.client)

            this.sendWebSocketMessage(
               client = it.client,
               messageString = it.messageString
            )
         } else {
            println("Discard failed message for inactive socket session")
         }
      }

      this.tickSystems.forEach { tickSystem ->
         tickSystem.tick(
            deltaTime = deltaTime
         )
      }

      this.coroutineSystems.forEach { coroutineSystem ->
         coroutineSystem.cleanUp()
      }

      val context = this.context

      for (client in this.clients.toList()) {
         if (context.clientReceiveInteractionTimeoutSeconds != null) {
            val timeSinceInteraction = getCurrentUnixTimeSeconds() - client.lastReceivedInteractionUnixTime

            if (timeSinceInteraction > context.clientReceiveInteractionTimeoutSeconds) {
               this.sendAlertMessage(client, "Disconnecting for inactivity (${timeSinceInteraction.roundToInt()}).")
               this.queueCallbackWithoutDelay {
                  this.disconnectClient(client, reason = "Interaction timeout: ${timeSinceInteraction.roundToInt()}")
               }
               continue
            }
         }

         if (context.clientReceiveMessageTimeoutSeconds != null) {
            val timeSinceMessage = getCurrentUnixTimeSeconds() - client.lastReceivedMessageUnixTime

            if (timeSinceMessage > context.clientReceiveMessageTimeoutSeconds) {
               this.sendAlertMessage(client, "Disconnecting for not receiving message  (${timeSinceMessage.roundToInt()}).")
               this.queueCallbackWithoutDelay {
                  this.disconnectClient(client, reason = "Message timeout: ${timeSinceMessage.roundToInt()}")
               }
               continue
            }
         }
      }

      if (this.clients.isNotEmpty()) {
         this.lastAnyClientsConnectedUnixTime = getCurrentUnixTimeSeconds()
      } else {
         val timeSinceAnyClientsConnected = getCurrentUnixTimeSeconds() - this.lastAnyClientsConnectedUnixTime

         if (context.noClientsConnectedTerminationTimeoutSeconds != null &&
            timeSinceAnyClientsConnected > context.noClientsConnectedTerminationTimeoutSeconds
         ) {
            println("Terminating simulation because not clients connected for ${timeSinceAnyClientsConnected.roundToInt()} seconds")

            return TickResult(
               shouldTerminate = true
            )
         }
      }

      this.onTick(deltaTime = deltaTime)

      return TickResult(
         shouldTerminate = false
      )
   }

   fun disconnectClient(client: Client, reason: String) {
      println("Disconnecting client ${client.clientId} for reason '$reason'")
      try {
         client.webSocketSession.outgoing.close()
      } catch (exception: Exception) {
         println("disconnectClient: Exception while trying to close socket of client: " + exception.stackTraceToString())
      }

      this.clients.remove(client)
   }

   fun buildClientData(): ClientSimulationData {
      return this.buildData().buildClientData()
   }

   private fun buildData(): SimulationData {
      return SimulationData(
         scenarioInfo = this.scenarioInfo,
         simulationId = this.simulationId,
         configs = this.configs,
         entities = this.entities.map {
            it.buildData()
         },
         simulationTime = this.getCurrentSimulationTime(),
         lastTickUnixTime = this.lastTickUnixTime
      )
   }

   var isTerminated = false
      private set

   fun handleTermination() {
      this.isTerminated = true

      this.entities.forEach {
         it.stop()
      }

      this.coroutineSystems.forEach { system ->
         system.handleTermination()
      }

      this.saveReplay()

      synchronized(this.simulationContainer) {
         this.simulationContainer.handleSimulationTerminated(this)
      }
   }

   fun start(startContext: StartContext) {
      this.onStart()
   }

   private fun activateSystemsForEntity(entity: Entity) {
      this.tickSystems.forEach { system ->
         system.activateForEntity(entity)
      }

      this.coroutineSystems.forEach { system ->
         system.activateForEntity(
            entity = entity
         )
      }
   }


   fun getEntityOrNull(entityId: EntityId) = this.entitiesById[entityId]

   fun hasEntityExistedBefore(entityId: EntityId) = this.destroyedEntitiesById.containsKey(entityId)

   fun getDestroyedEntityOrNull(entityId: EntityId) = this.destroyedEntitiesById[entityId]

   fun getEntity(entityId: EntityId): Entity {
      val entity = this.getEntityOrNull(entityId = entityId)
         ?: if (this.destroyedEntitiesById.containsKey(entityId)) {
            throw Exception("getEntity: Entity for $entityId has already been destroyed")
         } else {
            throw Exception("getEntity: No entity for $entityId (and no entity has existed with that ID before)")
         }

      return entity
   }

   open fun handleClientMessage(
      client: Client,
      messageType: String,
      messageData: JsonObject,
      isAdminRequest: Boolean
   ) {

   }

   fun handleWebSocketMessage(
      session: DefaultWebSocketServerSession,
      messageType: String,
      messageData: JsonObject,
      isAdminRequest: Boolean
   ) {
      if (this.isTerminated) {
         println("handleWebSocketMessage: Ignoring message because simulation is terminated ($messageType)")
         return
      }

      val client = this.clients.find { it.webSocketSession == session }

      if (client == null) {
         println("handleWebSocketMessage: $messageType: Client not found")
         return
      }

      client.notifyMessageReceived()

//      println("handleWebSocketMessage: $messageType: From client ${client.clientId}")

      try {
         this.handleClientMessage(
            client = client,
            messageType = messageType,
            messageData = messageData,
            isAdminRequest = isAdminRequest
         )
      } catch (exception: Exception) {
         val errorId = buildShortRandomIdentifier().substring(0, 6)

         println("Exception while handling client message ($errorId): ${exception.stackTraceToString()}")
         sendAlertMessage(client, "Server error while handling client request ($errorId)")
      }
   }

   fun sendAlertMessage(client: Client, mode: AlertMode, message: String) {
      println("sendAlertMessage (${mode.name}): $message")
      this.sendWebSocketMessage(
         client = client,
         message = AlertWebSocketMessage(
            message = message,
            mode = mode.name
         )
      )
   }

   fun sendAlertMessage(client: Client, message: String) {
      this.sendAlertMessage(
         client = client,
         message = message,
         mode = AlertMode.Alert
      )
   }

   fun sendAlertMessage(userId: UserId, message: String) {
      this.clients.forEach { client ->
         if (client.userId == userId) {
            this.sendAlertMessage(
               client = client,
               message = message,
               mode = AlertMode.Alert
            )
         }
      }
   }

   fun broadcastAlertMessage(message: String) {
      this.broadcastAlertMessage(
         message = message,
         mode = AlertMode.Alert
      )
   }

   fun broadcastAlertMessage(mode: AlertMode, message: String) {
      println("broadcastAlertMessage (${mode.name}): $message")

      if (mode == AlertMode.GameMessage) {
         this.broadcastAlertAsGameMessage(message)
      } else {
         this.broadcastMessage(
            message = AlertWebSocketMessage(
               message = message,
               mode = mode.name
            )
         )
      }
   }

   open fun broadcastAlertAsGameMessage(message: String) {
      this.broadcastMessage(
         message = AlertWebSocketMessage(
            message = "(default broadcastAlertAsGameMessage): $message",
            mode = AlertMode.Alert.name
         )
      )
   }

   inline fun <reified T : Config> getConfigOrNull(configKey: String): T? {
      val results = this.configs
         .mapNotNull {
            if (it.key == configKey) {
               it as? T
            } else null
         }

      if (results.size > 1) {
         throw Exception("More than one config matching key '$configKey' and type '${T::class}'")
      }

      return results.firstOrNull()
   }

   inline fun <reified T : Config> getConfig(configKey: String): T {
      return this.getConfigOrNull<T>(configKey)
         ?: throw Exception("Config not found for key '$configKey' and type '${T::class}'")
   }

   fun getClient(clientId: ClientId): Client? {
      return this.clients.find { it.clientId == clientId }
   }

   fun getClientsForUserId(userId: UserId): List<Client> {
      return this.clients.filter { it.userId == userId }
   }

   fun buildInfo(checkBelongsToUserSecret: UserSecret?): SimulationInfo {
      synchronized(this) {
         return SimulationInfo(
            simulationId = this.simulationId,
            scenarioInfo = this.scenarioInfo,
            isTerminated = this.isTerminated,
            startedAtUnixTime = this.startedAtUnixTime,
            belongsToUser = checkBelongsToUserSecret == this.context.createdByUserSecret,
            wasCreatedByAdmin = this.context.wasCreatedByAdmin
         )
      }
   }

   private var shouldTerminate = false

   fun requestTermination() {
      synchronized(this) {
         this.shouldTerminate = true
      }
   }
}
