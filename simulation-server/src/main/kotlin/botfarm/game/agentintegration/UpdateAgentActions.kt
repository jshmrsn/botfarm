package botfarm.game.agentintegration

import botfarm.engine.simulation.Entity
import botfarm.game.components.AgentControlledComponentData
import botfarm.game.components.isAvailableToPerformAction
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

fun updateAgentActions(
   entity: Entity,
   state: AgentSyncState
) {
   synchronized(state.simulation) {
      if (state.activeAction == null) {
         if (entity.isAvailableToPerformAction) {
            val nextAction = state.pendingActions.removeFirstOrNull()

            if (nextAction != null) {
               state.activeAction = nextAction
               state.startedActionUniqueIds.add(nextAction.actionUniqueId)

               val jsonFormat = Json {
                  prettyPrint = true
                  encodeDefaults = false
               }

               entity.getComponent<AgentControlledComponentData>().modifyData {
                  it.copy(
                     currentActionTimeline = if (it.currentActionTimeline != null) {
                        it.currentActionTimeline + "\n"
                     } else {
                        ""
                     }  + jsonFormat.encodeToString(nextAction)
                  )
               }

               performAgentAction(
                  action = nextAction,
                  state = state,
                  entity = entity
               ) { actionResult ->
                  state.actionResultsByActionUniqueId[nextAction.actionUniqueId] = actionResult
                  state.activeAction = null

                  entity.getComponent<AgentControlledComponentData>().modifyData {
                     it.copy(
                        currentActionTimeline = it.currentActionTimeline + "\n(done)"
                     )
                  }
               }
            }
         }
      }
   }
}
