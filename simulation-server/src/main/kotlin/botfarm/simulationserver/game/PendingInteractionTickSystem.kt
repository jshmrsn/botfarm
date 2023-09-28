package botfarm.simulationserver.game

import botfarm.simulationserver.common.PositionComponentData
import botfarm.simulationserver.common.resolvePosition
import botfarm.simulationserver.simulation.EntityComponent
import botfarm.simulationserver.simulation.Systems
import botfarm.simulationserver.simulation.TickSystemContext

fun pendingInteractionTickSystem(
   context: TickSystemContext,
   positionComponent: EntityComponent<PositionComponentData>,
   characterComponent: EntityComponent<CharacterComponentData>
) {
   val simulation = context.simulation as GameSimulation

   val simulationTime = simulation.getCurrentSimulationTime()
   val characterComponentData = characterComponent.data

   val equippedToolItemConfig = characterComponent.entity.getEquippedItemConfig(EquipmentSlot.Tool)

   val controllingUserId = context.entity.getComponentOrNull<UserControlledComponentData>()?.data?.userId

   fun sendAlertToControlledClient(message: String) {
      if (controllingUserId != null) {
         simulation.sendAlertMessage(controllingUserId, message)
      }
   }


   val position = positionComponent.entity.resolvePosition()
   val lastKeyFrame = positionComponent.data.positionAnimation.keyFrames.last()
   val hasFinishedMoving = simulationTime >= lastKeyFrame.time

   if (!hasFinishedMoving) {
      return
   }

   if (characterComponentData.pendingUseEquippedToolItemRequest == null &&
      characterComponentData.pendingInteractionTargetEntityId == null) {
      return
   }

   characterComponent.modifyData {
      it.copy(
         pendingInteractionTargetEntityId = null,
         pendingUseEquippedToolItemRequest = null
      )
   }

   if (characterComponentData.pendingUseEquippedToolItemRequest != null) {
      simulation.useEquippedToolItem(
         interactingEntity = context.entity,
         expectedItemConfigKey =  characterComponentData.pendingUseEquippedToolItemRequest.expectedItemConfigKey
      )
   } else if (characterComponentData.pendingInteractionTargetEntityId != null) {
      val targetEntity =
         context.simulation.getEntityOrNull(characterComponentData.pendingInteractionTargetEntityId)

      if (targetEntity != null && !targetEntity.isDead) {
         characterComponent.modifyData {
            it.copy(
               pendingInteractionTargetEntityId = null
            )
         }

         val targetPosition = targetEntity.resolvePosition()

         val distance = targetPosition.distance(position)

         if (distance <= 30.0) {
            val targetItemComponent = targetEntity.getComponentOrNull<ItemComponentData>()
            val targetGrowerComponent = targetEntity.getComponentOrNull<GrowerComponentData>()

            if (targetItemComponent != null) {
               val itemConfig = simulation.getConfig<ItemConfig>(targetItemComponent.data.itemConfigKey)

               if (itemConfig.storableConfig != null) {
                  val result = simulation.pickUpItem(
                     pickingUpEntity = context.entity,
                     targetEntity = targetEntity
                  )

                  when (result) {
                     GameSimulation.PickUpItemResult.Success -> {}
                     GameSimulation.PickUpItemResult.TooFar -> sendAlertToControlledClient("Too far away to pick up item")
                  }
               } else if (itemConfig.killableConfig?.canBeDamagedByToolItemConfigKey != null &&
                  equippedToolItemConfig != null &&
                  itemConfig.killableConfig.canBeDamagedByToolItemConfigKey == equippedToolItemConfig.key
               ) {
                  val result = simulation.interactWithEntityUsingEquippedItem(
                     interactingEntity = context.entity,
                     targetEntity = targetEntity
                  )

                  when (result) {
                     GameSimulation.InteractWithEntityUsingEquippedItemResult.Success -> {}
                     else -> sendAlertToControlledClient("Can't damage this item: " + result.name)
                  }
               }
               else if (itemConfig.growerConfig != null &&
                  equippedToolItemConfig != null &&
                  itemConfig.growerConfig.canReceiveGrowableItemConfigKeys.contains(equippedToolItemConfig.key) &&
                  targetEntity.getComponentOrNull<GrowerComponentData>() != null &&
                  targetGrowerComponent != null &&
                  targetGrowerComponent.data.activeGrowth == null
               ) {
                  val result = simulation.interactWithEntityUsingEquippedItem(
                     interactingEntity = context.entity,
                     targetEntity = targetEntity
                  )

                  when (result) {
                     GameSimulation.InteractWithEntityUsingEquippedItemResult.Success -> {}

                     else -> sendAlertToControlledClient("Can't place growable in grower: " + result.name)
                  }
               }
            }
         }
      }
   }
}

fun registerPendingInteractionTickSystem() =
   Systems.default.registerTickSystem2(::pendingInteractionTickSystem)

