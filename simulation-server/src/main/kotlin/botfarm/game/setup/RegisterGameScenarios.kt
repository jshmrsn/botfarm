package botfarm.game.setup

import botfarm.engine.simulation.ScenarioRegistration
import botfarm.game.setup.scenarios.*

fun registerGameScenarios() {
   ScenarioRegistration.registerScenario(DefaultNoAgentGameScenario())
   ScenarioRegistration.registerScenario(DefaultGameScenario())
   ScenarioRegistration.registerScenario(SpectateAgentsGameScenario())
   ScenarioRegistration.registerScenario(LegacyAgentGameScenario())
}

