package botfarm.game.setup

import botfarm.engine.simulation.ScenarioRegistration
import botfarm.game.setup.scenarios.DefaultGameScenario
import botfarm.game.setup.scenarios.DefaultNoAgentGameScenario

fun registerGameScenarios() {
   ScenarioRegistration.registerScenario(DefaultGameScenario())
   ScenarioRegistration.registerScenario(DefaultNoAgentGameScenario())
}

