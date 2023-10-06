package botfarmtest.game

import botfarm.common.isMoving
import botfarm.common.resolvePosition
import botfarm.game.GameSimulation
import botfarm.game.components.*
import botfarmshared.misc.Vector2
import kotlin.test.*

class SimulationTest {
   @Test
   fun test() = simulationTest {
      val simulation = this.simulation

      val axe = simulation.spawnItem(
         itemConfigKey = "axe",
         location = Vector2(1100.0, 1000.0)
      )

      val tree = simulation.spawnItem(
         itemConfigKey = "tree",
         location = Vector2(1200.0, 1000.0)
      )

      simulation.spawnItem(
         itemConfigKey = "boulder",
         location = Vector2(1300.0, 1000.0)
      )

      val startLocation = Vector2(1000.0, 1000.0)

      val character = simulation.spawnCharacter(
         location = startLocation
      )

      assert(character.resolvePosition().distance(startLocation) < 1.0)

      run {
         val endLocation = Vector2(900.0, 1100.0)
         simulation.startEntityMovement(
            entity = character,
            endPoint = endLocation
         )

         assertTrue(character.resolvePosition().distance(startLocation) < 1.0)

         simulateSeconds(5.0)

         assertTrue(character.resolvePosition().distance(endLocation) < 1.0)
      }

      run {
         assertEquals(
            GameSimulation.InteractWithEntityUsingEquippedItemResult.NoToolItemEquipped,
            simulation.interactWithEntityUsingEquippedItem(
               interactingEntity = character,
               targetEntity = tree
            )
         )

         assertEquals(getWoodEntities().size, 0)

         assertEquals(
            GameSimulation.EquipItemResult.ItemNotInInventory,
            simulation.equipItem(
               entity = character,
               expectedItemConfigKey = "axe"
            )
         )

         assertEquals(
            GameSimulation.PickUpItemResult.TooFar,
            simulation.pickUpItem(
               pickingUpEntity = character,
               targetEntity = axe
            )
         )

         assertFalse(character.getComponent<InventoryComponentData>().data.inventory.itemStacks.any {
            it.itemConfigKey == "axe"
         })

         assertIs<GameSimulation.MoveToResult.Success>(
            simulation.startEntityMovement(
               entity = character,
               endPoint = axe.resolvePosition() + Vector2(-10.0, 0.0)
            )
         )

         simulateUntil {
            !character.isMoving
         }

         assertEquals(
            GameSimulation.PickUpItemResult.Success,
            simulation.pickUpItem(
               pickingUpEntity = character,
               targetEntity = axe
            )
         )

         simulateUntil(
            description = "picked up axe",
         ) {
            character.getComponent<InventoryComponentData>().data.inventory.itemStacks.any {
               it.itemConfigKey == "axe"
            }
         }

         assertEquals(
            GameSimulation.EquipItemResult.Success,
            simulation.equipItem(
               entity = character,
               expectedItemConfigKey = "axe"
            )
         )

         simulateSeconds(1.0) // wait for pickup animation to finish

         assertEquals(
            GameSimulation.InteractWithEntityUsingEquippedItemResult.TooFar,
            simulation.interactWithEntityUsingEquippedItem(
               interactingEntity = character,
               targetEntity = tree
            )
         )

         assertIs<GameSimulation.MoveToResult.Success>(
            simulation.startEntityMovement(
               entity = character,
               endPoint = tree.resolvePosition() + Vector2(-5.0, -5.0)
            )
         )

         simulateUntil(
            description = "character finished walking to tree"
         ) {
            !character.isMoving
         }

         assertEquals(
            GameSimulation.InteractWithEntityUsingEquippedItemResult.Success,
            simulation.interactWithEntityUsingEquippedItem(
               interactingEntity = character,
               targetEntity = tree
            )
         )

         assertNull(tree.getComponent<DamageableComponentData>().data.killedAtTime)

         simulateUntil(
            description = "tree killed"
         ) {
            tree.getComponent<DamageableComponentData>().data.killedAtTime != null
         }

         assertTrue(character.resolvePosition().distance(tree.resolvePosition()) < 70.0)
         assertTrue(tree.exists)
         assertTrue(getWoodEntities().isNotEmpty())

         simulateUntil(
            description = "tree cleaned up"
         ) {
            !tree.exists
         }

         assertEquals(character.getInventoryItemTotalAmount("wood"), 0)

         val woodCountStart = getWoodEntities().size


         val firstWood = getWoodEntities().first()
         assertIs<GameSimulation.MoveToResult.Success>(
            simulation.startEntityMovement(
               entity = character,
               endPoint = firstWood.resolvePosition()
            )
         )

         simulateUntil(
            description = "character finished walking to wood"
         ) {
            !character.isMoving
         }

         assertEquals(
            GameSimulation.PickUpItemResult.Success, simulation.pickUpItem(
               pickingUpEntity = character,
               targetEntity = firstWood
            )
         )

         simulateUntil(
            description = "wood no longer exists after pickup"
         ) {
            getWoodEntities().size < woodCountStart
         }

         assertTrue(character.getInventoryItemTotalAmount("wood") > 0)
      }

      run {
         assertEquals(
            GameSimulation.CraftItemResult.ItemCannotBeCrafted,
            simulation.craftItem(
               entity = character,
               itemConfigKey = "tree"
            )
         )

         assertEquals(
            GameSimulation.CraftItemResult.CannotAfford,
            simulation.craftItem(
               entity = character,
               itemConfigKey = "house"
            )
         )

         assertTrue(getItemEntities("house").isEmpty())

         character.giveInventoryItem("wood", 1000)

         assertEquals(
            GameSimulation.CraftItemResult.CannotAfford,
            simulation.craftItem(
               entity = character,
               itemConfigKey = "house"
            )
         )

         character.giveInventoryItem("stone", 1000)

         assertEquals(
            GameSimulation.CraftItemResult.Success,
            simulation.craftItem(
               entity = character,
               itemConfigKey = "house"
            )
         )

         assertTrue(getItemEntities("house").isNotEmpty())

         assertTrue(character.getInventoryItemTotalAmount("pickaxe") == 0)

         assertEquals(
            GameSimulation.CraftItemResult.Success,
            simulation.craftItem(
               entity = character,
               itemConfigKey = "pickaxe"
            )
         )

         assertTrue(getItemEntities("pickaxe").isEmpty())

         assertTrue(character.getInventoryItemTotalAmount("pickaxe") == 1)
      }
   }
}