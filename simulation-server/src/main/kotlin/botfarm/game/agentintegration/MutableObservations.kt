package botfarm.game.agentintegration

import botfarmshared.engine.apidata.EntityId
import botfarmshared.game.apidata.*
import kotlinx.serialization.Serializable

@Serializable
class MutableObservations {
   val spokenMessages: MutableList<ObservedSpokenMessage> = mutableListOf()
   val selfSpokenMessages: MutableList<SelfSpokenMessage> = mutableListOf()
   val entitiesById: MutableMap<EntityId, EntityInfo> = mutableMapOf()
   val movementRecords: MutableList<MovementRecord> = mutableListOf()
   val actionOnEntityRecords: MutableList<ActionOnEntityRecord> = mutableListOf()
   val actionOnInventoryItemActionRecords: MutableList<ActionOnInventoryItemRecord> = mutableListOf()
   val craftItemActionRecords: MutableList<CraftItemActionRecord> = mutableListOf()
   val activityStreamEntries: MutableList<ActivityStreamEntryRecord> = mutableListOf()

   val actionResults: MutableList<ActionResult> = mutableListOf()
   val startedActionUniqueIds: MutableList<String> = mutableListOf()

   fun toObservations(): Observations = Observations(
      spokenMessages = this.spokenMessages.toList(),
      selfSpokenMessages = this.selfSpokenMessages.toList(),
      entitiesById = this.entitiesById.toMap(),
      movementRecords = this.movementRecords.toList(),
      actionOnEntityRecords = this.actionOnEntityRecords.toList(),
      actionOnInventoryItemActionRecords = this.actionOnInventoryItemActionRecords.toList(),
      craftItemActionRecords = this.craftItemActionRecords.toList(),
      activityStreamEntries = this.activityStreamEntries.toList(),
      actionResults = this.actionResults.toList(),
      startedActionUniqueIds = this.startedActionUniqueIds.toList()
   )
}