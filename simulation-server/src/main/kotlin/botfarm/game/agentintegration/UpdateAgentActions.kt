package botfarm.game.agentintegration

import botfarm.engine.simulation.Entity
import botfarm.game.components.isAvailableToPerformAction

fun updateAgentActions(
   entity: Entity,
   state: AgentSyncState
) {
   if (state.activeAction == null) {
      if (entity.isAvailableToPerformAction) {
         val nextAction = state.pendingActions.removeFirstOrNull()

         if (nextAction != null) {
            state.activeAction = nextAction

            handleAgentAction(
               action = nextAction,
               state = state,
               entity = entity
            ) { actionResult ->
               state.activeAction = null
            }
         }
      }
   }
}