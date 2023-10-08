package botfarm.game.config

import botfarm.engine.simulation.Config
import botfarmshared.misc.Vector2

class GameSimulationConfig(
   override val key: String = Companion.defaultKey,
   val worldBounds: Vector2
) : Config() {
   companion object {
      val defaultKey: String = "game-simulation-config"
   }
}
