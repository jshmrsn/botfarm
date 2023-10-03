package botfarm.game.systems

import botfarm.common.PositionComponentData
import botfarm.engine.simulation.*
import botfarm.game.GameSimulation
import botfarm.game.ai.*
import botfarm.game.components.AgentComponentData
import botfarm.game.components.CharacterComponentData
import botfarm.game.components.InventoryComponentData
import botfarm.game.config.ItemConfig
import botfarmshared.game.apidata.*
import botfarmshared.misc.buildShortRandomString
import kotlinx.coroutines.delay

class AgentState(
   val simulation: GameSimulation
) {
   var previousNewEventCheckTime = -1000.0
   var newObservations = MutableObservations()
   var runningJavaScriptThread: Thread? = null
   var lastNewThreadUnixTimeSeconds = 0.0
}

object AgentConstants {
   val distanceUnit = "centimeters"
}

suspend fun agentCoroutineSystem(
   context: CoroutineSystemContext,
   agentComponent: EntityComponent<AgentComponentData>
) {
   val entity = agentComponent.entity

   val agentId = agentComponent.data.agentId
   val agentType = agentComponent.data.agentType

   val simulation = context.simulation as GameSimulation

   val state = AgentState(simulation)

   // Prevent AI from seeing into the past
   state.previousNewEventCheckTime = simulation.getCurrentSimulationTime()

   while (true) {
      try {
         val syncId = buildShortRandomString()

         sync(
            context = context,
            simulation = simulation,
            entity = entity,
            agentComponent = agentComponent,
            state = state,
            agentId = agentId,
            syncId = syncId
         )
      } catch (exception: Exception) {
         val errorId = buildShortRandomString()
         simulation.broadcastAlertAsGameMessage("Exception in character agent logic (errorId = $errorId)")
         println("Exception in character agent logic (errorId = $errorId):\n${exception.stackTraceToString()}")

         context.synchronize {
            agentComponent.modifyData {
               it.copy(
                  agentIntegrationStatus = "exception",
                  agentError = "Exception in character agent logic (errorId = $errorId)"
               )
            }
         }
      }

      delay(500)
      context.unwindIfNeeded()
   }
}



private suspend fun sync(
   context: CoroutineSystemContext,
   simulation: GameSimulation,
   entity: Entity,
   agentComponent: EntityComponent<AgentComponentData>,
   state: AgentState,
   agentId: AgentId,
   syncId: String
) {
   val remoteAgentIntegration = simulation.agentServerIntegration

   val characterComponent = entity.getComponentOrNull<CharacterComponentData>()

   if (characterComponent == null) {
      return
   }

   val positionComponent = entity.getComponentOrNull<PositionComponentData>()

   if (positionComponent == null) {
      return
   }

   val simulationTimeForStep: Double

   context.unwindIfNeeded()
   synchronized(simulation) {
      val observationDistance = agentComponent.data.observationDistance

      simulationTimeForStep = simulation.getCurrentSimulationTime()
      val currentLocation = positionComponent.data.positionAnimation.resolve(simulationTimeForStep)

      simulation.entities.forEach { otherEntity ->
         val otherCharacterComponentData = otherEntity.getComponentOrNull<CharacterComponentData>()?.data
         val otherPositionComponent = otherEntity.getComponentOrNull<PositionComponentData>()

         if (otherEntity != entity && otherPositionComponent != null) {
            val otherPosition = otherPositionComponent.data.positionAnimation.resolve(simulationTimeForStep)

            val distance = otherPosition.distance(currentLocation)

            if (distance <= observationDistance) {
               state.newObservations.entitiesById[otherEntity.entityId] = buildEntityInfoForAgent(
                  entity = otherEntity,
                  simulationTime = simulationTimeForStep,
                  interactingEntity = entity
               )
            }

            // jshmrsn: Currently not limiting distance that agents can "hear" chat messages
            if (otherCharacterComponentData != null) {
               val newMessages = otherCharacterComponentData.recentSpokenMessages.filter {
//                  val age = simulationTimeForStep - it.sentSimulationTime
                  val tooOldToBeObserved =
                     false // age > 15 jshmrsn: This is dangerous because currently observation logic is run in the coroutine, which can be blocked for a long time waiting for prompts.
                  // Consider running observation in tick
                  it.sentSimulationTime > state.previousNewEventCheckTime && !tooOldToBeObserved
               }

               newMessages.forEach { newMessage ->
                  state.newObservations.spokenMessages.add(
                     ObservedSpokenMessage(
                        entityId = otherEntity.entityId,
                        characterName = otherCharacterComponentData.name,
                        message = newMessage.message,
                        speakerLocation = otherPosition,
                        myLocation = currentLocation,
                        time = newMessage.sentSimulationTime
                     )
                  )
               }
            }
         }
      }

      val activityStream = simulation.getActivityStream()

      val newActivityStreamEntries = activityStream.filter { activityStreamEntry ->
         activityStreamEntry.time > state.previousNewEventCheckTime &&
                 activityStreamEntry.shouldReportToAi
      }

      state.newObservations.activityStreamEntries.addAll(newActivityStreamEntries.map {
         ActivityStreamEntryRecord(
            time = it.time,
            title = it.title,
            message = it.message,
            actionType = it.actionType,
            sourceEntityId = it.sourceEntityId,
            sourceLocation = it.sourceLocation,
            targetEntityId = it.targetEntityId
         )
      })

      state.previousNewEventCheckTime = simulationTimeForStep
   }

   if (simulation.shouldPauseAi) {
      context.synchronize {
         agentComponent.modifyData {
            it.copy(
               agentIntegrationStatus = "paused",
               agentStatus = null,
               agentError = null,
               wasRateLimited = false
            )
         }
      }

      return
   }

   val itemConfigs = simulation.configs
      .mapNotNull { it as? ItemConfig }

   val craftingRecipes = itemConfigs.mapNotNull {
      if (it.craftableConfig != null) {
         CraftingRecipe(
            itemConfigKey = it.key,
            itemName = it.name,
            description = it.description,
            cost = it.craftableConfig.craftingCost,
            amount = it.craftableConfig.craftingAmount
         )
      } else {
         null
      }
   }

   val selfEntityInfo = buildEntityInfoForAgent(
      interactingEntity = null,
      entity = entity,
      simulationTime = simulationTimeForStep
   )

   val inventoryComponentData = entity.getComponent<InventoryComponentData>().data

   val inventoryInfo = InventoryInfo(
      itemStacks = inventoryComponentData.inventory.itemStacks.map {
         val itemConfig = simulation.getConfig<ItemConfig>(it.itemConfigKey)

         ItemStackInfo(
            itemConfigKey = it.itemConfigKey,
            amount = it.amount,
            itemName = itemConfig.name,
            itemDescription = itemConfig.description,
            canBeDropped = itemConfig.storableConfig?.canBeDropped ?: false,
            canBeEquipped = itemConfig.equippableConfig != null,
            isEquipped = it.isEquipped,
            spawnItemOnUseConfigKey = itemConfig.spawnItemOnUseConfig?.spawnItemConfigKey
         )
      }
   )

   val selfInfo = SelfInfo(
      agentId = agentId,
      entityInfo = selfEntityInfo,
      corePersonality = agentComponent.data.corePersonality,
      initialMemories = agentComponent.data.initialMemories,
      inventoryInfo = inventoryInfo,
      observationDistance = agentComponent.data.observationDistance
   )

   val newObservationsForInput = state.newObservations
   val agentSyncInputs = AgentSyncInputs(
      syncId = syncId,
      agentType = agentComponent.data.agentType,
      simulationId = simulation.simulationId,
      simulationTime = simulationTimeForStep,
      craftingRecipes = craftingRecipes,
      selfInfo = selfInfo,
      newObservations = newObservationsForInput.toObservations(),
      distanceUnit = AgentConstants.distanceUnit,
      peopleSize = 40.0
   )

   state.newObservations = MutableObservations()

   context.synchronize {
      agentComponent.modifyData {
         it.copy(
            agentIntegrationStatus = "waiting_for_agent",
            totalRemoteAgentRequests = it.totalRemoteAgentRequests + 1,
            agentError = null
         )
      }
   }

   context.unwindIfNeeded()
   val remoteAgentStepResults = remoteAgentIntegration.sendSyncRequest(agentSyncInputs)
   context.unwindIfNeeded()

   context.synchronize {
      agentComponent.modifyData {
         it.copy(
            agentIntegrationStatus = "idle"
         )
      }

      remoteAgentStepResults.forEach { agentStepResult ->
         try {
            handleAgentStepResult(
               agentStepResult = agentStepResult,
               positionComponent = positionComponent,
               simulationTimeForStep = simulationTimeForStep,
               agentComponent = agentComponent,
               simulation = simulation,
               characterComponent = characterComponent,
               state = state,
               entity = entity,
               syncId = syncId
            )
         } catch (exception: Exception) {
            val errorId = buildShortRandomString()
            println("Exception while handling agent step result (errorId = $errorId, agentId = $agentId, stepId = $syncId): ${exception.stackTraceToString()}")

            context.synchronize {
               agentComponent.modifyData {
                  it.copy(
                     agentError = "Exception while handling agent step result (errorId = $errorId)"
                  )
               }
            }
         }
      }
   }
}
