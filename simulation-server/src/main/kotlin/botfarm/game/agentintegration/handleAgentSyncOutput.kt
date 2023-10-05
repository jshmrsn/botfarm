package botfarm.game.agentintegration

import botfarm.common.PositionComponentData
import botfarm.common.isMoving
import botfarm.common.resolvePosition
import botfarm.engine.simulation.AlertMode
import botfarm.game.components.AgentComponentData
import botfarm.game.components.CharacterComponentData
import botfarm.game.GameSimulation
import botfarmshared.game.apidata.*
import botfarm.engine.simulation.Entity
import botfarm.engine.simulation.EntityComponent

fun handleAgentSyncOutput(
   agentSyncOutput: AgentSyncOutput,
   agentComponent: EntityComponent<AgentComponentData>,
   simulation: GameSimulation,
   state: AgentSyncState,
   entity: Entity
) {
   val actions = agentSyncOutput.actions

   agentComponent.modifyData {
      it.copy(
         agentRemoteDebugInfo = agentSyncOutput.debugInfo ?: it.agentRemoteDebugInfo,
         agentStatus = agentSyncOutput.agentStatus ?: it.agentStatus,
         wasRateLimited = agentSyncOutput.wasRateLimited,
         statusDuration = agentSyncOutput.statusDuration,
         statusStartUnixTime = agentSyncOutput.statusStartUnixTime
      )
   }

   agentSyncOutput.promptUsages.forEach {
      updateComponentModelUsage(it, agentComponent)
   }

   if (agentSyncOutput.error != null) {
      simulation.broadcastAlertMessage(AlertMode.ConsoleError, "Error from remote agent: " + agentSyncOutput.error)

      agentComponent.modifyData {
         it.copy(
            agentError = agentSyncOutput.error
         )
      }

      return
   }


   if (actions != null) {
      if (entity.isMoving) {
         simulation.startEntityMovement(
            entity = entity,
            endPoint = entity.resolvePosition()
         )
      }

      state.activeAction = null
      state.pendingActions.clear()
      state.pendingActions.addAll(agentSyncOutput.actions)
   }
}

