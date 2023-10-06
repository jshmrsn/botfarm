package botfarmtest.game

import botfarm.game.agentintegration.scriptSequenceMockAgentBuilder
import botfarm.game.components.InventoryComponentData
import botfarm.game.components.getEquippedItemConfig
import botfarm.game.components.killedAtTime
import botfarm.game.config.EquipmentSlot
import botfarmshared.game.apidata.ScriptToRun
import botfarmshared.misc.Vector2
import kotlin.test.Test
import kotlin.test.assertTrue

private val testScript1 = ScriptToRun(
   scriptId = "test-script-1",
   script = """
   walkTo(vector2(1100.0, 1200.0))
   const axe = getCurrentNearbyEntities().find(it => it.itemOnGround && it.itemOnGround.itemTypeId == "axe")
   axe.itemOnGround.pickUp()
   equipItem("axe", null)
""".trimIndent()
)

private val testScript2 =  ScriptToRun(
   scriptId = "test-script-2",
   script = """
   const tree = getCurrentNearbyEntities().find(it => it.itemOnGround && it.itemOnGround.itemTypeId == "tree")
   tree.damageable.attackWithEquippedItem()
""".trimIndent()
)

class AgentScriptsTest {
   @Test
   fun test() = simulationTest(
      buildMockAgent = scriptSequenceMockAgentBuilder(
         scripts = listOf(testScript1, testScript2)
      )
   ) {
      val simulation = this.simulation

      simulation.spawnItem(
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

      val character = simulation.spawnAgentControlledCharacter(
         location = startLocation,
         agentType = "test"
      )

      assertTrue(character.getComponent<InventoryComponentData>().data.inventory.itemStacks.isEmpty())

      simulateUntil(
         description = "equip axe",
         delayMsPerTick = 100
      ) {
         character.getEquippedItemConfig(EquipmentSlot.Tool)?.key == "axe"
      }

      assertTrue(tree.killedAtTime == null)

      simulateUntil(
         delayMsPerTick = 10
      ) {
         tree.killedAtTime != null
      }
   }
}