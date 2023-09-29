package botfarm.game.setup

import botfarm.engine.simulation.ScenarioRegistration
import botfarm.game.setup.scenarios.DefaultGameScenario

fun registerGameScenarios() {
   ScenarioRegistration.registerScenario(DefaultGameScenario())
}

