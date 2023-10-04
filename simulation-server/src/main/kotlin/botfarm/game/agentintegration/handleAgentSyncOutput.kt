package botfarm.game.agentintegration

import botfarm.common.PositionComponentData
import botfarm.engine.simulation.AlertMode
import botfarm.game.components.AgentComponentData
import botfarm.game.components.CharacterComponentData
import botfarm.game.GameSimulation
import botfarmshared.game.apidata.*
import botfarm.engine.simulation.Entity
import botfarm.engine.simulation.EntityComponent

fun handleAgentSyncOutput(
   agentSyncOutput: AgentSyncOutput,
   positionComponent: EntityComponent<PositionComponentData>,
   simulationTime: Double,
   agentComponent: EntityComponent<AgentComponentData>,
   simulation: GameSimulation,
   characterComponent: EntityComponent<CharacterComponentData>,
   state: AgentSyncState,
   entity: Entity,
   syncId: String
) {
   val agentType = agentComponent.data.agentType
   val debugInfo = "$agentType, syncId = $syncId"

   val agentActionUtils = AgentActionUtils(
      entity = entity,
      state = state,
      simulation = simulation,
      debugInfo = debugInfo
   )

   val actions = agentSyncOutput.actions

   val currentLocation = positionComponent.data.positionAnimation.resolve(simulationTime)

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
      actions.forEach { action ->
         handleAgentAction(
            action = action,
            state = state,
            characterComponent = characterComponent,
            agentActionUtils = agentActionUtils,
            simulationTimeForStep = simulationTime,
            currentLocation = currentLocation,
            simulation = simulation,
            entity = entity,
            positionComponent = positionComponent,
            debugInfo = debugInfo
         )
      }
   }
}

