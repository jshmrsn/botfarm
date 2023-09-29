package botfarm.game.config

import botfarm.engine.simulation.Config
import botfarm.game.components.RegisteredCompositeAnimation

class CharacterBodySelectionsConfig(
   override val key: String,
   val skinColors: List<String>,
   val bodyTypes: List<String>,
   val bodies: List<String>,
   val heads: List<String>,
   val noses: List<String>,
   val eyes: List<RegisteredCompositeAnimation>,
   val wrinkles: List<String>,
   val hairs: List<RegisteredCompositeAnimation>
) : Config()