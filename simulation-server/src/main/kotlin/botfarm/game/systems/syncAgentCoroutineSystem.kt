package botfarm.game.systems

import botfarm.engine.simulation.CoroutineSystemContext
import botfarm.engine.simulation.EntityComponent
import botfarm.game.GameSimulation
import botfarm.game.agentintegration.AgentSyncState
import botfarm.game.agentintegration.recordObservationsForAgent
import botfarm.game.agentintegration.syncAgent
import botfarm.game.agentintegration.updateAgentActions
import botfarm.game.components.AgentControlledComponentData
import botfarmshared.misc.buildShortRandomIdentifier
import java.net.ConnectException

suspend fun syncAgentCoroutineSystem(
   context: CoroutineSystemContext,
   agentControlledComponent: EntityComponent<AgentControlledComponentData>
) {
   val entity = agentControlledComponent.entity
   val agentId = agentControlledComponent.data.agentId
   val simulation = context.simulation as GameSimulation
   val agentType = agentControlledComponent.data.agentType

   val state = AgentSyncState(
      simulation = simulation,
      agentType = agentType,
      entity = entity,
      agentId = agentId
   )

   var lastAgentSyncSimulationTime = 0.0

   while (true) {
      context.unwindIfNeeded()

      val syncId = buildShortRandomIdentifier()

      try {
         recordObservationsForAgent(
            simulation = simulation,
            entity = entity,
            agentControlledComponent = agentControlledComponent,
            state = state
         )

         val timeSinceAgentSync = simulation.simulationTime - lastAgentSyncSimulationTime
         val agentSyncInterval = 1.0
         if (timeSinceAgentSync > agentSyncInterval) {
            lastAgentSyncSimulationTime = simulation.simulationTime

            syncAgent(
               context = context,
               simulation = simulation,
               entity = entity,
               agentControlledComponent = agentControlledComponent,
               state = state,
               agentId = agentId,
               syncId = syncId
            )
         }

         updateAgentActions(
            entity = entity,
            state = state
         )

         context.delay(100)
      } catch (connectException: ConnectException) {
         context.synchronizeSimulation {
            agentControlledComponent.modifyData {
               it.copy(
                  agentIntegrationStatus = "exception",
                  agentError = "Agent connection refused (syncId = $syncId)"
               )
            }
         }

         context.delay(3000)
      } catch (exception: Exception) {
         val errorId = buildShortRandomIdentifier()
         simulation.broadcastAlertAsGameMessage("Exception in agent sync (errorId = $errorId, syncId = $syncId)")
         println("Exception in character agent logic (errorId = $errorId, syncId = $syncId):\n${exception.stackTraceToString()}")

         context.synchronizeSimulation {
            agentControlledComponent.modifyData {
               it.copy(
                  agentIntegrationStatus = "exception",
                  agentError = "Exception in character agent logic (errorId = $errorId, syncId = $syncId)"
               )
            }
         }

         context.delay(3000)
      }
   }
}

