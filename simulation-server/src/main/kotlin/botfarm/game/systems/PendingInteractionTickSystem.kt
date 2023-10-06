package botfarm.game.systems

import botfarm.common.PositionComponentData
import botfarm.common.resolvePosition
import botfarm.engine.simulation.EntityComponent
import botfarm.engine.simulation.Systems
import botfarm.engine.simulation.TickSystemContext
import botfarm.game.GameSimulation
import botfarm.game.components.UserControlledComponentData
import botfarm.game.components.*
import botfarm.game.config.EquipmentSlot
import botfarm.game.config.ItemConfig
import botfarmshared.game.apidata.AutoInteractType

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

   if (characterComponentData.pendingUseEquippedToolItemRequest != null) {
      val result = simulation.useEquippedToolItem(
         interactingEntity = entity,
         expectedItemConfigKey = characterComponentData.pendingUseEquippedToolItemRequest.expectedItemConfigKey
      )

      if (result !is GameSimulation.UseEquippedItemResult.Success) {
         pendingCallback(AutoInteractType.Failed)
      } else {
         pendingCallback(AutoInteractType.UseEquippedTool)
      }
   } else if (characterComponentData.pendingInteractionTargetEntityId != null) {
      val targetEntity =
         context.simulation.getEntityOrNull(characterComponentData.pendingInteractionTargetEntityId)

      if (targetEntity == null) {
         pendingCallback(AutoInteractType.TargetNoLongerExists)
      } else if (targetEntity.isDead) {
         pendingCallback(AutoInteractType.TargetAlreadyDead)
      } else {
         val targetPosition = targetEntity.resolvePosition()

         val distance = targetPosition.distance(position)

         if (distance <= 30.0) {
            val targetItemComponent = targetEntity.getComponentOrNull<ItemComponentData>()
            val targetGrowerComponent = targetEntity.getComponentOrNull<GrowerComponentData>()

            if (targetItemComponent != null) {
               val itemConfig = simulation.getConfig<ItemConfig>(targetItemComponent.data.itemConfigKey)

               if (itemConfig.storableConfig != null) {
                  val result = simulation.pickUpItem(
                     pickingUpEntity = entity,
                     targetEntity = targetEntity
                  )

                  when (result) {
                     GameSimulation.PickUpItemResult.Success -> {
                        pendingCallback(AutoInteractType.PickUp)
                     }
                     GameSimulation.PickUpItemResult.TooFar -> {
                        sendAlertToControlledClient("Too far away to pick up item")
                        pendingCallback(AutoInteractType.Failed)
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
                     GameSimulation.InteractWithEntityUsingEquippedItemResult.Success -> {
                        pendingCallback(AutoInteractType.AttackWithEquippedTool)
                     }
                     else -> {
                        sendAlertToControlledClient("Can't damage this item: " + result.name)
                        pendingCallback(AutoInteractType.Failed)
                     }
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
                     GameSimulation.InteractWithEntityUsingEquippedItemResult.Success -> {
                        pendingCallback(AutoInteractType.PlacedGrowableInGrower)
                     }
                     else -> {
                        sendAlertToControlledClient("Can't place growable in grower: " + result.name)
                        pendingCallback(AutoInteractType.Failed)
                     }
                  }
               }
            }
         } else {
            pendingCallback(AutoInteractType.StillTooFarAfterMoving)
         }
      }
   }
}

fun registerPendingInteractionTickSystem() =
   Systems.default.registerTickSystem2(::pendingInteractionTickSystem)

