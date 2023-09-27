package botfarm.simulationserver.game

import botfarm.apidata.ItemCollection
import botfarm.simulationserver.simulation.Entity
import botfarm.simulationserver.simulation.EntityComponentData

data class ItemStack(
   val itemConfigKey: String,
   val amount: Int
   // jshmrsn: Could later add special attributes for single-item stacks here (e.g. unique writing of a book item)
)

data class Inventory(
   val itemStacks: List<ItemStack> = listOf()
)

data class InventoryComponentData(
   val inventory: Inventory = Inventory()
) : EntityComponentData()



fun Entity.getInventoryItemTotalAmount(itemConfigKey: String): Int {
   val inventoryComponent = this.getComponent<InventoryComponentData>()

   val existingStacks = inventoryComponent.data.inventory.itemStacks
      .filter { it.itemConfigKey == itemConfigKey }

   return existingStacks.sumOf { it.amount }
}

fun Entity.takeInventoryItemCollection(itemCollection: ItemCollection): Boolean {
   val canAfford = itemCollection.entries.all { costEntry ->
      this.getInventoryItemTotalAmount(costEntry.itemConfigKey) > costEntry.amount
   }

   if (!canAfford) {
      return false
   }

   itemCollection.entries.forEach {
      val result = this.takeInventoryItem(
         itemConfigKey = it.itemConfigKey,
         amount = it.amount
      )

      if (!result) {
         throw Exception("Expected all items to be taken away (${it.itemConfigKey}, ${it.amount})")
      }
   }

   return true
}

fun Entity.takeInventoryItem(itemConfigKey: String, amount: Int): Boolean {
   if (amount < 0) {
      throw Exception("takeItem: Amount cannot be negative")
   }

   if (amount == 0) {
      return true
   }

   val inventoryComponent = this.getComponent<InventoryComponentData>()

   val existingStacks = inventoryComponent.data.inventory.itemStacks
      .filter { it.itemConfigKey == itemConfigKey }
      .sortedBy { it.amount }
      .toMutableList()

   // Check if there are enough items in existingStacks
   val existingTotalAmount = this.getInventoryItemTotalAmount(itemConfigKey = itemConfigKey)

   if (existingTotalAmount < amount) {
      return false
   }

   // Perform the actual take operation if there are enough items
   inventoryComponent.modifyData {
      val itemStacks = it.inventory.itemStacks.toMutableList()

      var remainingToTake = amount // Initialize remainingToTake to the total amount required

      for (itemStack in existingStacks) {
         val amountToTake = Math.min(remainingToTake, itemStack.amount)

         val index = itemStacks.indexOf(itemStack)

         if (itemStack.amount == amountToTake) {
            itemStacks.removeAt(index)
         } else {
            itemStacks[index] = itemStack.copy(
               amount = itemStack.amount - amountToTake
            )
         }

         remainingToTake -= amountToTake
         if (remainingToTake == 0) {
            break
         }
      }

      it.copy(
         inventory = it.inventory.copy(
            itemStacks = itemStacks
         )
      )
   }

   return true
}

fun Entity.giveInventoryItem(itemConfigKey: String, amount: Int) {
   if (amount == 0) {
      return
   }

   val itemConfig = this.simulation.getConfig<ItemConfig>(itemConfigKey)
   val inventoryComponent = this.getComponent<InventoryComponentData>()

   if (amount < 0) {
      throw Exception("giveItem: Amount cannot be negative")
   }

   var remaining = amount

   inventoryComponent.modifyData {
      val itemStacks = it.inventory.itemStacks.toMutableList()

      if (itemConfig.maxStackSize != null && itemConfig.maxStackSize <= 0) {
         throw Exception("Invalid maxStackSize would cause infinite loop: $itemConfigKey ${itemConfig.maxStackSize}")
      }

      while (true) {
         val smallestNotFullExistingStack = itemStacks
            .filter { it.itemConfigKey == itemConfigKey }
            .filter { itemConfig.maxStackSize == null || it.amount < itemConfig.maxStackSize }
            .sortedBy { it.amount }
            .firstOrNull()

         if (smallestNotFullExistingStack == null ||
            itemConfig.maxStackSize == null ||
            smallestNotFullExistingStack.amount < itemConfig.maxStackSize
         ) {
            val smallestNotFullExistingStackSize = smallestNotFullExistingStack?.amount ?: 0

            val amountToAdd = if (itemConfig.maxStackSize == null) {
               remaining
            } else {
               Math.min(remaining, itemConfig.maxStackSize - smallestNotFullExistingStackSize)
            }

            if (smallestNotFullExistingStack != null) {
               val index = itemStacks.indexOf(smallestNotFullExistingStack)

               itemStacks[index] = smallestNotFullExistingStack.copy(
                  amount = smallestNotFullExistingStack.amount + amountToAdd
               )
            } else {
               itemStacks.add(
                  ItemStack(
                     itemConfigKey = itemConfigKey,
                     amount = amountToAdd
                  )
               )
            }

            remaining -= amountToAdd

            if (remaining == 0) {
               break
            }
         }
      }

      it.copy(
         inventory = it.inventory.copy(
            itemStacks = itemStacks
         )
      )
   }
}