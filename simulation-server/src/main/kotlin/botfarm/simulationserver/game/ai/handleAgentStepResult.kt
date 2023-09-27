package botfarm.simulationserver.game.ai

import botfarm.apidata.*
import botfarm.misc.Vector2
import botfarm.simulationserver.common.PositionComponentData
import botfarm.simulationserver.common.resolvePosition
import botfarm.simulationserver.game.AgentComponentData
import botfarm.simulationserver.game.CharacterComponentData
import botfarm.simulationserver.game.GameSimulation
import botfarm.simulationserver.simulation.Entity
import botfarm.simulationserver.simulation.EntityComponent

fun handleAgentStepResult(
   agentStepResult: AgentStepResult,
   positionComponent: EntityComponent<PositionComponentData>,
   simulationTimeForStep: Double,
   agentComponent: EntityComponent<AgentComponentData>,
   simulation: GameSimulation,
   characterComponent: EntityComponent<CharacterComponentData>,
   state: AgentState,
   entity: Entity,
   stepId: String
) {
   val interactions = agentStepResult.interactions

   val currentLocation = positionComponent.data.positionAnimation.resolve(simulationTimeForStep)

   val agentType = agentComponent.data.agentType

   val debugInfo = "$agentType, stepId = $stepId"

   agentComponent.modifyData {
      it.copy(
         agentRemoteDebugInfo = agentStepResult.newDebugInfo ?: it.agentRemoteDebugInfo,
         agentStatus = agentStepResult.agentStatus ?: it.agentStatus,
         wasRateLimited = agentStepResult.wasRateLimited
      )
   }

   agentStepResult.promptUsages.forEach {
      updateComponentModelUsage(it, agentComponent)
   }

   if (agentStepResult.error != null) {
      simulation.broadcastAlertAsGameMessage("Error from remote agent: " + agentStepResult.error)

      agentComponent.modifyData {
         it.copy(
            agentError = agentStepResult.error
         )
      }

      return
   }

   if (interactions != null) {
      val whatToSay = interactions.iWantToSay

      val facialExpressionEmoji = interactions.facialExpressionEmoji

      val locationToWalkToAndReason = interactions.locationToWalkToAndReason

      val actionOnEntity = interactions.actionOnEntity

      val actionOnInventoryItem = interactions.actionOnInventoryItem
      val craftItemAction = interactions.craftItemAction


      if (facialExpressionEmoji != null) {
         characterComponent.modifyData {
            it.copy(
               facialExpressionEmoji = facialExpressionEmoji
            )
         }
      }

      if (whatToSay != null) {
         state.newObservations.selfSpokenMessages.add(
            SelfSpokenMessage(
               message = whatToSay,
               reason = "",
               location = currentLocation,
               time = simulationTimeForStep
            )
         )

         simulation.addCharacterMessage(
            entity = entity,
            message = whatToSay
         )
      }

      if (locationToWalkToAndReason != null) {
         val locationToWalkTo = locationToWalkToAndReason.location

         val reason = locationToWalkToAndReason.reason

         if (locationToWalkTo.size == 2) {
            val endPoint = Vector2(locationToWalkTo.first(), locationToWalkTo.last())

            state.newObservations.movementRecords.add(
               MovementRecord(
                  startedAtTime = simulationTimeForStep,
                  startPoint = currentLocation,
                  endPoint = endPoint,
                  reason = reason
               )
            )

            simulation.moveEntityToPoint(
               entity = entity,
               endPoint = endPoint
            )
         } else {
            throw Exception("Unexpected location size for locationToWalkToAndReason: ${locationToWalkTo.size}")
         }
      }

      if (craftItemAction != null) {
         val reason = craftItemAction.reason
         val itemConfigKey = craftItemAction.itemConfigKey

         simulation.craftItem(
            entity = entity,
            itemConfigKey = itemConfigKey
         )

         state.newObservations.craftItemActionRecords.add(
            CraftItemActionRecord(
               startedAtTime = simulationTimeForStep,
               reason = reason,
               itemConfigKey = itemConfigKey
            )
         )
      }

      if (actionOnInventoryItem != null) {
         val reason = actionOnInventoryItem.reason
         val itemConfigKey = actionOnInventoryItem.itemConfigKey
         val actionId = actionOnInventoryItem.actionId
         val amount = actionOnInventoryItem.amount

         when (actionId) {
            "equipItem" -> {
               simulation.equipItem(
                  entity = entity,
                  itemConfigKey = itemConfigKey
               )
            }

            "dropItem" -> {
               simulation.dropItem(
                  droppingEntity = entity,
                  itemConfigKey = itemConfigKey,
                  amount = amount
               )
            }

            else -> {
               simulation.broadcastAlertAsGameMessage("Unexpected action on inventory item ($debugInfo): actionId = $actionId, itemConfigKey = $itemConfigKey")
            }
         }

         state.newObservations.actionOnInventoryItemActionRecords.add(
            ActionOnInventoryItemRecord(
               startedAtTime = simulationTimeForStep,
               reason = reason,
               itemConfigKey = itemConfigKey,
               amount = amount,
               actionId = actionId
            )
         )
      }

      if (actionOnEntity != null) {
         val actionIdKey = actionOnEntity.actionId

         val targetEntityId = actionOnEntity.targetEntityId

         val reason = actionOnEntity.reason

         state.newObservations.actionOnEntityRecords.add(
            ActionOnEntityRecord(
               startedAtTime = simulationTimeForStep,
               targetEntityId = targetEntityId,
               actionId = actionIdKey,
               reason = reason
            )
         )

         val targetEntity = simulation.getEntityOrNull(targetEntityId)

         if (targetEntity == null) {
            val destroyedTargetEntity = simulation.getDestroyedEntityOrNull(targetEntityId)
            if (destroyedTargetEntity != null) {
               val destroyedAtTime = destroyedTargetEntity.destroyedAtTime ?: 0.0

               if (destroyedAtTime > simulationTimeForStep) {
                  simulation.broadcastAlertAsGameMessage("Can't find entity for action from AI (but was destroyed AFTER prompt was generated) ($debugInfo): $actionIdKey, targetEntityId = $targetEntityId")
               } else {
                  simulation.broadcastAlertAsGameMessage("Can't find entity for action from AI (but was destroyed BEFORE prompt was generated) ($debugInfo): $actionIdKey, targetEntityId = $targetEntityId")
               }
            } else {
               simulation.broadcastAlertAsGameMessage("Can't find entity for action from AI ($debugInfo): $actionIdKey, targetEntityId = $targetEntityId")
            }
         } else if (actionIdKey == "pickupItem" ||
            actionIdKey == "harvestItem"
         ) {
            simulation.moveEntityToPoint(
               entity = entity,
               endPoint = targetEntity.resolvePosition()
            )

            characterComponent.modifyData {
               it.copy(
                  pendingInteractionTargetEntityId = targetEntityId
               )
            }
         } else {
            simulation.broadcastAlertAsGameMessage("Unhandled action id from AI ($debugInfo): $actionIdKey")
         }
      }
   }
}