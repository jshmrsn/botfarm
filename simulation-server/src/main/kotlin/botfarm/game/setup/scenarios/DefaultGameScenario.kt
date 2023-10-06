package botfarm.game.setup.scenarios

import botfarm.game.GameSimulation
import botfarm.game.setup.GameScenario
import botfarm.game.setup.SpawnPlayersMode
import botfarmshared.misc.Vector2

class DefaultGameScenario : GameScenario(
   identifier = "default",
   name = "Single Agent (Script Execution Agent)"
) {
   override fun configureGameSimulation(simulation: GameSimulation) {
      spawnCommonEnvironmentEntities(simulation)

      simulation.spawnAgentControlledCharacter(
         name = "Joe",
         corePersonality = "Friendly and talkative. Enjoys conversation. Happy to take suggestions.",
         initialMemories = listOf(
            "I want to build a new house.",
            "I should be nice to new people in case we can become friends, but if they mistreat me I should stop doing what they tell me to do."
         ),
         agentType = "script",
         bodySelections = simulation.buildRandomCharacterBodySelections(
            bodyType = "male",
            hairColor = "black",
            skinColor = "light"
         ),
         age = 25,
         location = Vector2(3000.0, 3000.0)
      )
   }
}

class SpectateAgentsGameScenario : GameScenario(
   identifier = "spectate-agents",
   name = "Spectate Two Agents (Script Execution Agent)",
   spawnPlayersEntityMode = SpawnPlayersMode.None
) {
   override fun configureGameSimulation(simulation: GameSimulation) {
      spawnCommonEnvironmentEntities(simulation)

      simulation.spawnAgentControlledCharacter(
         name = "Joe",
         corePersonality = "Friendly. Enjoys conversation. Enjoys walking around randomly.",
         initialMemories = listOf(
            "I want to build a new house, but I shouldn't bother people about it unless it seems relevant.",
            "I should be nice to new people in case we can become friends, but if they mistreat me I should stop doing what they tell me to do."
         ),
         agentType = "script",
         bodySelections = simulation.buildRandomCharacterBodySelections(
            bodyType = "male",
            hairColor = "black",
            skinColor = "light"
         ),
         age = 25,
         location = Vector2(3000.0, 3000.0)
      )

      simulation.spawnAgentControlledCharacter(
         name = "Linda",
         corePersonality = "Friendly. Enjoys conversation. Enjoys walking around randomly.",
         initialMemories = listOf(
            "I want to build a new house, but I shouldn't bother people about it unless it seems relevant.",
            "I should be nice to new people in case we can become friends, but if they mistreat me I should stop doing what they tell me to do."
         ),
         agentType = "script",
         bodySelections = simulation.buildRandomCharacterBodySelections(),
         age = 25,
         location = Vector2(2500.0, 2200.0)
      )
   }
}

class LegacyAgentGameScenario : GameScenario(
   identifier = "json",
   name = "Single Agent (JSON Action Agent)"
) {
   override fun configureGameSimulation(simulation: GameSimulation) {
      spawnCommonEnvironmentEntities(simulation)

      simulation.spawnAgentControlledCharacter(
         name = "Joe",
         corePersonality = "Friendly. Enjoys conversation. Enjoys walking around randomly.",
         initialMemories = listOf(
            "I want to build a new house, but I shouldn't bother people about it unless it seems relevant.",
            "I should be nice to new people in case we can become friends, but if they mistreat me I should stop doing what they tell me to do."
         ),
         agentType = "json",
         bodySelections = simulation.buildRandomCharacterBodySelections(
            bodyType = "male",
            hairColor = "black",
            skinColor = "light"
         ),
         age = 25,
         location = Vector2(2000.0, 2000.0)
      )
   }
}
