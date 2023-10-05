package botfarm.game.setup.scenarios

import botfarm.game.agentintegration.MockAgent
import botfarm.game.setup.GameScenario
import botfarm.game.setup.SpawnPlayersMode
import botfarmshared.game.apidata.Action
import botfarmshared.game.apidata.AgentSyncOutput
import botfarmshared.game.apidata.AgentSyncResponse
import botfarmshared.game.apidata.WalkAction
import botfarmshared.misc.Vector2
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
                  script = """
                     speak("Hello 1")
                     walkTo(vector2(1000, 2500))
                     speak("Hello 2")
                     walkTo(vector2(500, 1000))
                     speak("Hello 3")
                  """.trimIndent()
//                  actions = listOf(
//                     Action(
//                        reason = "test-1",
//                        walk = WalkAction(
//                           location = Vector2(1000.0, 2500.0)
//                        )
//                     ),
//                     Action(
//                        reason = "test-2",
//                        walk = WalkAction(
//                           location = Vector2(500.0, 1000.0)
//                        )
//                     )
//                  )
               )
            )
         } else if (getCurrentUnixTimeSeconds() - startTime > 30 && !hasSentSecondScriptResponse) {
            hasSentSecondScriptResponse = true

            outputs.add(
               AgentSyncOutput(
                  script = """
                     speak("Additional script")
                     walkTo(vector2(Math.random()  * 2000, Math.random()  * 2000))
                     speak("Additional script step 2")
                     walkTo(vector2(Math.random()  * 2000, Math.random()  * 2000))
                     speak("Additional script step 3")
                  """.trimIndent()
//                  actions = listOf(
//                     Action(
//                        reason = "test-1",
//                        walk = WalkAction(
//                           location = Vector2(1000.0, 2500.0)
//                        )
//                     ),
//                     Action(
//                        reason = "test-2",
//                        walk = WalkAction(
//                           location = Vector2(500.0, 1000.0)
//                        )
//                     )
//                  )
               )
            )
         }

         AgentSyncResponse(
            outputs = outputs
         )
      }
   },
   configureGameSimulationCallback = {
      it.spawnAgent(
         location = Vector2(1000.0, 0.0),
         agentType = "test"
      )
   }
)