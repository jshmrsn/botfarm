package botfarm.game.setup.scenarios

import botfarm.game.GameSimulation
import botfarm.game.config.RandomItemQuantity
import botfarm.game.setup.GameScenario
import botfarmshared.misc.Vector2

class DefaultNoAgentGameScenario : GameScenario(
   identifier = "default-no-agent",
   name = "Default (no agent)"
) {
   override fun configureGameSimulation(simulation: GameSimulation) {
      spawnCommonEnvironmentEntities(simulation)
   }
}