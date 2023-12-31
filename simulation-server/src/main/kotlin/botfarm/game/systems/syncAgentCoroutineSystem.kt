package botfarm.game.systems

import botfarm.engine.ktorplugins.ServerEnvironmentGlobals
import botfarm.engine.simulation.CoroutineSystemContext
import botfarm.engine.simulation.EntityComponent
import botfarm.game.GameSimulation
import botfarm.game.agentintegration.AgentIntegration
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

   val agentIntegration = AgentIntegration(
      simulation = simulation,
      agentType = agentType,
      entity = entity,
      agentId = agentId,
      fastJavaScriptThreadSleep = context.shouldMinimizeSleep
   )

   var lastAgentSyncSimulationTime = 0.0

   while (true) {
      context.unwindIfNeeded()

      val syncId = buildShortRandomIdentifier()

      try {
         agentIntegration.recordObservationsForAgent()

         val timeSinceAgentSync = simulation.simulationTime - lastAgentSyncSimulationTime
         val agentSyncInterval = 1.0
         if (timeSinceAgentSync > agentSyncInterval) {
            lastAgentSyncSimulationTime = simulation.simulationTime

            agentIntegration.syncWithAgent(
               coroutineSystemContext = context,
               simulation = simulation,
               syncId = syncId
            )
         }

         agentIntegration.updatePendingActions()

         context.delay(100)
      } catch (connectException: ConnectException) {
         agentControlledComponent.modifyData {
            it.copy(
               agentIntegrationStatus = "agent-connection-error"
            )
         }

         context.delay(3000)
      } catch (exception: Exception) {
         val errorId = buildShortRandomIdentifier()
         simulation.broadcastAlertAsGameMessage("Exception in agent sync (errorId = $errorId, syncId = $syncId)" + if (ServerEnvironmentGlobals.hideErrorDetailsFromClients) {
            ""
         } else {
            "\n" + exception.stackTraceToString()
         })

         println("Exception in character agent logic (errorId = $errorId, syncId = $syncId):\n${exception.stackTraceToString()}")

         agentControlledComponent.modifyData {
            it.copy(
               agentIntegrationStatus = "exception"
            )
         }

         context.delay(3000)
      }
   }
}

