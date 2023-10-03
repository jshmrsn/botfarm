package botfarm.game.config

import botfarm.engine.simulation.Config
import botfarm.game.components.CompositeAnimationSelection
import botfarmshared.game.apidata.ItemCollection

class GrowerConfig(
   val canReceiveGrowableItemConfigKeys: List<String>
)

class GrowableConfig(
   val growingSpriteConfigKey: String,
   val progressAnimationNames: List<String>,
   val timeToGrow: Double,
   val growsIntoItemConfigKey: String,
   val growsIntoItemQuantity: RandomItemQuantity
)

class SpawnItemOnUseConfig(
   val spawnItemConfigKey: String,
   val randomDistanceScale: Double = 0.0,
   val quantity: RandomItemQuantity = RandomItemQuantity.amount(1)
)

class SpawnItemOnDestructionConfig(
   val spawnItemConfigKey: String,
   val quantity: RandomItemQuantity
)

class CraftableConfig(
   val craftingCost: ItemCollection,
   val craftingAmount: Int = 1,
)

enum class EquipmentSlot {
   Tool,
   Chest,
   Pants,
   Shoes,
   Hat
}

class KillableConfig(
   val maxHp: Int,
   val spawnItemOnDestructionConfig: SpawnItemOnDestructionConfig? = null,
   val damageableByEquippedToolItemConfigKey: String? = null
)

class StorableConfig(
   val canBeDropped: Boolean = true,
   val maxStackSize: Int? = 0
)

class EquippableConfig(
   val equipmentSlot: EquipmentSlot,
   val equippedCompositeAnimation: CompositeAnimationSelection? = null
)

class ItemConfig(
   override val key: String,
   val name: String,
   val description: String,
   val spriteConfigKey: String,
   val iconUrl: String,
   val useCustomAnimationBaseName: String? = null,
   val blocksPathfinding: Boolean = false,
   val blocksPlacement: Boolean = false,
   val equippableConfig: EquippableConfig? = null,
   val storableConfig: StorableConfig? = null,
   val damageableConfig: KillableConfig? = null,
   val craftableConfig: CraftableConfig? = null,
   val spawnItemOnDestructionConfig: SpawnItemOnDestructionConfig? = null, // tree spawns wood when cut down
   val growerConfig: GrowerConfig? = null, // farm plots receive and grow carrot seeds
   val growableConfig: GrowableConfig? = null, // carrot seeds grow into carrots
   val spawnItemOnUseConfig: SpawnItemOnUseConfig? = null // hoe spawns farm plots
) : Config()

