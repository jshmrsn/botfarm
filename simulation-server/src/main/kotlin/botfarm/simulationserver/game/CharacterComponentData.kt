package botfarm.simulationserver.game

import botfarm.misc.Vector2
import botfarm.simulationserver.simulation.Config
import botfarm.simulationserver.simulation.EntityComponentData
import botfarm.apidata.EntityId


class CharacterBodySelections(
   val skinColor: String,
   val bodyType: String,
   val head: String?,
   val body: String?,
   val nose: String? = null,
   val eyes: CompositeAnimation? = null,
   val wrinkles: String? = null,
   val hair: CompositeAnimation? = null
)

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

data class CharacterComponentData(
   val name: String,
   val age: Int = 30,
   val recentSpokenMessages: List<SpokenMessage> = listOf(),
   val facialExpressionEmoji: String? = null,
   val pendingInteractionTargetEntityId: EntityId? = null,
   val equippedItemConfigKey: String? = null,
   val bodySelections: CharacterBodySelections
) : EntityComponentData()

data class RegisteredCompositeAnimation(
   val key: String,
   val includedVariants: List<String>
)

class CompositeAnimationRegistryConfig(
   override val key: String,
   val registeredCompositeAnimations: List<RegisteredCompositeAnimation>,
   val includedCategories: List<String> = listOf()
) : Config()

class CompositeAnimation(
   val key: String,
   val variant: String
)

data class SpokenMessage(
   val message: String,
   val sentSimulationTime: Double
) : EntityComponentData()


