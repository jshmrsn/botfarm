package botfarm.game.components

import botfarmshared.game.apidata.ItemCollection
import botfarmshared.misc.removed
import botfarmshared.misc.replaced
import botfarm.engine.simulation.Entity
import botfarm.engine.simulation.EntityComponentData
import botfarm.game.config.EquipmentSlot
import botfarm.game.config.ItemConfig

data class ItemStack(
   val itemConfigKey: String,
   val amount: Int,
   val isEquipped: Boolean = false
   // jshmrsn: Could later add special attributes for single-item stacks here (e.g. unique writing of a book item)
)

data class Inventory(
   val itemStacks: List<ItemStack> = listOf()
)

data class InventoryComponentData(
   val inventory: Inventory = Inventory()
) : EntityComponentData()

fun Entity.getEquippedItemConfig(
   equipmentSlot: EquipmentSlot
): ItemConfig? = this.getEquippedItemConfigAndStackIndex(equipmentSlot)?.second

fun Entity.getEquippedItemConfigAndStackIndex(
   equipmentSlot: EquipmentSlot
): Pair<Int, ItemConfig>? {
   val inventory = this.getComponent<InventoryComponentData>().data.inventory
   return inventory.itemStacks
      .mapIndexed { index, it -> index to it }
      .firstNotNullOfOrNull {
      if (it.second.isEquipped) {
         val itemConfig = this.simulation.getConfig<ItemConfig>(it.second.itemConfigKey)
         if (itemConfig.equippableConfig?.equipmentSlot == equipmentSlot) {
            Pair(it.first, itemConfig)
         } else {
            null
         }
      } else {
         null
      }
   }
}


fun Entity.getInventoryItemTotalAmount(itemConfigKey: String): Int {
   val inventoryComponent = this.getComponent<InventoryComponentData>()

   val existingStacks = inventoryComponent.data.inventory.itemStacks
      .filter { it.itemConfigKey == itemConfigKey }

   return existingStacks.sumOf { it.amount }
}

fun Entity.takeInventoryItemCollection(itemCollection: ItemCollection): Boolean {
   val canAfford = itemCollection.entries.all { costEntry ->
      this.getInventoryItemTotalAmount(costEntry.itemConfigKey) >= costEntry.amount
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

fun Entity.takeInventoryItemFromStack(
   itemConfigKey: String,
   stackIndex: Int,
   amountToTake: Int
): Boolean {
   if (amountToTake < 0) {
      throw Exception("takeItem: Amount cannot be negative")
   }

   if (amountToTake == 0) {
      return true
   }

   val inventoryComponent = this.getComponent<InventoryComponentData>()

   val itemStack = inventoryComponent.data.inventory.itemStacks.getOrNull(stackIndex)

   if (itemStack == null ||
      itemStack.itemConfigKey != itemConfigKey) {
      return false
   }

   if (itemStack.amount < amountToTake) {
      return false
   }

   inventoryComponent.modifyData {
      it.copy(
         inventory = it.inventory.copy(
            itemStacks = if (itemStack.amount == amountToTake) {
               it.inventory.itemStacks.removed(
                  index = stackIndex
               )
            } else {
               it.inventory.itemStacks.replaced(
                  index = stackIndex,
                  value = itemStack.copy(
                     amount = itemStack.amount - amountToTake
                  )
               )
            }
         )
      )
   }

   return true
}

fun Entity.takeInventoryItem(
   itemConfigKey: String,
   amount: Int
): Boolean {
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


      val maxStackSize = itemConfig.storableConfig?.maxStackSize

      if (maxStackSize != null && maxStackSize <= 0) {
         throw Exception("Invalid maxStackSize would cause infinite loop: $itemConfigKey ${maxStackSize}")
      }

      while (true) {
         val smallestNotFullExistingStack = itemStacks
            .filter { it.itemConfigKey == itemConfigKey }
            .filter { maxStackSize == null || it.amount < maxStackSize }
            .sortedBy { it.amount }
            .firstOrNull()

         if (smallestNotFullExistingStack == null ||
            maxStackSize == null ||
            smallestNotFullExistingStack.amount < maxStackSize
         ) {
            val smallestNotFullExistingStackSize = smallestNotFullExistingStack?.amount ?: 0

            val amountToAdd = if (maxStackSize == null) {
               remaining
            } else {
               Math.min(remaining, maxStackSize - smallestNotFullExistingStackSize)
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