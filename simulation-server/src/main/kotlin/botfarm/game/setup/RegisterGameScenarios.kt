package botfarm.game.setup

import botfarm.engine.simulation.ScenarioRegistration
import botfarm.game.setup.scenarios.DefaultGameScenario
import botfarm.game.setup.scenarios.DefaultNoAgentGameScenario
import botfarm.game.setup.scenarios.ScriptedAgentGameScenario
import botfarm.game.setup.scenarios.SpectateAgentsGameScenario

fun registerGameScenarios() {
   ScenarioRegistration.registerScenario(DefaultGameScenario())
   ScenarioRegistration.registerScenario(DefaultNoAgentGameScenario())
   ScenarioRegistration.registerScenario(SpectateAgentsGameScenario())
   ScenarioRegistration.registerScenario(ScriptedAgentGameScenario())
}

