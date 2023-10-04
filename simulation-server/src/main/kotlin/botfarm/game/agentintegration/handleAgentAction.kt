package botfarm.game.agentintegration

import botfarm.common.PositionComponentData
import botfarm.engine.simulation.Entity
import botfarm.engine.simulation.EntityComponent
import botfarm.game.GameSimulation
import botfarm.game.components.CharacterComponentData
import botfarm.game.components.InventoryComponentData
import botfarmshared.engine.apidata.EntityId
import botfarmshared.game.apidata.*
import botfarmshared.misc.Vector2
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun handleAgentAction(
   action: Action,
   state: AgentSyncState,
   characterComponent: EntityComponent<CharacterComponentData>,
   agentActionUtils: AgentActionUtils,
   simulationTimeForStep: Double,
   currentLocation: Vector2,
   simulation: GameSimulation,
   entity: Entity,
   positionComponent: EntityComponent<PositionComponentData>,
   debugInfo: String
) {
   val actionUniqueId = action.actionUniqueId
   val prettyPrint = Json { prettyPrint = true }

   println("Action started: $actionUniqueId\n${prettyPrint.encodeToString(action)}")
   val speak = action.speak
   state.mutableObservations.startedActionUniqueIds.add(actionUniqueId)

   fun addActionResult() {
      println("Action completed result: $actionUniqueId")
      state.mutableObservations.actionResults.add(
         ActionResult(
            actionUniqueId = actionUniqueId
         )
      )
   }

   val facialExpressionEmoji = action.facialExpressionEmoji
   val locationToWalkToAndReason = action.walk
   val useEquippedToolItemOnEntity = action.useEquippedToolItemOnEntity
   val pickUpEntity = action.pickUpEntity
   val useEquippedToolItem = action.useEquippedToolItem
   val dropInventoryItem = action.dropInventoryItem
   val equipInventoryItem = action.equipInventoryItem
   val craftItem = action.craftItem

   val reason = action.reason

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
      val endPoint = locationToWalkToAndReason.location

      state.mutableObservations.movementRecords.add(
         MovementRecord(
            startedAtTime = simulationTimeForStep,
            startPoint = currentLocation,
            endPoint = endPoint,
            reason = reason
         )
      )

      println("starting move: " + endPoint)
      val movementResult = simulation.moveEntityToPoint(
         entity = entity,
         endPoint = endPoint
      )

      println("movementResult: " + movementResult)

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
   }

   if (craftItem != null) {
      val itemConfigKey = craftItem.itemConfigKey

      val craftItemResult = simulation.craftItem(
         entity = entity,
         itemConfigKey = itemConfigKey
      )

      if (craftItemResult != GameSimulation.CraftItemResult.Success) {
         simulation.broadcastAlertAsGameMessage("Unable to use craft item for agent ($debugInfo): actionUniqueId = $actionUniqueId, result ${craftItemResult.name}")
      }

      state.mutableObservations.craftItemActionRecords.add(
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
         simulation.broadcastAlertAsGameMessage("Unable to use equipped item for agent ($debugInfo): actionUniqueId = $actionUniqueId, result ${result::class.simpleName}")
      } else {
         addActionResult()

         state.mutableObservations.actionOnInventoryItemActionRecords.add(
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

   if (equipInventoryItem != null) {
      val itemConfigKey = equipInventoryItem.itemConfigKey

      addActionResult()

      val equipResult = simulation.equipItem(
         entity = entity,
         expectedItemConfigKey = itemConfigKey,
         requestedStackIndex = equipInventoryItem.stackIndex
      )

      if (equipResult != GameSimulation.EquipItemResult.Success) {
         simulation.broadcastAlertAsGameMessage("Unable to equip item for agent ($debugInfo): actionUniqueId = $actionUniqueId, itemConfigKey = $itemConfigKey, result ${equipResult.name}")
      }

      state.mutableObservations.actionOnInventoryItemActionRecords.add(
         ActionOnInventoryItemRecord(
            startedAtTime = simulationTimeForStep,
            reason = reason,
            itemConfigKey = itemConfigKey,
            amount = 1,
            actionId = "equip"
         )
      )
   }

   if (dropInventoryItem != null) {
      val itemConfigKey = dropInventoryItem.itemConfigKey
      val amount = dropInventoryItem.amount

      val inventory = entity.getComponent<InventoryComponentData>().data.inventory

      val stackIndex = dropInventoryItem.stackIndex
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
         simulation.broadcastAlertAsGameMessage("Unable to find available item stack to drop ($debugInfo): actionUniqueId = $actionUniqueId, itemConfigKey = $itemConfigKey")
      } else {
         val didDrop = simulation.dropItemStack(
            droppingEntity = entity,
            expectedItemConfigKey = itemConfigKey,
            stackIndex = stackIndex,
            amountToDropFromStack = amount
         )

         if (!didDrop) {
            simulation.broadcastAlertAsGameMessage("Unable to drop item stack for agent ($debugInfo): actionUniqueId = $actionUniqueId, stackIndex = $stackIndex, amount = $amount, itemConfigKey = $itemConfigKey")
         }
      }

      state.mutableObservations.actionOnInventoryItemActionRecords.add(
         ActionOnInventoryItemRecord(
            startedAtTime = simulationTimeForStep,
            reason = reason,
            itemConfigKey = itemConfigKey,
            amount = amount,
            actionId = "dropItem"
         )
      )
   }

   fun handleAutoInteractWithEntity(
      targetEntityId: EntityId,
      actionId: String
   ) {
      // jshmrsn: Currently, all actions on entities are executed by the single autoInteractWithEntity
      // The provided actionId is currently just a hint for agents to keep track of their intended interaction
      val targetEntity = simulation.getEntityOrNull(targetEntityId)

      if (targetEntity == null) {
         val destroyedTargetEntity = simulation.getDestroyedEntityOrNull(targetEntityId)
         if (destroyedTargetEntity != null) {
            println("Can't find entity for action from AI (but was destroyed AFTER prompt was generated) ($debugInfo): $actionId, actionUniqueId = $actionUniqueId, targetEntityId = $targetEntityId")
         } else {
            simulation.broadcastAlertAsGameMessage("Can't find entity for action from AI ($debugInfo): $actionId, actionUniqueId = $actionUniqueId, targetEntityId = $targetEntityId")
         }
         addActionResult()
      } else {
         val movementResult = agentActionUtils.autoInteractWithEntity(targetEntity)

         if (movementResult is GameSimulation.MoveToResult.Success) {
            agentActionUtils.waitForMovement(
               positionComponent = positionComponent,
               movementResult = movementResult
            ) {
               state.mutableObservations.actionOnEntityRecords.add(
                  ActionOnEntityRecord(
                     startedAtTime = simulationTimeForStep,
                     targetEntityId = targetEntityId,
                     actionId = actionId,
                     reason = reason
                  )
               )

               addActionResult()
            }
         } else {
            addActionResult()
         }
      }
   }

   if (pickUpEntity != null) {
      handleAutoInteractWithEntity(
         targetEntityId = pickUpEntity.targetEntityId,
         actionId = "pickUpEntity"
      )
   }

   if (useEquippedToolItemOnEntity != null) {
      handleAutoInteractWithEntity(
         targetEntityId = useEquippedToolItemOnEntity.targetEntityId,
         actionId = "useEquippedToolItemOnEntity"
      )
   }
}