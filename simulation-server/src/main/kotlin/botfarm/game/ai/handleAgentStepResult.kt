package botfarm.game.ai

import botfarmshared.misc.Vector2
import botfarm.common.PositionComponentData
import botfarm.engine.simulation.AlertMode
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

   val agentActionUtils = AgentActionUtils(
      entity = entity,
      state = state,
      simulation = simulation,
      debugInfo = debugInfo
   )

   val actions = agentStepResult.actions

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
      simulation.broadcastAlertMessage(AlertMode.ConsoleError, "Error from remote agent: " + agentStepResult.error)

      agentComponent.modifyData {
         it.copy(
            agentError = agentStepResult.error
         )
      }

      return
   }

   if (actions != null) {
      val actionUniqueId = actions.actionUniqueId
      println("Action started: $actionUniqueId")
      val speak = actions.speak
      state.newObservations.startedActionUniqueIds.add(actionUniqueId)

      fun addActionResult() {
         println("Action completed result: $actionUniqueId")
         state.newObservations.actionResults.add(
            ActionResult(
               actionUniqueId = actionUniqueId
            )
         )
      }

      val facialExpressionEmoji = actions.facialExpressionEmoji

      val locationToWalkToAndReason = actions.walk

      val actionOnEntity = actions.actionOnEntity
      val useEquippedToolItem = actions.useEquippedToolItem

      val actionOnInventoryItem = actions.actionOnInventoryItem
      val craftItemAction = actions.craftItemAction

      if (facialExpressionEmoji != null) {
         characterComponent.modifyData {
            it.copy(
               facialExpressionEmoji = facialExpressionEmoji
            )
         }
      }

      if (speak != null) {
         agentActionUtils.speak(speak)
         addActionResult()
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

            val movementResult = simulation.moveEntityToPoint(
               entity = entity,
               endPoint = endPoint
            )

            if (movementResult is GameSimulation.MoveToResult.Success) {
               agentActionUtils.waitForMovement(
                  positionComponent = positionComponent,
                  movementResult = movementResult
               ) {
                  addActionResult()
               }
            } else {
               addActionResult()
            }
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

         addActionResult()
      }

      if (useEquippedToolItem != null) {
         val result = simulation.useEquippedToolItem(
            interactingEntity = entity,
            expectedItemConfigKey = null
         )

         if (result !is GameSimulation.UseEquippedItemResult.Success) {
            addActionResult()
            simulation.broadcastAlertAsGameMessage("Unable to use equipped item for agent ($debugInfo): result ${result::class.simpleName}")
         } else {
            addActionResult()

            state.newObservations.actionOnInventoryItemActionRecords.add(
               ActionOnInventoryItemRecord(
                  startedAtTime = simulationTimeForStep,
                  reason = useEquippedToolItem.reason,
                  itemConfigKey = result.equippedToolItemConfig.key,
                  amount = 1,
                  actionId = "equip"
               )
            )
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
               addActionResult()

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


               addActionResult()

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
               addActionResult()
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

         val targetEntity = simulation.getEntityOrNull(targetEntityId)

         if (targetEntity == null) {
            val destroyedTargetEntity = simulation.getDestroyedEntityOrNull(targetEntityId)
            if (destroyedTargetEntity != null) {
//               val destroyedAtTime = destroyedTargetEntity.destroyedAtTime ?: 0.0
               println("Can't find entity for action from AI (but was destroyed AFTER prompt was generated) ($debugInfo): $actionIdKey, targetEntityId = $targetEntityId")
               // jshmrsn: This check would require more careful tracking of simulation time of most recent received observed entities at the time the prompt was generated
//               if (destroyedAtTime > simulationTimeForStep) {
//
//               } else {
//                  simulation.broadcastAlertAsGameMessage("Can't find entity for action from AI (but was destroyed BEFORE prompt was generated) ($debugInfo): $actionIdKey, targetEntityId = $targetEntityId")
//               }
            } else {
               simulation.broadcastAlertAsGameMessage("Can't find entity for action from AI ($debugInfo): $actionIdKey, targetEntityId = $targetEntityId")
            }
            addActionResult()
         } else if (actionIdKey == "pickupItem" ||
            actionIdKey == "harvestItem" ||
            actionIdKey == "plantItem" ||
            actionIdKey == "interact"
         ) {
            val movementResult = agentActionUtils.interactWithEntity(targetEntity)

            if (movementResult is GameSimulation.MoveToResult.Success) {
               agentActionUtils.waitForMovement(
                  positionComponent = positionComponent,
                  movementResult = movementResult
               ) {
                  state.newObservations.actionOnEntityRecords.add(
                     ActionOnEntityRecord(
                        startedAtTime = simulationTimeForStep,
                        targetEntityId = targetEntityId,
                        actionId = actionIdKey,
                        reason = reason
                     )
                  )

                  addActionResult()
               }
            } else {
               addActionResult()
            }
         } else {
            simulation.broadcastAlertAsGameMessage("Unhandled action id from AI ($debugInfo): $actionIdKey")
            addActionResult()
         }
      }
   }
}