package botfarm.game.config

import botfarmshared.misc.RandomConfig

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

         for (i in 0..< stackCount) {
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