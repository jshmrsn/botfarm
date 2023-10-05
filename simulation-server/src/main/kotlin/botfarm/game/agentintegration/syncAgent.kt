package botfarm.game.agentintegration

import botfarm.engine.simulation.CoroutineSystemContext
import botfarm.engine.simulation.Entity
import botfarm.engine.simulation.EntityComponent
import botfarm.game.GameSimulation
import botfarm.game.components.*
import botfarm.game.config.EquipmentSlot
import botfarm.game.config.ItemConfig
import botfarmshared.game.GameConstants
import botfarmshared.game.GameSimulationInfo
import botfarmshared.game.apidata.*
import botfarmshared.misc.buildShortRandomIdentifier

suspend fun syncAgent(
   context: CoroutineSystemContext,
   simulation: GameSimulation,
   entity: Entity,
   agentComponent: EntityComponent<AgentComponentData>,
   state: AgentSyncState,
   agentId: AgentId,
   syncId: String
) {
   val agentServerIntegration = simulation.agentServerIntegration

   val simulationTime = simulation.getCurrentSimulationTime()

   context.unwindIfNeeded()

   val newObservationsForInput = state.mutableObservations.toObservations()
   state.mutableObservations = MutableObservations()

   val agentSyncInput: AgentSyncInput

   synchronized(simulation) {
      if (simulation.shouldPauseAi) {
         agentComponent.modifyData {
            it.copy(
               agentIntegrationStatus = "paused",
               agentStatus = null,
               agentError = null,
               wasRateLimited = false
            )
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
         entity = entity,
         simulationTime = simulation.getCurrentSimulationTime()
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

      val equippedToolItemConfigAndStackIndex = entity.getEquippedItemConfigAndStackIndex(EquipmentSlot.Tool)

      val selfInfo = SelfInfo(
         entityInfo = selfEntityInfo,
         corePersonality = agentComponent.data.corePersonality,
         initialMemories = agentComponent.data.initialMemories,
         inventoryInfo = inventoryInfo,
         observationDistance = agentComponent.data.observationDistance,
         equippedItemConfigKey = equippedToolItemConfigAndStackIndex?.second?.key
      )

      agentSyncInput = AgentSyncInput(
         syncId = syncId,
         agentId = agentId,
         agentType = agentComponent.data.agentType,
         simulationId = simulation.simulationId,
         simulationTime = simulationTime,
         gameSimulationInfo = GameSimulationInfo(
            craftingRecipes = craftingRecipes,
            worldBounds = simulation.worldBounds
         ),
         gameConstants = GameConstants,
         selfInfo = selfInfo,
         newObservations = newObservationsForInput
      )

      agentComponent.modifyData {
         it.copy(
            agentIntegrationStatus = "waiting_for_agent",
            totalRemoteAgentRequests = it.totalRemoteAgentRequests + 1,
            agentError = null
         )
      }
   }

   val agentSyncOutputs = agentServerIntegration.sendSyncRequest(agentSyncInput)
   context.unwindIfNeeded()

   context.synchronizeSimulation {
      agentComponent.modifyData {
         it.copy(
            agentIntegrationStatus = "idle"
         )
      }

      agentSyncOutputs.forEach { agentSyncOutput ->
         try {
            handleAgentSyncOutput(
               agentSyncOutput = agentSyncOutput,
               agentComponent = agentComponent,
               simulation = simulation,
               state = state,
               entity = entity
            )
         } catch (exception: Exception) {
            val errorId = buildShortRandomIdentifier()
            println("Exception while handling agent step result (errorId = $errorId, agentId = $agentId, stepId = $syncId): ${exception.stackTraceToString()}")

            context.synchronizeSimulation {
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

