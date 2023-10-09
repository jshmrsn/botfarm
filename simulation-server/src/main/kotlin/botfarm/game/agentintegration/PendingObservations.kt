package botfarm.game.agentintegration

import botfarm.game.scripting.JavaScriptCodeSerialization
import botfarm.game.scripting.jsdata.JsEntity
import botfarmshared.engine.apidata.EntityId
import botfarmshared.game.apidata.*
import kotlinx.serialization.Serializable

@Serializable
class PendingObservations {
   val entitiesById: MutableMap<EntityId, EntityInfo> = mutableMapOf()
   val movementRecords: MutableList<MovementRecord> = mutableListOf()
   val activityStreamEntries: MutableList<ActivityStreamEntry> = mutableListOf()
   val actionResults: MutableList<ActionResult> = mutableListOf()
   val startedActionUniqueIds: MutableList<String> = mutableListOf()
   val scriptExecutionErrors: MutableList<ScriptExecutionError> = mutableListOf()

   fun toObservations(): Observations = Observations(
      scriptExecutionErrors = this.scriptExecutionErrors.toList(),
      entitiesById = this.entitiesById.toMap().mapValues {
         val entityInfo = it.value

         val variableTypeName = if (entityInfo.characterInfo != null) {
            "character"
         } else if (entityInfo.itemInfo != null) {
            entityInfo.itemInfo.itemConfigKey.replace("-", "_")
         } else {
            ""
         }

         val entityVariableName = "${variableTypeName}_entity_${entityInfo.entityId.value}"

         EntityInfoWrapper(
            serializedAsJavaScript = JavaScriptCodeSerialization.serialize(JsEntity(api = null, entityInfo = entityInfo)),
            javaScriptVariableName = entityVariableName,
            entityInfo = it.value
         )
      },
      movementRecords = this.movementRecords.toList(),
      activityStreamEntries = this.activityStreamEntries.toList(),
      actionResults = this.actionResults.toList(),
      startedActionUniqueIds = this.startedActionUniqueIds.toList()
   )
}