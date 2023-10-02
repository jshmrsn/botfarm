package botfarm.game.ai

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

   fun toObservations(): Observations = Observations(
      spokenMessages = this.spokenMessages,
      selfSpokenMessages = this.selfSpokenMessages,
      entitiesById = this.entitiesById,
      movementRecords = this.movementRecords,
      actionOnEntityRecords = this.actionOnEntityRecords,
      actionOnInventoryItemActionRecords = this.actionOnInventoryItemActionRecords,
      craftItemActionRecords = this.craftItemActionRecords,
      activityStreamEntries = this.activityStreamEntries
   )
}