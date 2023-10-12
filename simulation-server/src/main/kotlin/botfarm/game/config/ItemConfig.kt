package botfarm.game.config

import botfarm.engine.simulation.Config
import botfarm.game.components.CompositeAnimationSelection
import botfarmshared.game.apidata.ItemCollection
import botfarmshared.misc.Vector2

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

class SpawnItemOnKillConfig(
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

class DamageableConfig(
   val maxHp: Int,
   val spawnItemOnKillConfig: SpawnItemOnKillConfig? = null,
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

enum class CollisionFlag {
   Placement,
   Walking
}

class CollisionConfig(
   val width: Int,
   val height: Int,
   val collisionOffset: Vector2 = Vector2.zero,
   val flags: List<CollisionFlag> = CollisionFlag.entries
)

class ItemConfig(
   override val key: String,
   val name: String,
   val description: String,
   val spriteConfigKey: String,
   val iconUrl: String,
   val useCustomAnimationBaseName: String? = null,
   val collisionConfig: CollisionConfig? = null,
   val equippableConfig: EquippableConfig? = null,
   val storableConfig: StorableConfig? = null,
   val damageableConfig: DamageableConfig? = null,
   val craftableConfig: CraftableConfig? = null,
   val spawnItemOnKillConfig: SpawnItemOnKillConfig? = null, // tree spawns wood when cut down
   val growerConfig: GrowerConfig? = null, // farm plots receive and grow carrot seeds
   val growableConfig: GrowableConfig? = null, // carrot seeds grow into carrots
   val spawnItemOnUseConfig: SpawnItemOnUseConfig? = null // hoe spawns farm plots
) : Config()

