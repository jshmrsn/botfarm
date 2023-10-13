package botfarm.game.agentintegration

import botfarm.common.PositionComponentData
import botfarm.common.isMoving
import botfarm.common.resolvePosition
import botfarm.engine.ktorplugins.ServerEnvironmentGlobals
import botfarm.engine.simulation.*
import botfarm.game.GameSimulation
import botfarm.game.components.*
import botfarm.game.config.EquipmentSlot
import botfarm.game.config.ItemConfig
import botfarm.game.scripting.JavaScriptCodeSerialization
import botfarm.game.scripting.UnwindScriptThreadThrowable
import botfarm.game.scripting.jsdata.*
import botfarmshared.engine.apidata.EntityId
import botfarmshared.game.apidata.*
import botfarmshared.misc.buildShortRandomIdentifier
import botfarmshared.misc.getCurrentUnixTimeSeconds
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.Source
import kotlin.concurrent.thread


class AgentIntegration(
   val simulation: GameSimulation,
   val entity: Entity,
   val agentType: String,
   val agentId: AgentId,
   val fastJavaScriptThreadSleep: Boolean
) {
   private val startedActionUniqueIds = mutableSetOf<String>()
   private val agentControlledComponent = this.entity.getComponent<AgentControlledComponentData>()
   private val characterComponent = this.entity.getComponent<CharacterComponentData>()
   private val actionResultsByActionUniqueId = mutableMapOf<String, ActionResult>()

   private var mostRecentSyncInput: AgentSyncInput? = null

   // Prevent new agents from seeing events from before they are started
   private var previousNewEventCheckSimulationTime = this.simulation.getCurrentSimulationTime()
   private var previousCheckedActivityStreamIndex = -1

   private var pendingObservations = PendingObservations()

   private var activeAction: Action? = null
   private val pendingActions = mutableListOf<Action>()

   private val agentJavaScriptApi = AgentJavaScriptApi(this, shouldMinimizeSleep = this.fastJavaScriptThreadSleep)

   private fun buildEntityInfoWrapper(entity: Entity): EntityInfoWrapper {
      val entityInfo = buildEntityInfoForAgent(
         entity = entity,
         simulationTime = this.simulation.simulationTime
      )

      val variableTypeName = if (entityInfo.characterInfo != null) {
         "character"
      } else if (entityInfo.itemInfo != null) {
         entityInfo.itemInfo.itemConfigKey.replace("-", "_")
      } else {
         ""
      }

      val entityVariableName = "${variableTypeName}_entity_${entityInfo.entityId.value}"

      return EntityInfoWrapper(
         serializedAsJavaScript = JavaScriptCodeSerialization.serialize(
            JsEntity(
               api = null,
               entityInfo = entityInfo
            )
         ),
         javaScriptVariableName = entityVariableName,
         entityInfo = entityInfo
      )
   }

   private val cachedEntityInfoWrappersByEntityId = mutableMapOf<EntityId, EntityInfoWrapper>()

   val entityContainer = EntityContainer(
      simulation = this.simulation,
      onEntityCreated = { entity ->
         if (entity.getComponentOrNull<PositionComponentData>() != null &&
            entity.entityId != this.entity.entityId) {
            val entityInfoWrapper = this.buildEntityInfoWrapper(
               entity = entity
            )

            this.cachedEntityInfoWrappersByEntityId[entity.entityId] = entityInfoWrapper

            this.pendingObservations.entityObservationEvents.add(
               EntityObservationEvent(
                  newEntity = ObservedNewEntity(
                     entityInfoWrapper = entityInfoWrapper
                  )
               )
            )
         }
      },
      onEntityChanged = { entity ->
         if (entity.getComponentOrNull<PositionComponentData>() != null &&
            entity.entityId != this.entity.entityId) {
            val entityInfoWrapper = this.buildEntityInfoWrapper(
               entity = entity
            )

            this.cachedEntityInfoWrappersByEntityId[entity.entityId] = entityInfoWrapper

            this.pendingObservations.entityObservationEvents.add(
               EntityObservationEvent(
                  entityChanged = ObservedEntityChanged(
                     entityInfoWrapper = entityInfoWrapper
                  )
               )
            )
         }
      },
      onEntityDestroyed = { entity ->
         if (entity.getComponentOrNull<PositionComponentData>() != null &&
            entity.entityId != this.entity.entityId) {
            this.cachedEntityInfoWrappersByEntityId.remove(entity.entityId)

            this.pendingObservations.entityObservationEvents.add(
               EntityObservationEvent(
                  entityDestroyed = ObservedEntityDestroyed(
                     entityId = entity.entityId
                  )
               )
            )
         }
      }
   )

   class RunningScript(
      val thread: Thread,
      val scriptToRun: ScriptToRun
   )

   private var runningScript: RunningScript? = null

   private var mostRecentCompletedScriptId: String? = null

   val agentJavaScriptRuntime = AgentJavaScriptRuntime(
      agentJavaScriptApi = this.agentJavaScriptApi
   )

   private val agentTypeScriptInterfaceString =
      this::class.java.getResource("/scripted-agent-interfaces.ts")?.readText()
         ?: throw Exception("Scripted agent interfaces resource not found")

   fun hasStartedAction(actionUniqueId: String) = this.startedActionUniqueIds.contains(actionUniqueId)

   fun getActionResult(actionUniqueId: String) = this.actionResultsByActionUniqueId[actionUniqueId]

   fun addPendingAction(action: Action) {
      this.pendingActions.add(action)
   }

   fun waitForMovement(
      positionComponent: EntityComponent<PositionComponentData>,
      movementResult: GameSimulation.MoveToResult.Success,
      callback: () -> Unit
   ) {
      val simulation = this.simulation

      simulation.queueCallback(
         condition = {
            val keyFrames = positionComponent.data.positionAnimation.keyFrames

            val result = positionComponent.entity.isDead ||
                    positionComponent.data.movementId != movementResult.movementId ||
                    keyFrames.isEmpty() ||
                    simulation.getCurrentSimulationTime() > keyFrames.last().time

            result
         }
      ) {
         callback()
      }
   }

   fun autoInteractWithEntity(
      targetEntity: Entity,
      expectedActionType: ActionType,
      callback: (ActionResultType) -> Unit = {}
   ) {
      val simulation = this.simulation
      val entity = this.entity

      simulation.autoInteractWithEntity(
         entity = entity,
         targetEntity = targetEntity,
         expectedActionType = expectedActionType,
         callback = callback
      )
   }

   fun speak(
      message: String,
      actionUniqueId: String,
      reason: String? = null
   ) {
      val simulation = this.simulation

      synchronized(simulation) {
         simulation.addCharacterMessage(
            entity = this.entity,
            message = message,
            agentReason = reason,
            agentActionUniqueId = actionUniqueId
         )
      }
   }

   private fun recordThought(
      thought: String,
      reason: String? = null,
      actionUniqueId: String
   ) {
      val simulation = this.simulation

      synchronized(simulation) {
         simulation.addActivityStreamEntry(
            message = thought,
            sourceEntityId = this.entity.entityId,
            sourceLocation = this.entity.resolvePosition(),
            observedByEntityIdsOverride = listOf(this.entity.entityId),
            agentReason = reason,
            agentUniqueActionId = actionUniqueId,
            actionType = ActionType.Thought,
            actionResultType = ActionResultType.Success,
            onlyShowForPerspectiveEntity = true
         )
      }
   }

   fun recordDynamicEntityChanges() {
      // jshmrsn: This handles the special case that entity resolved locations change over time via the
      // PositionComponent's animation functionality, even if the underlying component data hasn't changed
      for (cachedEntityInfoWrapper in this.cachedEntityInfoWrappersByEntityId.values) {
         if (cachedEntityInfoWrapper.entityInfo.isVisible) {
            val knownEntity = this.entityContainer.entitiesById[cachedEntityInfoWrapper.entityInfo.entityId]

            if (knownEntity != null) {
               val entityLocation = knownEntity.resolvePosition()

               if (entityLocation.distance(cachedEntityInfoWrapper.entityInfo.location) > 0.01) {
                  val updatedEntityInfoWrapper = this.buildEntityInfoWrapper(
                     entity = knownEntity
                  )

                  this.cachedEntityInfoWrappersByEntityId[knownEntity.entityId] = updatedEntityInfoWrapper

                  this.pendingObservations.entityObservationEvents.add(
                     EntityObservationEvent(
                        entityChanged = ObservedEntityChanged(
                           entityInfoWrapper = updatedEntityInfoWrapper
                        )
                     )
                  )
               }
            }
         }
      }
   }

   suspend fun syncWithAgent(
      coroutineSystemContext: CoroutineSystemContext,
      simulation: GameSimulation,
      syncId: String
   ) {
      val entity = this.entity
      val agentId = this.agentId

      val perspectiveEntityContainer = simulation.getPerspectiveEntityContainerForCharacterEntityId(
         characterEntityId = entity.entityId
      ) ?: throw Exception("No perspective entity container for agent's character entity")

      this.entityContainer.sync(
         source = perspectiveEntityContainer,
         hasVisibilityOfEntity = {
            true
         }
      )

      this.recordDynamicEntityChanges()

      val agentServerIntegration = simulation.agentService

      val simulationTime = simulation.getCurrentSimulationTime()

      coroutineSystemContext.unwindIfNeeded()

      val agentControlledComponent = this.agentControlledComponent

      if (simulation.debugInfoComponent.data.aiPaused) {
         agentControlledComponent.modifyData {
            it.copy(
               agentIntegrationStatus = "paused",
               agentStatus = null
            )
         }

         return
      }

      val newObservationsForInput = this.pendingObservations.toObservations()
      this.pendingObservations = PendingObservations()

      val selfEntityInfo = buildEntityInfoForAgent(
         entity = entity,
         simulationTime = simulation.getCurrentSimulationTime()
      )

      val agentSyncInput = run {
         val craftingRecipeInfos = simulation.getCraftingRecipeInfos(
            crafterEntity = entity
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
                  serializedAsJavaScript = JavaScriptCodeSerialization.serialize(
                     JsInventoryItemStackInfo(
                        api = null,
                        itemStackInfo = itemStackInfo,
                        stackIndex = stackIndex
                     )
                  ),
                  javaScriptVariableName = itemStackVariableName
               )
            }
         )

         val equippedToolItemConfigAndStackIndex = entity.getEquippedItemConfigAndStackIndex(EquipmentSlot.Tool)

         val selfInfo = SelfInfo(
            entityInfoWrapper = selfEntityInfo.buildWrapper(
               javaScriptVariableName = "self",
               api = null
            ),
            corePersonality = agentControlledComponent.data.corePersonality,
            initialMemories = agentControlledComponent.data.initialMemories,
            inventoryInfo = inventoryInfo,
            observationRadius = characterComponent.data.observationRadius,
            equippedItemConfigKey = equippedToolItemConfigAndStackIndex?.second?.key
         )

         val craftingRecipeInfoWrappers = craftingRecipeInfos.mapIndexed { recipeIndex, craftingRecipeInfo ->
            val variableName = "crafting_recipe_${craftingRecipeInfo.itemConfigKey.replace("-", "_")}"

            CraftingRecipeInfoWrapper(
               craftingRecipeInfo = craftingRecipeInfo,
               javaScriptVariableName = variableName,
               serializedAsJavaScript = JavaScriptCodeSerialization.serialize(
                  JsCraftingRecipe(
                     api = null,
                     craftingRecipeInfo = craftingRecipeInfo
                  )
               )
            )
         }

         AgentSyncInput(
            syncId = syncId,
            agentId = agentId,
            agentType = agentControlledComponent.data.agentType,
            simulationId = simulation.simulationId,
            simulationTime = simulationTime,
            gameSimulationInfo = GameSimulationInfo(
               craftingRecipeInfoWrappers = craftingRecipeInfoWrappers,
               worldBounds = simulation.worldBounds
            ),
            gameConstants = GameConstants.default,
            selfInfo = selfInfo,
            newObservations = newObservationsForInput,
            agentTypeScriptInterfaceString = this.agentTypeScriptInterfaceString,
            mostRecentCompletedScriptId = this.mostRecentCompletedScriptId
         )
      }

      agentControlledComponent.modifyData {
         it.copy(
            agentIntegrationStatus = "waiting_for_agent",
            totalRemoteAgentRequests = it.totalRemoteAgentRequests + 1
         )
      }

      this.mostRecentSyncInput = agentSyncInput

      val agentSyncOutputs = agentServerIntegration.sendSyncRequest(agentSyncInput)
      coroutineSystemContext.unwindIfNeeded()

      agentControlledComponent.modifyData {
         it.copy(
            agentIntegrationStatus = "idle"
         )
      }

      agentSyncOutputs.forEach { agentSyncOutput ->
         try {
            this.handleAgentSyncOutput(
               agentSyncInput = agentSyncInput,
               agentSyncOutput = agentSyncOutput,
               agentControlledComponent = agentControlledComponent,
               simulation = simulation,
               coroutineSystemContext = coroutineSystemContext
            )
         } catch (exception: Exception) {
            val errorId = buildShortRandomIdentifier()
            println("Exception while handling agent step result (errorId = $errorId, agentId = $agentId, stepId = $syncId): ${exception.stackTraceToString()}")

            simulation.addActivityStreamEntry(
               title = "Agent integration error handling sync output ($errorId)",
               sourceEntityId = entity.entityId,
               onlyShowForPerspectiveEntity = true,
               shouldReportToAi = false
            )
         }
      }
   }

   suspend fun endJavaScriptThread(
      coroutineSystemContext: CoroutineSystemContext
   ) {
      val threadStateLock = this

      val previousRunningScript = synchronized(threadStateLock) {
         val runningScript = this.runningScript
         this.agentJavaScriptApi.shouldEndScript = true
         runningScript
      }

      if (previousRunningScript != null) {
         val previousScriptId = previousRunningScript.scriptToRun.scriptId

         if (previousRunningScript.thread.isAlive) {
            println("Agent script thread exists and is active, so attempting to end now ($previousScriptId)")

            val waitStartTime = getCurrentUnixTimeSeconds()
            val timeout = 3.0

            while (true) {
               val waitedTime = getCurrentUnixTimeSeconds() - waitStartTime
               if (!previousRunningScript.thread.isAlive) {
                  println("Agent script thread has now ended after waiting $waitedTime seconds ($previousScriptId)")
                  break
               } else if (waitedTime > timeout) {
                  println("Agent script thread did not end after $timeout seconds, so interrupting ($previousScriptId)")
                  try {
                     previousRunningScript.thread.interrupt()
                  } catch (exception: Exception) {
                     println("Exception while calling thread.interrupt for script ($previousScriptId): " + exception.stackTraceToString())
                  }
                  break
               } else {
                  coroutineSystemContext.delay(200)
               }
            }

            coroutineSystemContext.delay(200)
         } else {
            println("Agent script thread exists, but is not alive, so not interrupting ($previousScriptId)")
         }
      } else {
         println("No previous agent script thread exists, starting a new one now")
      }

      synchronized(threadStateLock) {
         this.runningScript = null
         this.agentJavaScriptApi.shouldEndScript = false
      }
   }

   fun recordObservationsForAgent() {
      val entity = this.entity
      val simulation = this.simulation

      val simulationTimeForStep: Double

      simulationTimeForStep = simulation.getCurrentSimulationTime()

      val activityStream = simulation.getActivityStream()

      val newActivityStreamEntries = activityStream.filterIndexed { activityStreamIndex, activityStreamEntry ->
         val wasObserved = activityStreamEntry.observedByEntityIds == null ||
                 activityStreamEntry.observedByEntityIds.contains(entity.entityId)

         val isNew = activityStreamIndex > this.previousCheckedActivityStreamIndex

         isNew && wasObserved && activityStreamEntry.shouldReportToAi
      }

      this.pendingObservations.activityStreamEntries.addAll(newActivityStreamEntries.map {
//         println("Adding observed activity stream entry: " + it.actionType?.name)
         it.copy(
            // jshmrsn: Agents should not have awareness of internal observation system
            // Ideally, this would be a different class to explicitly omit this field
            observedByEntityIds = null,
            agentReason = if (it.sourceEntityId == entity.entityId) {
               it.agentReason
            } else {
               null
            }
         )
      })

      this.previousCheckedActivityStreamIndex = activityStream.lastIndex
      this.previousNewEventCheckSimulationTime = simulationTimeForStep
   }

   private val debugInfoByKey = mutableMapOf<String, String>()

   private suspend fun handleAgentSyncOutput(
      coroutineSystemContext: CoroutineSystemContext,
      agentSyncInput: AgentSyncInput,
      agentSyncOutput: AgentSyncOutput,
      agentControlledComponent: EntityComponent<AgentControlledComponentData>,
      simulation: GameSimulation,
   ) {
      val entity = this.entity
      val actions = agentSyncOutput.actions
      val scriptToRun = agentSyncOutput.scriptToRun

      val agentId = this.agentId

      if (agentSyncOutput.error != null) {
         val errorId = buildShortRandomIdentifier()
         val errorToSendToClient = if (ServerEnvironmentGlobals.hideErrorDetailsFromClients) {
            "(Error ID $errorId)"
         } else {
            "${agentSyncOutput.error} (Error ID $errorId)"
         }

         simulation.broadcastAlertMessage(
            AlertMode.ConsoleError,
            "Agent error: $errorToSendToClient"
         )

         simulation.addActivityStreamEntry(
            title = "Agent error",
            longMessage = errorToSendToClient,
            sourceEntityId = entity.entityId,
            onlyShowForPerspectiveEntity = true,
            shouldReportToAi = false
         )
      }

      if (agentSyncOutput.startedRunningPrompt != null) {
         simulation.addActivityStreamEntry(
            title = "Running prompt: ${agentSyncOutput.startedRunningPrompt.description} (${agentSyncOutput.startedRunningPrompt.promptId})",
            sourceEntityId = entity.entityId,
            onlyShowForPerspectiveEntity = true,
            shouldReportToAi = false,
            longMessage = if (ServerEnvironmentGlobals.hidePromptDetailsFromClients) {
               null
            } else {
               agentSyncOutput.startedRunningPrompt.prompt
            },
            message = "Input Tokens: ${agentSyncOutput.startedRunningPrompt.inputTokens}"
         )
      }

      if (agentSyncOutput.promptResult != null) {
         simulation.addActivityStreamEntry(
            title = "Prompt result: ${agentSyncOutput.promptResult.description} (${agentSyncOutput.promptResult.promptId})",
            sourceEntityId = entity.entityId,
            onlyShowForPerspectiveEntity = true,
            shouldReportToAi = false,
            longMessage = if (ServerEnvironmentGlobals.hidePromptDetailsFromClients) {
               null
            } else {
               agentSyncOutput.promptResult.response
            },
            message = "Output Tokens: ${agentSyncOutput.promptResult.completionTokens}"
         )
      }

      if (agentSyncOutput.debugInfoByKey != null) {
         agentSyncOutput.debugInfoByKey.forEach { debugInfoKey, debugInfo ->
            this.debugInfoByKey[debugInfoKey] = debugInfo
         }
      }

      agentControlledComponent.modifyData {
         it.copy(
            agentRemoteDebugInfo = this.debugInfoByKey
               .entries
               .sortedBy { it.key }
               .flatMap {
                  listOf("", "# ====== ${it.key} ======") + it.value.lines()
               }.joinToString("  \n"), // two spaces from https://github.com/remarkjs/react-markdown/issues/273,
            agentStatus = agentSyncOutput.agentStatus ?: it.agentStatus,
            lastAgentResponseUnixTime = getCurrentUnixTimeSeconds()
         )
      }

      agentSyncOutput.promptUsages.forEach {
         updateComponentModelUsage(it, agentControlledComponent)
      }

      if (scriptToRun != null) {
         val scriptId = scriptToRun.scriptId
         val script = scriptToRun.script

         println("Got new script from agent (${agentId.value}, scriptId = $scriptId):")
         println(script)

         if (entity.isMoving) {
            simulation.startEntityMovement(
               entity = entity,
               endPoint = entity.resolvePosition()
            )
         }

         this.activeAction = null
         this.pendingActions.clear()

         val threadStateLock: Any = this

         this.endJavaScriptThread(coroutineSystemContext)

         simulation.addActivityStreamEntry(
            title = "Started script...",
            longMessage = script,
            sourceEntityId = entity.entityId,
            onlyShowForPerspectiveEntity = true,
            shouldReportToAi = false
         )

         val wrappedResponseScript = """
         (function() {
         $script
         })()
      """.trimIndent()

         agentControlledComponent.modifyData {
            it.copy(
               executingScriptId = scriptId,
               executingScript = script,
               currentActionTimeline = null
            )
         }

         val craftingRecipeInfos = simulation.getCraftingRecipeInfos(
            crafterEntity = entity
         )


         fun addScriptExecutionErrorFromBackgroundThread(
            description: String,
            exception: Exception
         ) {
            simulation.runOnSimulationThread(
               task = {
                  this.pendingObservations.scriptExecutionErrors.add(
                     ScriptExecutionError(
                        scriptId = scriptId,
                        error = exception.stackTraceToString()
                     )
                  )

                  val errorId = buildShortRandomIdentifier()

                  println("$description (scriptId = $scriptId, errorId = $errorId): ${exception.stackTraceToString()}")

                  simulation.addActivityStreamEntry(
                     title = "Agent script execution error",
                     message = "Script ID: $scriptId\nError ID: $errorId",
                     sourceEntityId = entity.entityId,
                     onlyShowForPerspectiveEntity = true,
                     shouldReportToAi = false,
                     longMessage = if (ServerEnvironmentGlobals.hideErrorDetailsFromClients) {
                        null
                     } else {
                        exception.stackTraceToString()
                     }
                  )

                  simulation.broadcastAlertMessage(
                     AlertMode.ConsoleError,
                     "Agent script execution error (Script ID $scriptId, Error ID $errorId)"
                  )
               },
               handleException = {}
            )
         }

         synchronized(threadStateLock) {
            val agentJavaScriptApi = this.agentJavaScriptApi

            println("Starting new thread for script $scriptId")
            val newThread = thread {
               println("In thread for script $scriptId (${Thread.currentThread().name})")
               agentJavaScriptApi.notifyJavaScriptThreadStarted()
               val bindings = this.agentJavaScriptRuntime.bindings

               val jsConversionContext = JsConversionContext(bindings)

               for (craftingRecipeInfo in craftingRecipeInfos) {
                  val variableName = "crafting_recipe_${craftingRecipeInfo.itemConfigKey.replace("-", "_")}"

                  bindings.putMember(
                     variableName, agentJavaScriptApi.buildJsCraftingRecipe(
                        craftingRecipeInfo = craftingRecipeInfo,
                        jsConversionContext = jsConversionContext
                     )
                  )
               }

               val inventoryItemStacks = agentJavaScriptApi.getCurrentInventoryItemStacks()

               for (jsInventoryItemStackInfo in inventoryItemStacks.values) {
                  val stackIndex = jsInventoryItemStackInfo.stackIndex

                  val itemStackVariableName = "inventory_item_${stackIndex}"

                  bindings.putMember(itemStackVariableName, jsInventoryItemStackInfo)
               }

               val selfJsEntity = agentJavaScriptApi.buildJsEntity(agentSyncInput.selfInfo.entityInfoWrapper.entityInfo)

               bindings.putMember("self", selfJsEntity)

               for (cachedEntityInfoWrapper in this.cachedEntityInfoWrappersByEntityId.values) {

                  val entityInfo = cachedEntityInfoWrapper.entityInfo
                  val jsEntity = agentJavaScriptApi.buildJsEntity(entityInfo)
                  val entityVariableName = cachedEntityInfoWrapper.javaScriptVariableName

                  bindings.putMember(entityVariableName, jsEntity)
               }

               val sourceName = "agent"
               val javaScriptSource = Source.newBuilder("js", wrappedResponseScript, sourceName).build()

               try {
                  println("Calling javaScriptContext.eval for script $scriptId")
                  this.agentJavaScriptRuntime.javaScriptContext.eval(javaScriptSource)
                  println("Agent script eval complete: $scriptId")

                  synchronized(threadStateLock) {
                     println("Agent script thread complete: $scriptId")
                     if (this.runningScript?.scriptToRun?.scriptId == scriptId) {
                        this.runningScript = null
                        this.mostRecentCompletedScriptId = scriptId

                        simulation.runOnSimulationThread {
                           simulation.addActivityStreamEntry(
                              title = "Finished executing script",
                              sourceEntityId = entity.entityId,
                              onlyShowForPerspectiveEntity = true,
                              shouldReportToAi = false
                           )
                        }
                     }
                  }
               } catch (unwindScriptThreadThrowable: UnwindScriptThreadThrowable) {
                  println("Got unwind agent script thread throwable ($scriptId): ${unwindScriptThreadThrowable.reason}")
                  synchronized(threadStateLock) {
                     if (this.runningScript?.scriptToRun?.scriptId == scriptId) {
                        this.runningScript = null
                        this.mostRecentCompletedScriptId = scriptId
                     }
                  }
               } catch (polyglotException: PolyglotException) {
                  val resolvedException = if (polyglotException.isHostException) {
                     polyglotException.asHostException()
                  } else {
                     polyglotException
                  }

                  if (resolvedException is UnwindScriptThreadThrowable) {
                     println("Got unwind agent script thread throwable via PolyglotException ($scriptId): ${resolvedException.reason}")

                     synchronized(threadStateLock) {
                        if (this.runningScript?.scriptToRun?.scriptId == scriptId) {
                           this.runningScript = null
                           this.mostRecentCompletedScriptId = scriptId

                           simulation.runOnSimulationThread {
                              simulation.addActivityStreamEntry(
                                 title = "Script execution unwound",
                                 message = scriptId,
                                 sourceEntityId = entity.entityId,
                                 onlyShowForPerspectiveEntity = true,
                                 shouldReportToAi = false
                              )
                           }
                        }
                     }
                  } else {
                     synchronized(threadStateLock) {
                        if (this.runningScript?.scriptToRun?.scriptId == scriptId) {
                           this.runningScript = null
                           this.mostRecentCompletedScriptId = scriptId

                           addScriptExecutionErrorFromBackgroundThread(
                              "Polyglot exception evaluating agent JavaScript",
                              polyglotException
                           )
                        }
                     }
                  }
               } catch (exception: Exception) {
                  synchronized(threadStateLock) {
                     if (this.runningScript?.scriptToRun?.scriptId == scriptId) {
                        this.runningScript = null
                        this.mostRecentCompletedScriptId = scriptId

                        addScriptExecutionErrorFromBackgroundThread("Exception evaluating agent JavaScript", exception)
                     }
                  }
               }
            }

            println("Created thread ${Thread.currentThread().name} for script $scriptId")

            coroutineSystemContext.setOnCancelCallback {
               println("Ending JavaScript thread due to coroutine system cancellation")
               runBlocking {
                  this@AgentIntegration.endJavaScriptThread(coroutineSystemContext)
               }
            }

            this.runningScript = RunningScript(
               thread = newThread,
               scriptToRun = scriptToRun
            )
         }
      } else if (actions != null) {
         val prettyPrintJson = Json {
            prettyPrint = true
         }

         simulation.addActivityStreamEntry(
            title = "Started actions...",
            longMessage = prettyPrintJson.encodeToString(actions),
            sourceEntityId = entity.entityId,
            onlySourceEntityCanObserve = true,
            shouldReportToAi = false,
            onlyShowForPerspectiveEntity = true
         )

         if (entity.isMoving) {
            simulation.startEntityMovement(
               entity = entity,
               endPoint = entity.resolvePosition()
            )
         }

         agentControlledComponent.modifyData {
            it.copy(
               currentActionTimeline = null
            )
         }

         this.activeAction = null
         this.pendingActions.clear()
         this.pendingActions.addAll(agentSyncOutput.actions)
      }
   }

   private fun performAgentAction(
      action: Action,
      onResult: (ActionResult) -> Unit
   ) {
      val entity = this.entity
      val simulation = this.simulation
      val positionComponent = entity.getComponent<PositionComponentData>()
      val characterComponent = entity.getComponent<CharacterComponentData>()
      val currentLocation = entity.resolvePosition()
      val simulationTimeForStep = simulation.getCurrentSimulationTime()

      val actionUniqueId = action.actionUniqueId
      val debugInfo = "${this.agentType}, actionUniqueId = ${actionUniqueId}"

      val prettyPrint = Json { prettyPrint = true }

      println("Action started: $actionUniqueId\n${prettyPrint.encodeToString(action)}")
      val speak = action.speak
      val recordThought = action.recordThought
      this.pendingObservations.startedActionUniqueIds.add(actionUniqueId)

      fun addActionResult() {
         synchronized(this.simulation) {
            println("Action completed result: $actionUniqueId")
            val actionResult = ActionResult(
               actionUniqueId = actionUniqueId
            )

            this.actionResultsByActionUniqueId[actionUniqueId] = actionResult
            this.pendingObservations.actionResults.add(actionResult)

            onResult(actionResult)
         }
      }

      val facialExpressionEmoji = action.facialExpressionEmoji
      val walk = action.walk
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
         addActionResult()
      }

      if (speak != null) {
         this.speak(
            message = speak,
            reason = reason,
            actionUniqueId = actionUniqueId
         )

         addActionResult()
      }

      if (recordThought != null) {
         this.recordThought(
            thought = recordThought,
            reason = reason,
            actionUniqueId = actionUniqueId
         )
         addActionResult()
      }

      if (walk != null) {
         val endPoint = walk.location

         this.pendingObservations.movementRecords.add(
            MovementRecord(
               startedAtTime = simulationTimeForStep,
               startPoint = currentLocation,
               endPoint = endPoint,
               reason = reason
            )
         )

         val movementResult = simulation.startEntityMovement(
            entity = entity,
            endPoint = endPoint
         )

         if (movementResult is GameSimulation.MoveToResult.Success) {
            this.waitForMovement(
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
         val itemConfig = simulation.getConfig<ItemConfig>(itemConfigKey)

         val craftItemResult = simulation.craftItem(
            entity = entity,
            itemConfig = itemConfig
         )

         if (craftItemResult != GameSimulation.CraftItemResult.Success) {
            simulation.addActivityStreamEntry(
               actionType = ActionType.Craft,
               agentUniqueActionId = actionUniqueId,
               actionResultType = when (craftItemResult) {
                  GameSimulation.CraftItemResult.CannotAfford -> ActionResultType.CannotAfford
                  GameSimulation.CraftItemResult.ItemCannotBeCrafted -> ActionResultType.ItemCannotBeCrafted
                  else -> ActionResultType.Failed
               },
               sourceLocation = entity.resolvePosition(),
               sourceEntityId = entity.entityId,
               targetItemConfig = itemConfig,
               onlyShowForPerspectiveEntity = true
            )
         }

         addActionResult()
      }

      if (useEquippedToolItem != null) {
         val result = simulation.useEquippedToolItem(
            interactingEntity = entity,
            expectedItemConfigKey = null
         )

         addActionResult()

         val equippedToolItemConfigAndStackIndex = entity.getEquippedItemConfigAndStackIndex(EquipmentSlot.Tool)
         val equippedToolItemConfig = equippedToolItemConfigAndStackIndex?.second

         if (result !is GameSimulation.UseEquippedItemResult.Success) {
            val spawnItemOnUseConfig = equippedToolItemConfig?.spawnItemOnUseConfig

            val spawnItemConfig = spawnItemOnUseConfig?.let { simulation.getConfig<ItemConfig>(it.spawnItemConfigKey) }

            simulation.addActivityStreamEntry(
               actionType = ActionType.UseEquippedTool,
               agentUniqueActionId = actionUniqueId,
               actionResultType = when (result) {
                  GameSimulation.UseEquippedItemResult.UnexpectedEquippedItem -> ActionResultType.UnexpectedEquippedItem
                  GameSimulation.UseEquippedItemResult.NoActionForEquippedTool -> ActionResultType.NoActionForEquippedTool
                  GameSimulation.UseEquippedItemResult.NoToolItemEquipped -> ActionResultType.NoToolItemEquipped
                  GameSimulation.UseEquippedItemResult.Busy -> ActionResultType.Busy
                  GameSimulation.UseEquippedItemResult.Obstructed -> ActionResultType.Obstructed
                  else -> ActionResultType.Failed
               },
               sourceLocation = entity.resolvePosition(),
               sourceEntityId = entity.entityId,
               targetItemConfig = equippedToolItemConfig,
               resultItemConfig = spawnItemConfig,
               onlyShowForPerspectiveEntity = true
            )
         }
      }

      if (equipInventoryItem != null) {
         val itemConfigKey = equipInventoryItem.itemConfigKey
         val itemConfig = this.simulation.getConfig<ItemConfig>(equipInventoryItem.itemConfigKey)

         addActionResult()

         val equipResult = simulation.equipItem(
            entity = entity,
            expectedItemConfigKey = itemConfigKey,
            requestedStackIndex = equipInventoryItem.stackIndex
         )

         if (equipResult != GameSimulation.EquipItemResult.Success) {
            simulation.broadcastAlertAsGameMessage("Unable to equip item for agent ($debugInfo): actionUniqueId = $actionUniqueId, itemConfigKey = $itemConfigKey, result ${equipResult.name}")

            simulation.addActivityStreamEntry(
               actionResultType = when (equipResult) {
                  GameSimulation.EquipItemResult.ItemCannotBeEquipped -> ActionResultType.ItemCannotBeEquipped
                  GameSimulation.EquipItemResult.ItemNotInInventory -> ActionResultType.ItemNotInInventory
                  GameSimulation.EquipItemResult.UnexpectedItemInStack -> ActionResultType.UnexpectedItemInStack
                  else -> ActionResultType.Failed
               },
               agentUniqueActionId = actionUniqueId,
               sourceLocation = entity.resolvePosition(),
               sourceEntityId = entity.entityId,
               actionType = ActionType.EquipItem,
               onlySourceEntityCanObserve = true,
               targetItemConfig = itemConfig
            )
         }
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

         if (stackIndex == null) {
            simulation.addActivityStreamEntry(
               onlySourceEntityCanObserve = true,
               sourceEntityId = entity.entityId,
               actionType = ActionType.DropItem,
               actionResultType = ActionResultType.ItemNotInInventory,
               agentReason = reason,
               agentUniqueActionId = actionUniqueId
            )
         } else {
            val droppedEntity = simulation.dropItemStack(
               droppingEntity = entity,
               expectedItemConfigKey = itemConfigKey,
               stackIndex = stackIndex,
               amountToDropFromStack = amount,
               agentReason = reason,
               agentActionUniqueId = actionUniqueId
            )

            if (droppedEntity == null) {
               simulation.addActivityStreamEntry(
                  onlySourceEntityCanObserve = true,
                  sourceEntityId = entity.entityId,
                  actionType = ActionType.DropItem,
                  actionResultType = ActionResultType.Failed,
                  agentReason = reason,
                  agentUniqueActionId = actionUniqueId
               )
            }
         }

         addActionResult()
      }

      fun autoInteractWithEntity(
         targetEntityId: EntityId,
         expectedActionType: ActionType
      ) {
         // jshmrsn: Currently, all actions on entities are executed by the single autoInteractWithEntity
         // The provided actionId is currently just a hint for agents to keep track of their intended interaction
         val targetEntity = simulation.getEntityOrNull(targetEntityId)

         if (targetEntity == null) {
            val destroyedTargetEntity = simulation.getDestroyedEntityOrNull(targetEntityId)

            if (destroyedTargetEntity != null) {
               simulation.addActivityStreamEntry(
                  actionType = expectedActionType,
                  agentUniqueActionId = actionUniqueId,
                  actionResultType = ActionResultType.TargetNoLongerExists,
                  sourceEntityId = entity.entityId,
                  onlySourceEntityCanObserve = true,
                  targetEntityId = targetEntityId,
                  targetItemConfig = destroyedTargetEntity.itemConfigOrNull
               )
            } else {
               simulation.addActivityStreamEntry(
                  actionType = expectedActionType,
                  agentUniqueActionId = actionUniqueId,
                  actionResultType = ActionResultType.InvalidTargetEntityId,
                  sourceEntityId = entity.entityId,
                  onlySourceEntityCanObserve = true,
                  targetEntityId = targetEntityId
               )
            }

            addActionResult()
         } else {
            this.autoInteractWithEntity(
               targetEntity = targetEntity,
               expectedActionType = expectedActionType
            ) { actionResult ->
               println("Agent autoInteractResult ($expectedActionType): " + actionResult.name)

               if (actionResult != ActionResultType.Success) {
                  simulation.addActivityStreamEntry(
                     actionType = expectedActionType,
                     agentUniqueActionId = actionUniqueId,
                     actionResultType = actionResult,
                     sourceEntityId = entity.entityId,
                     onlySourceEntityCanObserve = true,
                     onlyShowForPerspectiveEntity = true,
                     targetEntityId = targetEntity.entityId,
                     targetItemConfig = targetEntity.itemConfigOrNull
                  )
               }

               addActionResult()
            }
         }
      }

      if (pickUpEntity != null) {
         autoInteractWithEntity(
            targetEntityId = pickUpEntity.targetEntityId,
            expectedActionType = ActionType.PickUpItem
         )
      }

      if (useEquippedToolItemOnEntity != null) {
         autoInteractWithEntity(
            targetEntityId = useEquippedToolItemOnEntity.targetEntityId,
            expectedActionType = ActionType.UseEquippedTool
         )
      }
   }

   fun updatePendingActions() {
      val entity = this.entity

      synchronized(this.simulation) {
         if (this.activeAction == null) {
            if (entity.isAvailableToPerformAction) {
               val nextAction = this.pendingActions.removeFirstOrNull()

               if (nextAction != null) {
                  this.activeAction = nextAction
                  this.startedActionUniqueIds.add(nextAction.actionUniqueId)

                  val jsonFormat = Json {
                     prettyPrint = true
                     encodeDefaults = false
                  }

                  entity.getComponent<AgentControlledComponentData>().modifyData {
                     it.copy(
                        currentActionTimeline = if (it.currentActionTimeline != null) {
                           it.currentActionTimeline + "\n"
                        } else {
                           ""
                        } + jsonFormat.encodeToString(nextAction)
                     )
                  }

                  this.performAgentAction(
                     action = nextAction
                  ) { actionResult ->
                     this.activeAction = null

                     entity.getComponent<AgentControlledComponentData>().modifyData {
                        it.copy(
                           currentActionTimeline = it.currentActionTimeline + "\n(done)"
                        )
                     }
                  }
               }
            }
         }
      }
   }
}