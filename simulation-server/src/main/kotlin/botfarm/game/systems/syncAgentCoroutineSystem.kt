package botfarm.game.systems

import botfarm.engine.simulation.CoroutineSystemContext
import botfarm.engine.simulation.EntityComponent
import botfarm.game.GameSimulation
import botfarm.game.agentintegration.*
import botfarm.game.components.AgentComponentData
import botfarmshared.game.apidata.AgentSyncResponse
import botfarmshared.misc.buildShortRandomIdentifier
import botfarmshared.misc.getCurrentUnixTimeSeconds
import kotlinx.coroutines.delay
import java.net.ConnectException

suspend fun syncAgentCoroutineSystem(
   context: CoroutineSystemContext,
   agentComponent: EntityComponent<AgentComponentData>
) {
   val entity = agentComponent.entity
   val agentId = agentComponent.data.agentId
   val simulation = context.simulation as GameSimulation
   val agentType = agentComponent.data.agentType

   val state = AgentSyncState(
      simulation = simulation,
      agentType = agentType,
      entity = entity
   )

   var lastAgentSyncUnixTime = 0.0

   while (true) {
      context.unwindIfNeeded()

      val syncId = buildShortRandomIdentifier()

      try {
         recordObservationsForAgent(
            simulation = simulation,
            entity = entity,
            agentComponent = agentComponent,
            state = state
         )

         val timeSinceAgentSync = getCurrentUnixTimeSeconds() - lastAgentSyncUnixTime
         val agentSyncInterval = 1.0
         if (timeSinceAgentSync > agentSyncInterval) {
            lastAgentSyncUnixTime = getCurrentUnixTimeSeconds()

            syncAgent(
               context = context,
               simulation = simulation,
               entity = entity,
               agentComponent = agentComponent,
               state = state,
               agentId = agentId,
               syncId = syncId
            )
         }

         updateAgentActions(
            entity = entity,
            state = state
         )

         delay(100)
      } catch (connectException: ConnectException) {
         context.synchronizeSimulation {
            agentComponent.modifyData {
               it.copy(
                  agentIntegrationStatus = "exception",
                  agentError = "Agent connection refused (syncId = $syncId)"
               )
            }
         }

         delay(3000)
      } catch (exception: Exception) {
         val errorId = buildShortRandomIdentifier()
         simulation.broadcastAlertAsGameMessage("Exception in agent sync (errorId = $errorId, syncId = $syncId)")
         println("Exception in character agent logic (errorId = $errorId, syncId = $syncId):\n${exception.stackTraceToString()}")

         context.synchronizeSimulation {
            agentComponent.modifyData {
               it.copy(
                  agentIntegrationStatus = "exception",
                  agentError = "Exception in character agent logic (errorId = $errorId, syncId = $syncId)"
               )
            }
         }

         delay(3000)
      }
   }
}

