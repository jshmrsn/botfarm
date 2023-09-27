package botfarm.simulationserver.game

import botfarm.simulationserver.common.PositionComponentData
import botfarm.simulationserver.common.resolvePosition
import botfarm.simulationserver.simulation.Systems

fun registerPendingInteractionTickSystem() =
   Systems.default.registerTickSystem2<PositionComponentData, CharacterComponentData> { context, positionComponent, characterComponent ->
      val simulation = context.simulation as GameSimulation

      val simulationTime = simulation.getCurrentSimulationTime()
      val characterComponentData = characterComponent.data

      val equippedItemId = characterComponentData.equippedItemConfigKey
      val equippedItemConfig = equippedItemId?.let { simulation.getConfig<ItemConfig>(it) }

      val controllingUserId = context.entity.getComponentOrNull<UserControlledComponentData>()?.data?.userId
      val controllingClients = controllingUserId?.let { context.simulation.getClientsForUserId(it) } ?: listOf()

      if (characterComponentData.pendingInteractionTargetEntityId != null) {
         fun sendAlertToControlledClient(message: String) {
            for (controllingClient in controllingClients) {
               simulation.sendAlertMessage(controllingClient, message)
            }
         }

         val lastKeyFrame = positionComponent.data.positionAnimation.keyFrames.last()

         if (simulationTime >= lastKeyFrame.time) {
            characterComponent.modifyData {
               it.copy(
                  pendingInteractionTargetEntityId = null
               )
            }

            val position = lastKeyFrame.value
            val targetEntity =
               context.simulation.getEntityOrNull(characterComponentData.pendingInteractionTargetEntityId)

            if (targetEntity != null) {
               val targetPosition = targetEntity.resolvePosition()

               val distance = targetPosition.distance(position)

               if (distance <= 30.0) {
                  val targetItemComponent = targetEntity.getComponentOrNull<ItemComponentData>()

                  if (targetItemComponent != null) {
                     val itemConfig = simulation.getConfig<ItemConfig>(targetItemComponent.data.itemConfigKey)

                     if (itemConfig.canBePickedUp) {
                        val result = simulation.pickUpItem(
                           pickingUpEntity = context.entity,
                           targetEntity = targetEntity
                        )

                        when (result) {
                           GameSimulation.PickUpItemResult.Success -> {}
                           GameSimulation.PickUpItemResult.TooFar -> sendAlertToControlledClient("Too far away to pick up item")
                           else -> sendAlertToControlledClient("Can't pick up item: " + result.name)
                        }
                     } else if (itemConfig.canBeDamagedByItem != null &&
                        equippedItemConfig != null &&
                        itemConfig.canBeDamagedByItem == equippedItemConfig.key
                     ) {
                        val result = simulation.interactWithEntityUsingEquippedItem(
                           interactingEntity = context.entity,
                           targetEntity = targetEntity
                        )

                        when (result) {
                           GameSimulation.InteractWithEntityUsingEquippedItemResult.Success -> {}
                           GameSimulation.InteractWithEntityUsingEquippedItemResult.TooFar -> sendAlertToControlledClient("Too far away to interact with item")
                           else -> sendAlertToControlledClient("Can't interact with item: " + result.name)
                        }
                     }
                  }
               }
            }
         }
      }
   }
