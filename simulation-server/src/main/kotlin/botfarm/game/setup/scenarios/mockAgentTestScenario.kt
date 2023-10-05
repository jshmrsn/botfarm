package botfarm.game.setup.scenarios

import botfarm.game.agentintegration.MockAgent
import botfarm.game.setup.GameScenario
import botfarm.game.setup.SpawnPlayersMode
import botfarmshared.game.apidata.*
import botfarmshared.misc.Vector2
import botfarmshared.misc.buildShortRandomIdentifier
import botfarmshared.misc.getCurrentUnixTimeSeconds

val mockAgentTestScenario = GameScenario(
   identifier = "mock-agent-test",
   name = "Mock Agent Test",
   spawnPlayersEntityMode = SpawnPlayersMode.None,
   buildMockAgent = {
      var hasSentScriptResponse = false
      var hasSentSecondScriptResponse = false

      val startTime = getCurrentUnixTimeSeconds()
      MockAgent { request ->
         val outputs = mutableListOf<AgentSyncOutput>()

         if (!hasSentScriptResponse) {
            hasSentScriptResponse = true
            outputs.add(
               AgentSyncOutput(
                  scriptToRun = ScriptToRun(
                     script = """
                     speak("Hello 1")
                     walkTo(vector2(1000, 2500))
                     speak("Hello 2")
                     walkTo(vector2(500, 1000))
                     speak("Hello 3")
                  """.trimIndent(),
                     scriptId = buildShortRandomIdentifier()
                  )
               )
            )
         } else if (getCurrentUnixTimeSeconds() - startTime > 30 && !hasSentSecondScriptResponse) {
            hasSentSecondScriptResponse = true

            outputs.add(
               AgentSyncOutput(
                  scriptToRun = ScriptToRun(
                     script = """
                     speak("Additional script")
                     walkTo(vector2(Math.random()  * 2000, Math.random()  * 2000))
                     speak("Additional script step 2")
                     walkTo(vector2(Math.random()  * 2000, Math.random()  * 2000))
                     speak("Additional script step 3")
                  """.trimIndent(),
                     scriptId = buildShortRandomIdentifier()
                  )
               )
            )
         }

         AgentSyncResponse(
            outputs = outputs
         )
      }
   },
   configureGameSimulationCallback = {
      it.spawnAgentControlledCharacter(
         location = Vector2(1000.0, 0.0),
         agentType = "test"
      )
   }
)