package botfarm.game.setup

import botfarm.engine.simulation.ScenarioRegistration
import botfarm.game.setup.scenarios.*

fun registerGameScenarios(scenarioRegistration: ScenarioRegistration) {
   scenarioRegistration.registerScenario(DefaultNoAgentGameScenario())
   scenarioRegistration.registerScenario(DefaultGameScenario())
   scenarioRegistration.registerScenario(SpectateAgentsGameScenario())
   scenarioRegistration.registerScenario(LegacyAgentGameScenario())

   scenarioRegistration.registerScenario(mockPromptResponsesTestScenario)
   scenarioRegistration.registerScenario(mockAgentTestScenario)
}

