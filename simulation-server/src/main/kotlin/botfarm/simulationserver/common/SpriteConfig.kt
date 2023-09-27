package botfarm.simulationserver.common

import botfarm.misc.Vector2
import botfarm.simulationserver.simulation.Config

class SpriteConfig(
   override val key: String,
   val baseScale: Vector2 = Vector2.one,
   val baseOffset: Vector2 = Vector2.zero,
   val textureUrl: String,
   val atlasUrl: String? = null,
   val animations: List<SpriteAnimationConfig> = listOf(),
   val animationsUrl: String = ""
) : Config()

