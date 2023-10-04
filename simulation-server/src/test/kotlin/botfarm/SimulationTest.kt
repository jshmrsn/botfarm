package botfarm

import botfarm.common.isMoving
import botfarm.common.resolvePosition
import botfarm.engine.simulation.*
import botfarm.game.GameSimulation
import botfarm.game.components.*
import botfarm.game.setup.GameScenario
import botfarm.game.setup.addCharacterConfigs
import botfarm.game.setup.addItemConfigs
import botfarmshared.engine.apidata.SimulationId
import botfarmshared.misc.Vector2
import kotlin.math.min
import kotlin.test.*

class SimulationTest {
   @Test
   fun test() {
      val agentServerIntegration = createTestAgentServerIntegration()

      val configs = mutableListOf<Config>()
      addCharacterConfigs(configs)
      addItemConfigs(configs)

      val simulationData = SimulationData(
         scenarioInfo = ScenarioInfo(
            identifier = "test",
            gameIdentifier = "game"
         ),
         simulationId = SimulationId(value = "test-simulation-1"),
         configs = configs
      )

      val simulationContainer = SimulationContainer()

      val scenario = GameScenario(
         identifier = "test"
      )

      val simulationContext = SimulationContext(
         wasCreatedByAdmin = true,
         simulationContainer = simulationContainer,
         createdByUserSecret = UserSecret("test"),
         scenario = scenario,
         agentServerIntegration = agentServerIntegration,
         noClientsConnectedTerminationTimeoutSeconds = null
      )

      fun simulateSeconds(
         duration: Double,
         shouldStop: () -> Boolean = { false }
      ) {
         var durationRemaining = duration

         while (durationRemaining >= 0.00001) {
            val deltaTime = min(0.2, durationRemaining)
            durationRemaining -= deltaTime
            simulationContainer.tick(
               deltaTime = deltaTime
            )

            if (shouldStop()) {
               break
            }
         }
      }

      fun simulateUntil(
         description: String = "default",
         maxSeconds: Double = 30.0,
         until: () -> Boolean = { false }
      ) {
         var didSucceed = false
         simulateSeconds(
            duration = maxSeconds,
            shouldStop = {
               if (until()) {
                  didSucceed = true
                  true
               } else {
                  false
               }
            }
         )

         if (!didSucceed) {
            throw Exception("simulateUntil: Max time reached (${maxSeconds.toInt()}) ($description)")
         }
      }

      val simulation = GameSimulation(
         context = simulationContext,
         data = simulationData
      )

      simulationContainer.addSimulation(simulation)

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


      fun getItemEntities(itemConfigKey: String) = simulation.entities.filter {
         it.getComponentOrNull<ItemComponentData>()?.data?.itemConfigKey == itemConfigKey
      }

      fun getWoodEntities() = getItemEntities("wood")

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

         simulateUntil {
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

         simulateUntil {
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

         simulateUntil {
            tree.getComponent<DamageableComponentData>().data.killedAtTime != null
         }

         assertTrue(character.resolvePosition().distance(tree.resolvePosition()) < 70.0)
         assertTrue(tree.exists)
         assertTrue(getWoodEntities().isNotEmpty())

         simulateUntil {
            !tree.exists
         }

         assertEquals(character.getInventoryItemTotalAmount("wood"), 0)

         val woodCountStart = getWoodEntities().size

         simulation.pickUpItem(
            pickingUpEntity = character,
            targetEntity = getWoodEntities().first()
         )

         simulateUntil {
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