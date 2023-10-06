package botfarm.game.setup.scenarios

import botfarm.game.agentintegration.scriptSequenceMockAgentBuilder
import botfarm.game.setup.GameScenario
import botfarm.game.setup.SpawnPlayersMode
import botfarmshared.game.apidata.ScriptToRun
import botfarmshared.misc.Vector2

val mockAgentTestScenario = GameScenario(
   identifier = "mock-agent-test",
   name = "Mock Agent Test",
   spawnPlayersEntityMode = SpawnPlayersMode.None,
   buildMockAgent = scriptSequenceMockAgentBuilder(listOf(
      ScriptToRun(
         scriptId = "script-1",
         script = """
                     speak("Hello 1")
                     walkTo(vector2(1000, 2500))
                     speak("Hello 2")
                     walkTo(vector2(500, 1000))
                     speak("Hello 3")
                  """.trimIndent()
      ),
      ScriptToRun(
         scriptId = "script-2",
         script = """
                     speak("Additional script")
                     walkTo(vector2(Math.random()  * 2000, Math.random()  * 2000))
                     speak("Additional script step 2")
                     walkTo(vector2(Math.random()  * 2000, Math.random()  * 2000))
                     speak("Additional script step 3")
                  """.trimIndent()
      )
   )),
   configureGameSimulationCallback = {
      it.spawnAgentControlledCharacter(
         location = Vector2(1000.0, 0.0),
         agentType = "test"
      )
   }
)