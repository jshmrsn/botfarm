package botfarm.simulationserver.game

import botfarm.simulationserver.game.ai.registerCharacterAgentSystem
import botfarm.simulationserver.game.registerPendingInteractionTickSystem

fun registerSystems() {
   registerPendingInteractionTickSystem()
   registerCharacterAgentSystem()
}