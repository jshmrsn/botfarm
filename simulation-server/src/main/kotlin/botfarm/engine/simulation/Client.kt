package botfarm.engine.simulation

import botfarmshared.misc.getCurrentUnixTimeSeconds
import io.ktor.server.websocket.DefaultWebSocketServerSession

class Client(
   val simulation: Simulation,
   val webSocketSession: DefaultWebSocketServerSession,
   val clientId: ClientId,
   val userId: UserId,
   val userSecret: UserSecret
) {
   val entityContainer: EntityContainer = EntityContainer(
      simulation = this.simulation,
      sendWebSocketMessage = {
         this.simulation.sendWebSocketMessage(
            client = this,
            message = it
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