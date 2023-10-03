package botfarm.game.setup

import botfarm.engine.simulation.ScenarioRegistration
import botfarm.game.setup.scenarios.*

fun registerGameScenarios() {
   ScenarioRegistration.registerScenario(DefaultGameScenario())
   ScenarioRegistration.registerScenario(DefaultNoAgentGameScenario())
   ScenarioRegistration.registerScenario(SpectateAgentsGameScenario())
   ScenarioRegistration.registerScenario(ScriptedAgentGameScenario())
   ScenarioRegistration.registerScenario(ScriptedAgentGameScenario_gpt35())
}

