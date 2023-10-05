package botfarm.game.agentintegration

import botfarm.engine.simulation.CoroutineSystemContext
import botfarm.engine.simulation.Entity
import botfarm.engine.simulation.EntityComponent
import botfarm.game.GameSimulation
import botfarm.game.codeexecution.JavaScriptCodeSerialization
import botfarm.game.codeexecution.jsdata.JsCraftingRecipe
import botfarm.game.codeexecution.jsdata.JsInventoryItemStackInfo
import botfarm.game.codeexecution.jsdata.buildInfo
import botfarm.game.codeexecution.jsdata.buildWrapper
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

   val agentJavaScriptApi = state.agentJavaScriptApi

   val newObservationsForInput = state.mutableObservations.toObservations(api = agentJavaScriptApi)
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

      val craftingRecipeInfos = simulation.getCraftingRecipeInfos(
         crafterEntity = entity
      )

      val selfEntityInfo = buildEntityInfoForAgent(
         entity = entity,
         simulationTime = simulation.getCurrentSimulationTime()
      )

      val inventoryComponentData = entity.getComponent<InventoryComponentData>().data

      val inventoryInfo = InventoryInfo(
         itemStacks = inventoryComponentData.inventory.itemStacks.mapIndexed { stackIndex, itemStack ->
            val itemConfig = simulation.getConfig<ItemConfig>(itemStack.itemConfigKey)

            val itemStackInfo = itemStack.buildInfo(
               itemConfig = itemConfig
            )

            val itemStackVariableName = "inventory_item_${stackIndex}"

            ItemStackInfoWrapper(
               itemStackInfo = itemStackInfo,
               serializedAsJavaScript = JavaScriptCodeSerialization.serialize(JsInventoryItemStackInfo(
                  api = agentJavaScriptApi,
                  itemStackInfo = itemStackInfo,
                  stackIndex = stackIndex
               )),
               javaScriptVariableName = itemStackVariableName
            )
         }
      )

      val equippedToolItemConfigAndStackIndex = entity.getEquippedItemConfigAndStackIndex(EquipmentSlot.Tool)

      val selfInfo = SelfInfo(
         entityInfoWrapper = selfEntityInfo.buildWrapper(
            javaScriptVariableName = "self",
            api = agentJavaScriptApi
         ),
         corePersonality = agentComponent.data.corePersonality,
         initialMemories = agentComponent.data.initialMemories,
         inventoryInfo = inventoryInfo,
         observationDistance = agentComponent.data.observationDistance,
         equippedItemConfigKey = equippedToolItemConfigAndStackIndex?.second?.key
      )

      val craftingRecipeInfoWrappers = craftingRecipeInfos.mapIndexed { recipeIndex, craftingRecipeInfo ->
         val variableName = "crafting_recipe_${craftingRecipeInfo.itemConfigKey.replace("-", "_")}"

         CraftingRecipeInfoWrapper(
            craftingRecipeInfo = craftingRecipeInfo,
            javaScriptVariableName = variableName,
            serializedAsJavaScript = JavaScriptCodeSerialization.serialize(JsCraftingRecipe(
               api = agentJavaScriptApi,
               craftingRecipeInfo = craftingRecipeInfo
            ))
         )
      }

      agentSyncInput = AgentSyncInput(
         syncId = syncId,
         agentId = agentId,
         agentType = agentComponent.data.agentType,
         simulationId = simulation.simulationId,
         simulationTime = simulationTime,
         gameSimulationInfo = GameSimulationInfo(
            craftingRecipeInfoWrappers = craftingRecipeInfoWrappers,
            worldBounds = simulation.worldBounds
         ),
         gameConstants = GameConstants,
         selfInfo = selfInfo,
         newObservations = newObservationsForInput,
         agentTypeScriptInterfaceString = state.agentTypeScriptInterfaceString
      )

      agentComponent.modifyData {
         it.copy(
            agentIntegrationStatus = "waiting_for_agent",
            totalRemoteAgentRequests = it.totalRemoteAgentRequests + 1,
            agentError = null
         )
      }
   }

   state.mostRecentSyncInput = agentSyncInput
   val agentSyncOutputs = agentServerIntegration.sendSyncRequest(agentSyncInput)
   context.unwindIfNeeded()

   synchronized(simulation) {
      agentComponent.modifyData {
         it.copy(
            agentIntegrationStatus = "idle"
         )
      }
   }

   agentSyncOutputs.forEach { agentSyncOutput ->
      try {
         handleAgentSyncOutput(
            syncId = syncId,
            agentSyncInput = agentSyncInput,
            agentSyncOutput = agentSyncOutput,
            agentComponent = agentComponent,
            simulation = simulation,
            state = state,
            entity = entity
         )
      } catch (exception: Exception) {
         val errorId = buildShortRandomIdentifier()
         println("Exception while handling agent step result (errorId = $errorId, agentId = $agentId, stepId = $syncId): ${exception.stackTraceToString()}")

         synchronized(simulation) {
            agentComponent.modifyData {
               it.copy(
                  agentError = "Exception while handling agent step result (errorId = $errorId)"
               )
            }
         }
      }
   }
}

