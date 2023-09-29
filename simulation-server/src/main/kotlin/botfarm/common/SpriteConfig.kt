package botfarm.common

import botfarmshared.misc.Vector2
import botfarm.engine.simulation.Config

class SpriteConfig(
   override val key: String,
   val baseScale: Vector2 = Vector2.one,
   val baseOffset: Vector2 = Vector2.zero,
   val textureUrl: String,
   val atlasUrl: String? = null,
   val animations: List<SpriteAnimationConfig> = listOf(),
   val animationsUrl: String = "",
   val depthOffset: Double = 0.0
) : Config()

