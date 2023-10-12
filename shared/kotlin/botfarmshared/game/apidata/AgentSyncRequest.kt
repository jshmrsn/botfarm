package botfarmshared.game.apidata

import botfarmshared.engine.apidata.EntityId
import botfarmshared.engine.apidata.SimulationId
import botfarmshared.misc.Vector2
import kotlinx.serialization.Serializable


@Serializable
class AgentSyncRequest(
   val input: AgentSyncInput
)

@Serializable
class AgentSyncInput(
   val agentType: String,
   val syncId: String,
   val agentId: AgentId,
   val simulationId: SimulationId,
   val simulationTime: Double,
   val selfInfo: SelfInfo,
   val newObservations: Observations,
   val gameConstants: GameConstants,
   val gameSimulationInfo: GameSimulationInfo,
   val agentTypeScriptInterfaceString: String,
   val mostRecentCompletedScriptId: String?
)

@Serializable
class ItemCollectionEntry(
   val itemConfigKey: String,
   val amount: Int
)

@Serializable
class ItemCollection(
   val entries: List<ItemCollectionEntry> = listOf()
)

@Serializable
class ObservedSpokenMessage(
   val entityId: EntityId,
   val messageId: String,
   val characterName: String,
   val message: String,
   val time: Double,
   val speakerLocation: Vector2,
   val myLocation: Vector2
)

@Serializable
class MovementRecord(
   val startedAtTime: Double,
   val endPoint: Vector2,
   val startPoint: Vector2,
   val reason: String?
)

@Serializable
class ItemInfo(
   val name: String,
   val description: String,
   val itemConfigKey: String
)

@Serializable
class CharacterEntityInfo(
   val name: String,
   val gender: String,
   val skinColor: String,
   val age: Int,
   val description: String,
   val equippedItemInfo: ItemInfo? = null,
   val hairColor: String? = null,
   val hairStyle: String? = null
)

@Serializable
class ItemEntityInfo(
   val itemConfigKey: String,
   val itemName: String,
   val description: String,
   val canBePickedUp: Boolean,
   val amount: Int
)

@Serializable
class DamageableEntityInfo(
   val damageableByEquippedToolItemConfigKey: String?,
   val hp: Int
)

@Serializable
class ActiveGrowthInfo(
   val growableItemConfigKey: String,
   val growingIntoItemConfigKey: String,
   val startTime: Double,
   val duration: Double
)

@Serializable
class GrowerEntityInfo(
   val activeGrowthInfo: ActiveGrowthInfo? = null,
   val canReceiveGrowableItemConfigKeys: List<String>
)

@Serializable
class EntityInfo(
   val observedAtSimulationTime: Double,
   val entityId: EntityId,
   val location: Vector2,
   val itemInfo: ItemEntityInfo?,
   val damageableInfo: DamageableEntityInfo?,
   val characterInfo: CharacterEntityInfo?,
   val growerInfo: GrowerEntityInfo? = null
)

enum class ActionType {
   UseToolToDamageEntity,
   UseToolToKillEntity,
   PlaceGrowableInGrower,
   DropItem,
   PickUpItem,
   UseEquippedTool,
   EquipItem,
   UnequipItem,
   Speak,
   Thought,
   Craft
}

enum class ActionResultType {
   Success,
   Failed,
   NoValidAction,
   TargetNotAnItem,
   UnexpectedItemInStack,
   UnexpectedEquippedItem,
   ItemCannotBeEquipped,
   StillTooFarAfterMoving,
   FailedToMoveForAction,
   TargetNoLongerExists,
   InvalidTargetEntityId,
   TargetAlreadyDead,
   NoToolItemEquipped,
   Busy,
   Obstructed,
   NoActionForEquippedTool,
   ItemNotInInventory,
   UnexpectedAutoAction
}


@Serializable
data class SpawnedItemEntity(
   val amount: Int,
   val itemConfigKey: String,
   val entityId: EntityId
)

@Serializable
data class ActivityStreamEntry(
   val time: Double,
   val title: String?,
   val message: String? = null,
   val longMessage: String? = null,

   val shouldReportToAi: Boolean = true,

   val agentReason: String? = null,
   val agentUniqueActionId: String? = null,

   val actionType: ActionType? = null,
   val actionResultType: ActionResultType? = null,
   val actionItemConfigKey: String? = null,

   val sourceItemConfigKey: String? = null,
   val sourceLocation: Vector2? = null,
   val sourceEntityId: EntityId? = null,

   val targetEntityId: EntityId? = null,
   val targetItemConfigKey: String? = null,

   val resultItemConfigKey: String? = null,
   val resultEntityId: EntityId? = null,

   val onlyShowForPerspectiveEntity: Boolean,

   val observedByEntityIds: List<EntityId>? = null,
   val spawnedItems: List<SpawnedItemEntity>? = null
)

@Serializable
class EntityInfoWrapper(
   val entityInfo: EntityInfo,
   val serializedAsJavaScript: String,
   val javaScriptVariableName: String
)

@Serializable
class ActionResult(
   val actionUniqueId: String
)

@Serializable
class Observations(
   val scriptExecutionErrors: List<ScriptExecutionError>,
   val entitiesById: Map<EntityId, EntityInfoWrapper>,
   val movementRecords: List<MovementRecord>,
   val activityStreamEntries: List<ActivityStreamEntry>,
   val actionResults: List<ActionResult>,
   val startedActionUniqueIds: List<String>
)

@Serializable
class ItemStackInfo(
   val amount: Int,
   val itemConfigKey: String,
   val itemName: String,
   val itemDescription: String,
   val canBeEquipped: Boolean,
   val canBeDropped: Boolean,
   val isEquipped: Boolean,
   val spawnItemOnUseConfigKey: String?
)

@Serializable
class ItemStackInfoWrapper(
   val itemStackInfo: ItemStackInfo,
   val serializedAsJavaScript: String,
   val javaScriptVariableName: String
)

@Serializable
class InventoryInfo(
   val itemStacks: List<ItemStackInfoWrapper>
)

@JvmInline
@Serializable
value class AgentId(val value: String)


@Serializable
class SelfInfo(
   val entityInfoWrapper: EntityInfoWrapper,
   val corePersonality: String,
   val initialMemories: List<String>,
   val observationRadius: Double,
   val inventoryInfo: InventoryInfo,
   val equippedItemConfigKey: String?
)

@Serializable
class CraftingRecipeInfo(
   val itemConfigKey: String,
   val itemName: String,
   val description: String,
   val cost: ItemCollection,
   val amount: Int,
   val canCurrentlyAfford: Boolean
)

@Serializable
class CraftingRecipeInfoWrapper(
   val craftingRecipeInfo: CraftingRecipeInfo,
   val serializedAsJavaScript: String,
   val javaScriptVariableName: String
)

@Serializable
class GameConstants(
   val distanceUnit: String = "centimeters",
   val peopleSize: Double = 40.0
) {
   companion object {
      val default = GameConstants()
   }
}

@Serializable
class GameSimulationInfo(
   val worldBounds: Vector2,
   val craftingRecipeInfoWrappers: List<CraftingRecipeInfoWrapper>
)