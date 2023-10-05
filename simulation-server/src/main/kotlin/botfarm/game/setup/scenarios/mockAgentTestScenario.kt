package botfarm.game.setup.scenarios

import botfarm.game.agentintegration.MockAgent
import botfarm.game.setup.GameScenario
import botfarm.game.setup.SpawnPlayersMode
import botfarmshared.game.apidata.Action
import botfarmshared.game.apidata.AgentSyncOutput
import botfarmshared.game.apidata.AgentSyncResponse
import botfarmshared.game.apidata.WalkAction
import botfarmshared.misc.Vector2

val mockAgentTestScenario = GameScenario(
   identifier = "mock-agent-test",
   name = "Mock Agent Test",
   spawnPlayersEntityMode = SpawnPlayersMode.None,
   buildMockAgent = {
      var hasSentScriptResponse = false

      MockAgent { request ->
         val outputs = mutableListOf<AgentSyncOutput>()

         if (!hasSentScriptResponse) {
            hasSentScriptResponse = true
            outputs.add(
               AgentSyncOutput(
                  actions = listOf(
                     Action(
                        reason = "test-1",
                        walk = WalkAction(
                           location = Vector2(1000.0, 2500.0)
                        )
                     ),
                     Action(
                        reason = "test-2",
                        walk = WalkAction(
                           location = Vector2(500.0, 1000.0)
                        )
                     )
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
      it.spawnAgent(
         location = Vector2(1000.0, 0.0),
         agentType = "test"
      )
   }
)