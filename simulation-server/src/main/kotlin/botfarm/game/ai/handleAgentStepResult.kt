package botfarm.game.ai

import botfarmshared.misc.Vector2
import botfarm.common.PositionComponentData
import botfarm.game.components.AgentComponentData
import botfarm.game.components.CharacterComponentData
import botfarm.game.GameSimulation
import botfarm.game.components.InventoryComponentData
import botfarmshared.game.apidata.*
import botfarm.engine.simulation.Entity
import botfarm.engine.simulation.EntityComponent
import botfarm.game.systems.AgentState

fun handleAgentStepResult(
   agentStepResult: AgentStepResult,
   positionComponent: EntityComponent<PositionComponentData>,
   simulationTimeForStep: Double,
   agentComponent: EntityComponent<AgentComponentData>,
   simulation: GameSimulation,
   characterComponent: EntityComponent<CharacterComponentData>,
   state: AgentState,
   entity: Entity,
   syncId: String
) {
   val agentType = agentComponent.data.agentType
   val debugInfo = "$agentType, syncId = $syncId"

   val agentApi = AgentApi(
      entity = entity,
      state = state,
      simulation = simulation,
      debugInfo = debugInfo
   )

   val interactions = agentStepResult.interactions

   val currentLocation = positionComponent.data.positionAnimation.resolve(simulationTimeForStep)



   agentComponent.modifyData {
      it.copy(
         agentRemoteDebugInfo = agentStepResult.newDebugInfo ?: it.agentRemoteDebugInfo,
         agentStatus = agentStepResult.agentStatus ?: it.agentStatus,
         wasRateLimited = agentStepResult.wasRateLimited,
         statusDuration = agentStepResult.statusDuration,
         statusStartUnixTime = agentStepResult.statusStartUnixTime
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
      val speak = interactions.speak

      val facialExpressionEmoji = interactions.facialExpressionEmoji

      val locationToWalkToAndReason = interactions.locationToWalkToAndReason

      val actionOnEntity = interactions.actionOnEntity
      val useEquippedToolItem = interactions.useEquippedToolItem

      val actionOnInventoryItem = interactions.actionOnInventoryItem
      val craftItemAction = interactions.craftItemAction


      if (facialExpressionEmoji != null) {
         characterComponent.modifyData {
            it.copy(
               facialExpressionEmoji = facialExpressionEmoji
            )
         }
      }

      if (speak != null) {
         agentApi.speak(speak)
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

      if (useEquippedToolItem != null) {
         val result = simulation.useEquippedToolItem(
            interactingEntity = entity,
            expectedItemConfigKey = null
         )

         if (result != GameSimulation.UseEquippedItemResult.Success) {
            simulation.broadcastAlertAsGameMessage("Unable to use equipped item for agent ($debugInfo): result ${result.name}")
         }
      }

      if (actionOnInventoryItem != null) {
         val reason = actionOnInventoryItem.reason
         val itemConfigKey = actionOnInventoryItem.itemConfigKey
         val actionId = actionOnInventoryItem.actionId
         val amount = actionOnInventoryItem.amount

         val inventory = entity.getComponent<InventoryComponentData>().data.inventory

         when (actionId) {
            "equipItem" -> {
               val equipResult = simulation.equipItem(
                  entity = entity,
                  expectedItemConfigKey = itemConfigKey,
                  requestedStackIndex = actionOnInventoryItem.stackIndex
               )

               if (equipResult != GameSimulation.EquipItemResult.Success) {
                  simulation.broadcastAlertAsGameMessage("Unable to equip item for agent ($debugInfo): itemConfigKey = $itemConfigKey, result ${equipResult.name}")
               }
            }

            "dropItem" -> {
               val stackIndex = actionOnInventoryItem.stackIndex
                  ?: inventory.itemStacks
                     .mapIndexed { index, it -> index to it }
                     .filter {
                        it.second.itemConfigKey == itemConfigKey
                     }
                     .sortedWith { a, b ->
                        val isEquippedA = a.second.isEquipped
                        val isEquippedB = b.second.isEquipped

                        if (isEquippedA != isEquippedB) {
                           // prioritize not equipped first
                           if (isEquippedA) {
                              -1
                           } else {
                              1
                           }
                        } else {
                           // prioritize smallest stack first
                           b.second.amount.compareTo(a.second.amount)
                        }
                     }
                     .firstOrNull()?.first


               if (stackIndex == null) {
                  simulation.broadcastAlertAsGameMessage("Unable to find available item stack to drop ($debugInfo): actionId = $actionId, itemConfigKey = $itemConfigKey")
               } else {
                  val didDrop = simulation.dropItemStack(
                     droppingEntity = entity,
                     expectedItemConfigKey = itemConfigKey,
                     stackIndex = stackIndex,
                     amountToDropFromStack = amount
                  )

                  if (!didDrop) {
                     simulation.broadcastAlertAsGameMessage("Unable to drop item stack for agent ($debugInfo): actionId = $actionId, stackIndex = $stackIndex, amount = $amount, itemConfigKey = $itemConfigKey")
                  }
               }
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
            actionIdKey == "harvestItem" ||
            actionIdKey == "plantItem"
         ) {
            agentApi.interactWithEntity(targetEntity)
         } else {
            simulation.broadcastAlertAsGameMessage("Unhandled action id from AI ($debugInfo): $actionIdKey")
         }
      }
   }
}