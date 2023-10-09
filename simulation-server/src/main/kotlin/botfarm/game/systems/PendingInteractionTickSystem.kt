package botfarm.game.systems

import botfarm.common.PositionComponentData
import botfarm.common.resolvePosition
import botfarm.engine.simulation.EntityComponent
import botfarm.engine.simulation.Systems
import botfarm.engine.simulation.TickSystemContext
import botfarm.game.GameSimulation
import botfarm.game.components.*
import botfarm.game.config.EquipmentSlot
import botfarmshared.game.apidata.ActionResultType

fun pendingInteractionTickSystem(
   context: TickSystemContext,
   positionComponent: EntityComponent<PositionComponentData>,
   characterComponent: EntityComponent<CharacterComponentData>
) {
   val simulation = context.simulation as GameSimulation

   val simulationTime = simulation.getCurrentSimulationTime()
   val characterComponentData = characterComponent.data

   val equippedToolItemConfig = characterComponent.entity.getEquippedItemConfig(EquipmentSlot.Tool)

   val entity = context.entity
   val controllingUserId = entity.getComponentOrNull<UserControlledComponentData>()?.data?.userId

   fun sendAlertToControlledClient(message: String) {
      if (controllingUserId != null) {
         simulation.sendAlertMessage(controllingUserId, message)
      }
   }

   if (characterComponentData.pendingUseEquippedToolItemRequest == null &&
      characterComponentData.pendingInteractionTargetEntityId == null
   ) {
      return
   }

   val position = positionComponent.entity.resolvePosition()
   val lastKeyFrame = positionComponent.data.positionAnimation.keyFrames.last()
   val hasFinishedMoving = simulationTime >= lastKeyFrame.time

   if (!hasFinishedMoving) {
      return
   }

   val pendingCallback = simulation.pendingAutoInteractCallbackByEntityId[entity.entityId] ?: { autoInteractType -> }
   simulation.pendingAutoInteractCallbackByEntityId.remove(entity.entityId)

   characterComponent.modifyData {
      it.copy(
         pendingInteractionTargetEntityId = null,
         pendingUseEquippedToolItemRequest = null
      )
   }

   // TODO: Only perform action if it matches characterComponentData.pendingInteractionActionType, otherwise result in ActionResultType.UnexpectedAction

   if (characterComponentData.pendingUseEquippedToolItemRequest != null) {
      val result = simulation.useEquippedToolItem(
         interactingEntity = entity,
         expectedItemConfigKey = characterComponentData.pendingUseEquippedToolItemRequest.expectedItemConfigKey
      )

      when (result) {
         is GameSimulation.UseEquippedItemResult.Success -> pendingCallback(ActionResultType.Success)
         is GameSimulation.UseEquippedItemResult.UnexpectedEquippedItem -> pendingCallback(ActionResultType.UnexpectedEquippedItem)
         is GameSimulation.UseEquippedItemResult.NoActionForEquippedTool -> pendingCallback(ActionResultType.NoActionForEquippedTool)
         is GameSimulation.UseEquippedItemResult.NoToolItemEquipped -> pendingCallback(ActionResultType.NoToolItemEquipped)
         is GameSimulation.UseEquippedItemResult.Busy -> pendingCallback(ActionResultType.Busy)
         is GameSimulation.UseEquippedItemResult.Obstructed -> pendingCallback(ActionResultType.Obstructed)
         else -> pendingCallback(ActionResultType.Failed)
      }
   } else if (characterComponentData.pendingInteractionTargetEntityId != null) {
      val targetEntity =
         context.simulation.getEntityOrNull(characterComponentData.pendingInteractionTargetEntityId)

      if (targetEntity == null) {
         pendingCallback(ActionResultType.TargetNoLongerExists)
      } else if (targetEntity.isDead) {
         pendingCallback(ActionResultType.TargetAlreadyDead)
      } else {
         val itemConfig = targetEntity.itemConfigOrNull

         val targetGrowerComponent = targetEntity.getComponentOrNull<GrowerComponentData>()

         if (itemConfig != null) {
            if (itemConfig.storableConfig != null) {
               val result = simulation.pickUpItem(
                  pickingUpEntity = entity,
                  targetEntity = targetEntity
               )

               when (result) {
                  GameSimulation.PickUpItemResult.Success -> {
                     pendingCallback(ActionResultType.Success)
                  }

                  GameSimulation.PickUpItemResult.TooFar -> {
                     sendAlertToControlledClient("Too far away to pick up item")
                     pendingCallback(ActionResultType.StillTooFarAfterMoving)
                  }
               }
            } else if (itemConfig.damageableConfig?.damageableByEquippedToolItemConfigKey != null &&
               equippedToolItemConfig != null &&
               itemConfig.damageableConfig.damageableByEquippedToolItemConfigKey == equippedToolItemConfig.key
            ) {
               val result = simulation.interactWithEntityUsingEquippedItem(
                  interactingEntity = entity,
                  targetEntity = targetEntity
               )

               when (result) {
                  GameSimulation.InteractWithEntityUsingEquippedItemResult.Success -> pendingCallback(ActionResultType.Success)
                  GameSimulation.InteractWithEntityUsingEquippedItemResult.NoToolItemEquipped -> pendingCallback(ActionResultType.NoToolItemEquipped)
                  GameSimulation.InteractWithEntityUsingEquippedItemResult.TooFar -> pendingCallback(ActionResultType.StillTooFarAfterMoving)
                  GameSimulation.InteractWithEntityUsingEquippedItemResult.TargetEntityIsDead -> pendingCallback(ActionResultType.TargetAlreadyDead)
                  else -> pendingCallback(ActionResultType.Failed)
               }
            } else if (itemConfig.growerConfig != null &&
               equippedToolItemConfig != null &&
               itemConfig.growerConfig.canReceiveGrowableItemConfigKeys.contains(equippedToolItemConfig.key) &&
               targetEntity.getComponentOrNull<GrowerComponentData>() != null &&
               targetGrowerComponent != null &&
               targetGrowerComponent.data.activeGrowth == null
            ) {
               val result = simulation.interactWithEntityUsingEquippedItem(
                  interactingEntity = entity,
                  targetEntity = targetEntity
               )

               when (result) {
                  GameSimulation.InteractWithEntityUsingEquippedItemResult.Success -> pendingCallback(ActionResultType.Success)
                  GameSimulation.InteractWithEntityUsingEquippedItemResult.NoToolItemEquipped -> pendingCallback(ActionResultType.NoToolItemEquipped)
                  GameSimulation.InteractWithEntityUsingEquippedItemResult.TooFar -> pendingCallback(ActionResultType.StillTooFarAfterMoving)
                  GameSimulation.InteractWithEntityUsingEquippedItemResult.TargetEntityIsDead -> pendingCallback(ActionResultType.TargetAlreadyDead)
                  else -> pendingCallback(ActionResultType.Failed)
               }
            } else {
               // No valid action
               pendingCallback(ActionResultType.NoValidAction)
            }
         } else {
            // Target not an item
            pendingCallback(ActionResultType.TargetNotAnItem)
         }
      }
   }
}

fun registerPendingInteractionTickSystem() =
   Systems.default.registerTickSystem2(::pendingInteractionTickSystem)

