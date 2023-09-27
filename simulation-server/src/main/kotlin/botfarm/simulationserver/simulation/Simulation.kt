package botfarm.simulationserver.simulation

import botfarm.apidata.EntityId
import botfarm.misc.DynamicSerialization
import botfarm.misc.buildShortRandomString
import botfarm.misc.getCurrentUnixTimeSeconds
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import kotlinx.coroutines.isActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement

@JvmInline
@Serializable
value class UserId(val value: String)

@JvmInline
@Serializable
value class ClientId(val value: String)

class Client(
   val webSocketSession: DefaultWebSocketServerSession,
   val clientId: ClientId,
   val userId: UserId
)

open class Simulation(
   data: SimulationData,
   val simulationContainer: SimulationContainer,
   val systems: Systems = Systems.default
) {
   var shouldPauseAi = false

   var tickedSimulationTime = data.tickedSimulationTime
   var lastTickUnixTime = data.lastTickUnixTime
   val startUnixTime = getCurrentUnixTimeSeconds()

   val simulationId = data.simulationId
   val configs = data.configs

   val latencyBufferTime = 0.3

   private val mutableEntities: MutableList<Entity>
   val entities: List<Entity>

   private val mutableEntitiesById = mutableMapOf<EntityId, Entity>()
   private val mutableDestroyedEntitiesById = mutableMapOf<EntityId, Entity>()

   val entitiesById: Map<EntityId, Entity> = this.mutableEntitiesById
   val destroyedEntitiesById: Map<EntityId, Entity> = this.mutableDestroyedEntitiesById

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
      val shouldKeep: Boolean
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

   fun createEntity(
      components: List<EntityComponentData>,
      entityId: EntityId = EntityId(buildShortRandomString())
   ) {
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

      this.mutableEntities.add(entity)
      this.mutableEntitiesById[entityId] = entity

      this.activateSystemsForEntity(entity)

      println("Broadcasting entity created message: entityId = $entityId")
      this.broadcastMessage(
         EntityCreatedWebSocketMessage(
            entityData = entity.buildData(),
            simulationTime = this.getCurrentSimulationTime()
         )
      )
   }

   fun handleEntityDestroyed(entity: Entity) {
      this.broadcastMessage(
         EntityDestroyedWebSocketMessage(
            entityId = entity.entityId,
            simulationTime = this.getCurrentSimulationTime()
         )
      )

      this.mutableEntities.remove(entity)
      this.mutableEntitiesById.remove(entity.entityId)

      this.mutableDestroyedEntitiesById[entity.entityId] = entity
   }

   private fun broadcastMessage(message: WebSocketMessage) {
      val messageString = Json.encodeToString(DynamicSerialization.serialize(message))

      this.clients.forEach { client ->
         println("Broadcasting message to client (${message::class}): " + client.clientId)
         this.sendWebSocketMessage(
            client = client,
            messageString = messageString
         )
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
      return getCurrentUnixTimeSeconds() - this.startUnixTime
   }

   fun handleNewWebSocketClient(
      clientId: ClientId,
      userId: UserId,
      webSocketSession: DefaultWebSocketServerSession
   ) {
      println("handleNewWebSocketClient: $clientId, $webSocketSession")

      val client = Client(
         clientId = clientId,
         webSocketSession = webSocketSession,
         userId = userId
      )

      this.clients.add(client)

      this.sendSnapshotMessage(client)

      this.handleNewClient(client)
   }

   open fun handleNewClient(client: Client) {}

   fun handleWebSocketClose(webSocketSession: DefaultWebSocketServerSession) {
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

   fun tick(): TickResult {
      val currentSimulationTime = this.getCurrentSimulationTime()
      val deltaTime = currentSimulationTime - this.tickedSimulationTime

      this.tickedSimulationTime = this.getCurrentSimulationTime()

      val currentUnixTimeSeconds = getCurrentUnixTimeSeconds()
      this.lastTickUnixTime = currentUnixTimeSeconds

      val webSocketMessagesToRetrySnapshot = this.webSocketMessagesToRetry.map { it }
      this.webSocketMessagesToRetry.clear()

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



      return TickResult(
         shouldKeep = true
      )
   }

   fun buildClientData(): ClientSimulationData {
      return this.buildData().buildClientData(
         simulationTime = this.getCurrentSimulationTime()
      )
   }

   private fun buildData(): SimulationData {
      return SimulationData(
         simulationId = this.simulationId,
         configs = this.configs,
         entities = this.entities.map {
            it.buildData()
         },
         tickedSimulationTime = this.tickedSimulationTime,
         lastTickUnixTime = this.lastTickUnixTime
      )
   }

   fun handleTermination() {
      this.entities.forEach {
         it.stop()
      }

      this.coroutineSystems.forEach { system ->
         system.handleTermination()
      }
   }

   fun start(startContext: StartContext) {
      this.entities.forEach { entity ->
         entity.start(
            startContext = startContext,
            simulation = this
         )
      }

      this.entities.forEach { entity ->
         this.activateSystemsForEntity(entity)
      }
   }

   private fun activateSystemsForEntity(entity: Entity) {
      this.tickSystems.forEach { system ->
         system.activateForEntity(entity)
      }

      this.coroutineSystems.forEach { system ->
         system.activateForEntity(
            entity = entity,
            simulationContainer = this.simulationContainer
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

   open fun handleClientMessage(client: Client, messageType: String, messageData: JsonObject) {

   }

   fun handleWebSocketMessage(session: DefaultWebSocketServerSession, messageType: String, messageData: JsonObject) {
      val client = this.clients.find { it.webSocketSession == session }

      if (client == null) {
         println("handleWebSocketMessage: $messageType: Client not found")
         return
      }

      println("handleWebSocketMessage: $messageType: From client ${client.clientId}")

      try {
         this.handleClientMessage(
            client = client,
            messageType = messageType,
            messageData = messageData
         )
      } catch (exception: Exception) {
         val errorId = buildShortRandomString().substring(0, 6)

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
}
