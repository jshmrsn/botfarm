package botfarm.game.components

import botfarm.engine.simulation.Config
import botfarm.engine.simulation.Entity
import botfarm.engine.simulation.EntityComponentData
import botfarm.game.UseEquippedToolItemRequest
import botfarmshared.engine.apidata.EntityId
import botfarmshared.game.apidata.Action
import botfarmshared.game.apidata.ActionType
import botfarmshared.misc.Vector2


class CharacterBodySelections(
   val skinColor: String,
   val bodyType: String,
   val head: String?,
   val body: String?,
   val nose: String? = null,
   val eyes: CompositeAnimationSelection? = null,
   val wrinkles: String? = null,
   val hair: CompositeAnimationSelection? = null
)


class PerformedAction(
   val performedAtLocation: Vector2,
   val startedAtSimulationTime: Double,
   val actionIndex: Int,
   val actionType: ActionType,
   val targetEntityId: EntityId?,
   val duration: Double
)

data class CharacterComponentData(
   val name: String,
   val age: Int = 30,
   val recentSpokenMessages: List<SpokenMessage> = listOf(),
   val facialExpressionEmoji: String? = null,
   val pendingInteractionTargetEntityId: EntityId? = null,
   val pendingInteractionActionType: ActionType? = null,
   val pendingUseEquippedToolItemRequest: UseEquippedToolItemRequest? = null,
   val bodySelections: CharacterBodySelections,
   val performedAction: PerformedAction? = null,
   val observationRadius: Double
) : EntityComponentData()


val Entity.ongoingAction: PerformedAction?
   get() {
      val performedAction = this.getComponentOrNull<CharacterComponentData>()?.data?.performedAction
         ?: return null

      if (this.simulation.getCurrentSimulationTime() > performedAction.startedAtSimulationTime + performedAction.duration) {
         return null
      }

      return performedAction
   }

val Entity.hasOngoingAction
   get() = this.ongoingAction != null

val Entity.isAvailableToPerformAction
   get() = this.exists && !this.isDead && !this.hasOngoingAction


data class RegisteredCompositeAnimation(
   val key: String,
   val includedVariants: List<String>
)

class CompositeAnimationRegistryConfig(
   override val key: String,
   val registeredCompositeAnimations: List<RegisteredCompositeAnimation>,
   val includedCategories: List<String> = listOf()
) : Config()

class CompositeAnimationSelection(
   val key: String,
   val variant: String
)

data class SpokenMessage(
   val messageId: String,
   val message: String,
   val sentSimulationTime: Double
) : EntityComponentData()


