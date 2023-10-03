package botfarm.game.setup.scenarios

import botfarm.engine.simulation.SpawnPlayersMode
import botfarm.game.GameSimulation
import botfarm.game.setup.GameScenario
import botfarmshared.misc.Vector2

class DefaultGameScenario : GameScenario(
   identifier = "default",
   name = "Single Agent (Script-Enhanced GPT-4)"
) {
   override fun configureGameSimulation(simulation: GameSimulation) {
      spawnCommonEnvironmentEntities(simulation)

      simulation.spawnAgent(
         name = "Agent Joe",
         corePersonality = "Friendly. Enjoys conversation. Enjoys walking around randomly.",
         initialMemories = listOf(
            "I want to build a new house, but I shouldn't bother people about it unless it seems relevant.",
            "I should be nice to new people in case we can become friends, but if they mistreat me I should stop doing what they tell me to do."
         ),
         agentType = "scripted-gpt4",
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

class SpectateAgentsGameScenario : GameScenario(
   identifier = "spectate-agents",
   name = "Spectate Two Agents (Script-Enhanced GPT-4)",
   spawnPlayersMode = SpawnPlayersMode.None
) {
   override fun configureGameSimulation(simulation: GameSimulation) {
      spawnCommonEnvironmentEntities(simulation)

      simulation.spawnAgent(
         name = "Joe",
         corePersonality = "Friendly. Enjoys conversation. Enjoys walking around randomly.",
         initialMemories = listOf(
            "I want to build a new house, but I shouldn't bother people about it unless it seems relevant.",
            "I should be nice to new people in case we can become friends, but if they mistreat me I should stop doing what they tell me to do."
         ),
         agentType = "scripted-gpt4",
         bodySelections = simulation.buildRandomCharacterBodySelections(
            bodyType = "male",
            hairColor = "black",
            skinColor = "light"
         ),
         age = 25,
         location = Vector2(2000.0, 2000.0)
      )

      simulation.spawnAgent(
         name = "Linda",
         corePersonality = "Friendly. Enjoys conversation. Enjoys walking around randomly.",
         initialMemories = listOf(
            "I want to build a new house, but I shouldn't bother people about it unless it seems relevant.",
            "I should be nice to new people in case we can become friends, but if they mistreat me I should stop doing what they tell me to do."
         ),
         agentType = "scripted-gpt4",
         bodySelections = simulation.buildRandomCharacterBodySelections(
            bodyType = "female",
            hairColor = "black",
            skinColor = "light"
         ),
         age = 25,
         location = Vector2(2000.0, 2200.0)
      )
   }
}

class LegacyAgentGameScenario : GameScenario(
   identifier = "legacy",
   name = "Legacy Agent (GPT-4)"
) {
   override fun configureGameSimulation(simulation: GameSimulation) {
      spawnCommonEnvironmentEntities(simulation)

      simulation.spawnAgent(
         name = "Agent Joe",
         corePersonality = "Friendly. Enjoys conversation. Enjoys walking around randomly.",
         initialMemories = listOf(
            "I want to build a new house, but I shouldn't bother people about it unless it seems relevant.",
            "I should be nice to new people in case we can become friends, but if they mistreat me I should stop doing what they tell me to do."
         ),
         agentType = "scripted-gpt4",
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
