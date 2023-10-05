package botfarm.game

import botfarm.engine.configureSimulationServerModule
import botfarm.engine.simulation.ScenarioRegistration
import botfarm.engine.startSimulationServer
import botfarm.game.setup.registerGameScenarios

fun main() {
   startSimulationServer { application ->
      val scenarioRegistration = ScenarioRegistration()
      registerGameScenarios(scenarioRegistration)

      configureSimulationServerModule(
         application = application,
         scenarioRegistration = scenarioRegistration
      )
   }
}