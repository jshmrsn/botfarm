package botfarm.apidata

import botfarm.misc.Vector2
import kotlinx.serialization.Serializable


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
   val entityId: String,
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
class ActionOnEntityRecord(
   val startedAtTime: Double,
   val actionId: String,
   val targetEntityId: String,
   val reason: String?
)

@Serializable
class ActionOnInventoryItemRecord(
   val startedAtTime: Double,
   val itemConfigKey: String,
   val actionId: String,
   val reason: String?,
   val amount: Int?
)

@Serializable
class CraftItemActionRecord(
   val startedAtTime: Double,
   val itemConfigKey: String,
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
   val age: Int,
   val description: String,
   val equippedItemInfo: ItemInfo? = null
)

@Serializable
class ItemEntityInfo(
   val itemConfigKey: String,
   val itemName: String,
   val description: String
)

@Serializable
class EntityInfo(
   val observedAtSimulationTime: Double,
   val entityId: String,
   val location: Vector2,
   val availableActionIds: List<String>? = null,
   val itemEntityInfo: ItemEntityInfo?,
   val characterEntityInfo: CharacterEntityInfo?
)

@Serializable
class ActivityStreamEntryRecord(
   val time: Double,
   val title: String,
   val message: String? = null,
   val actionType: String? = null,
   val sourceLocation: Vector2? = null,
   val sourceEntityId: String? = null,
   val targetEntityId: String? = null
)

@Serializable
class SelfSpokenMessage(
   val message: String,
   val location: Vector2,
   val time: Double,
   val reason: String
)

@Serializable
class Observations(
   val spokenMessages: List<ObservedSpokenMessage>,
   val selfSpokenMessages: List<SelfSpokenMessage>,
   val entitiesById: Map<String, EntityInfo>,
   val movementRecords: List<MovementRecord>,
   val actionOnEntityRecords: List<ActionOnEntityRecord>,
   val actionOnInventoryItemActionRecords: List<ActionOnInventoryItemRecord>,
   val craftItemActionRecords: List<CraftItemActionRecord>,
   val activityStreamEntries: List<ActivityStreamEntryRecord>
)

@Serializable
class ItemStackInfo(
   val amount: Int,
   val itemConfigKey: String,
   val availableActionIds: List<String>,
   val itemName: String,
   val itemDescription: String,
   val canBeEquipped: Boolean,
   val canBeDropped: Boolean
)

@Serializable
class InventoryInfo(
   val itemStacks: List<ItemStackInfo>
)

@Serializable
class SelfInfo(
   val agentId: String,
   val entityInfo: EntityInfo,
   val corePersonality: String,
   val initialMemories: List<String>,
   val observationDistance: Double,
   val inventoryInfo: InventoryInfo
)

@Serializable
class CraftingRecipe(
   val itemConfigKey: String,
   val itemName: String,
   val description: String,
   val cost: ItemCollection,
   val amount: Int
)

@Serializable
class AgentStepInputs(
   val agentType: String,
   val stepId: String,
   val simulationId: String,
   val simulationTime: Double,
   val craftingRecipes: List<CraftingRecipe>,
   val selfInfo: SelfInfo,
   val newObservations: Observations,
   val distanceUnit: String,
   val peopleSize: Double
)