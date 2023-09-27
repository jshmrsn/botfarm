package botfarm.simulationserver.common

open class SpriteAnimationConfig(
   val keySuffix: String? = null,
   val frameRate: Double? = null,
   val duration: Double? = null,
   val repeat: Int = -1,
   val singleFrame: String? = null,
   val framesPrefix: String? = null,
   val framesStart: Int = 0,
   val framesEnd: Int = 1,
   val framesZeroPad: Int = 0
)