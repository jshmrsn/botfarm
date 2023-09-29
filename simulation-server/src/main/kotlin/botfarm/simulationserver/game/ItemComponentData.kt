package botfarm.simulationserver.game

import botfarm.apidata.ItemCollection
import botfarm.misc.RandomConfig
import botfarm.simulationserver.simulation.Config
import botfarm.simulationserver.simulation.Entity
import botfarm.simulationserver.simulation.EntityComponentData
import kotlinx.serialization.Serializable

class RandomItemQuantity private constructor(
   val amount: RandomConfig? = null,
   val stackCount: RandomConfig? = null,
   val amountPerStack: RandomConfig? = RandomConfig.one
) {
   companion object {
      fun amount(fixed: Int) = RandomItemQuantity(
         amount = RandomConfig.fixed(fixed)
      )

      fun amount(min: Int, max: Int) = RandomItemQuantity(
         amount = RandomConfig.range(min = min, max = max)
      )

      fun stacks(fixed: Int) = RandomItemQuantity(
         stackCount = RandomConfig.fixed(fixed),
         amountPerStack = RandomConfig.one
      )

      fun stacks(min: Int, max: Int) = RandomItemQuantity(
         stackCount = RandomConfig.range(min = min, max = max),
         amountPerStack = RandomConfig.one
      )

      fun stacksOfAmount(
         stackCount: RandomConfig,
         amountPerStack: RandomConfig
      ) = RandomItemQuantity(
         stackCount = stackCount,
         amountPerStack = amountPerStack
      )
   }

   fun resolveStackAmountsForItemConfig(itemConfig: ItemConfig): List<Int> {
      val maxStackSize = itemConfig.storableConfig?.maxStackSize

      if (this.amount != null) {
         val amount = this.amount.rollInt()

         return if (maxStackSize != null) {
            var remaining = amount
            val stackAmounts = mutableListOf<Int>()
            while (remaining > 0) {
               val amountToAdd = Math.min(maxStackSize, remaining)
               remaining -= amountToAdd
               stackAmounts.add(amountToAdd)
            }

            stackAmounts
         } else {
            listOf(this.amount.rollInt())
         }
      }

      if (this.stackCount != null) {
         val stackAmounts = mutableListOf<Int>()
         val stackCount = this.stackCount.rollInt()

         for (i in 0..stackCount) {
            if (this.amountPerStack != null) {
               val amountPerStack = this.amountPerStack.rollInt()
               stackAmounts.add(amountPerStack)
            } else {
               stackAmounts.add(maxStackSize ?: 1)
            }
         }

         return stackAmounts
      }

      throw Exception("invalid resolveForItemConfig")
   }
}

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
   val canBeDamagedByToolItemConfigKey: String? = null
)

class StorableConfig(
   val canBeDropped: Boolean = true,
   val maxStackSize: Int? = 0
)

class EquippableConfig(
   val equipmentSlot: EquipmentSlot,
   val equippedCompositeAnimation: CompositeAnimation? = null
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
   val killableConfig: KillableConfig? = null,
   val craftableConfig: CraftableConfig? = null,
   val spawnItemOnDestructionConfig: SpawnItemOnDestructionConfig? = null, // tree spawns wood when cut down
   val growerConfig: GrowerConfig? = null, // farm plots receive and grow carrot seeds
   val growableConfig: GrowableConfig? = null, // carrot seeds grow into carrots
   val spawnItemOnUseConfig: SpawnItemOnUseConfig? = null // hoe spawns farm plots
) : Config()

data class ItemComponentData(
   val itemConfigKey: String,
   val amount: Int = 1
) : EntityComponentData()

data class KillableComponentData(
   val hp: Int,
   val killedAtTime: Double? = null
) : EntityComponentData()

val Entity.killedAtTime
   get() = this.getComponentOrNull<KillableComponentData>()?.data?.killedAtTime

val Entity.isDead
   get() = this.isDestroyed || this.getComponentOrNull<KillableComponentData>()?.data?.killedAtTime != null


@Serializable
data class ActiveGrowth(
   val startTime: Double,
   val itemConfigKey: String
) : EntityComponentData()

data class GrowerComponentData(
   val activeGrowth: ActiveGrowth? = null
) : EntityComponentData()
