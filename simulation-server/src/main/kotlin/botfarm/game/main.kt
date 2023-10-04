package botfarm.game

import botfarm.engine.startSimulationServer
import botfarm.game.setup.registerGameScenarios

fun main() {
   registerGameScenarios()
   startSimulationServer()
}