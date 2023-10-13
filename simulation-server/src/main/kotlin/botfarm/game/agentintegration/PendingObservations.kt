package botfarm.game.agentintegration

import botfarm.game.scripting.JavaScriptCodeSerialization
import botfarm.game.scripting.jsdata.JsEntity
import botfarmshared.engine.apidata.EntityId
import botfarmshared.game.apidata.*
import kotlinx.serialization.Serializable

@Serializable
class PendingObservations {
   val entityObservationEvents: MutableList<EntityObservationEvent> = mutableListOf()
   val movementRecords: MutableList<MovementRecord> = mutableListOf()
   val activityStreamEntries: MutableList<ActivityStreamEntry> = mutableListOf()
   val actionResults: MutableList<ActionResult> = mutableListOf()
   val startedActionUniqueIds: MutableList<String> = mutableListOf()
   val scriptExecutionErrors: MutableList<ScriptExecutionError> = mutableListOf()

   fun toObservations(): Observations = Observations(
      scriptExecutionErrors = this.scriptExecutionErrors.toList(),
      entityObservationEvents = this.entityObservationEvents.toList(),
      movementRecords = this.movementRecords.toList(),
      activityStreamEntries = this.activityStreamEntries.toList(),
      actionResults = this.actionResults.toList(),
      startedActionUniqueIds = this.startedActionUniqueIds.toList()
   )
}