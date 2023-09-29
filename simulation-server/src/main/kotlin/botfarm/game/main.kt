package botfarm.game

import botfarm.engine.startSimulationServer
import botfarm.game.setup.registerGameScenarios
import botfarm.game.setup.registerGameSystems

fun main() {
   registerGameSystems()
   registerGameScenarios()
   startSimulationServer()
}