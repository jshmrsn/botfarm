package botfarm.game.systems

import botfarm.engine.simulation.CoroutineSystemContext
import botfarm.engine.simulation.EntityComponent
import botfarm.game.GameSimulation
import botfarm.game.agentintegration.AgentSyncState
import botfarm.game.agentintegration.recordObservationsForAgent
import botfarm.game.agentintegration.syncAgent
import botfarm.game.components.AgentComponentData
import botfarmshared.misc.buildShortRandomIdentifier
import kotlinx.coroutines.delay
import java.net.ConnectException

suspend fun syncAgentCoroutineSystem(
   context: CoroutineSystemContext,
   agentComponent: EntityComponent<AgentComponentData>
) {
   val entity = agentComponent.entity
   val agentId = agentComponent.data.agentId
   val simulation = context.simulation as GameSimulation
   val state = AgentSyncState(simulation)

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

         syncAgent(
            context = context,
            simulation = simulation,
            entity = entity,
            agentComponent = agentComponent,
            state = state,
            agentId = agentId,
            syncId = syncId
         )

         delay(1000)
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

