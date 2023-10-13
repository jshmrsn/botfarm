package botfarm.engine.simulation

import botfarmshared.misc.DynamicSerialization
import botfarmshared.misc.getCurrentUnixTimeSeconds
import io.ktor.server.websocket.DefaultWebSocketServerSession
import kotlinx.serialization.json.encodeToJsonElement

class Client(
   val simulation: Simulation,
   val webSocketSession: DefaultWebSocketServerSession,
   val clientId: ClientId,
   val userId: UserId,
   val userSecret: UserSecret
) {
   val entityContainer: EntityContainer = EntityContainer(
      simulation = this.simulation,
      onEntityCreated = {
         this.simulation.sendWebSocketMessage(
            client = this,
            message = EntityCreatedWebSocketMessage(
               entityData = it.buildData(),
               simulationTime = this.simulation.simulationTime
            )
         )
      },
      onComponentChanged = { component, previousData, newData ->
         val diff = DynamicSerialization.serializeDiff(
            previous = previousData,
            new = newData
         )

         if (diff != null) {
            // jshmrsn: Pre-serialize so we can exclude defaults for less network data
            val serializedDiff = excludeDefaultsJsonFormat.encodeToJsonElement(diff)

            this.simulation.sendWebSocketMessage(
               client = this,
               message = EntityComponentWebSocketMessage(
                  entityId = component.entity.entityId,
                  componentTypeName = DynamicSerialization.getSerializationNameForClass(component.componentDataClass),
                  diff = serializedDiff,
                  simulationTime = this.simulation.simulationTime
               )
            )
         }
      },
      onEntityDestroyed = {
         this.simulation.sendWebSocketMessage(
            client = this,
            message = EntityDestroyedWebSocketMessage(
               entityId = it.entityId,
               simulationTime = this.simulation.simulationTime
            )
         )
      }
   )

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