package botfarm.simulationserver.game

import botfarm.apidata.ItemCollection
import botfarm.simulationserver.simulation.Config
import botfarm.simulationserver.simulation.EntityComponentData


class ItemConfig(
   override val key: String,
   val name: String,
   val description: String,
   val spriteConfigKey: String,
   val iconUrl: String,
   val canBePickedUp: Boolean = false,
   val canBeDropped: Boolean = true,
   val maxHp: Int = 100,
   val canBeEquipped: Boolean = false,
   val canBeDamagedByItem: String? = null,
   val spawnItemOnDestruction: String? = null,
   val spawnMinStacks: Int = 1,
   val spawnMaxStacks: Int = 1,
   val spawnMinAmountPerStack: Int = 1,
   val spawnMaxAmountPerStack: Int = 1,
   val maxStackSize: Int? = null,
   val blocksPathfinding: Boolean = false,
   val craftingCost: ItemCollection? = null,
   val craftingAmount: Int = 1,
   val equippedCompositeAnimation: CompositeAnimation? = null
) : Config()

data class ItemComponentData(
   val hp: Int,
   val itemConfigKey: String,
   val amount: Int = 1
) : EntityComponentData()
