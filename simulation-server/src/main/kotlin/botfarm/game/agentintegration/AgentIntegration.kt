package botfarm.game.agentintegration

import botfarm.common.PositionComponentData
import botfarm.common.isMoving
import botfarm.common.resolvePosition
import botfarm.engine.simulation.AlertMode
import botfarm.engine.simulation.CoroutineSystemContext
import botfarm.engine.simulation.Entity
import botfarm.engine.simulation.EntityComponent
import botfarm.game.GameSimulation
import botfarm.game.scripting.JavaScriptCodeSerialization
import botfarm.game.scripting.UnwindScriptThreadThrowable
import botfarm.game.scripting.jsdata.*
import botfarm.game.components.*
import botfarm.game.config.EquipmentSlot
import botfarm.game.config.ItemConfig
import botfarmshared.engine.apidata.EntityId
import botfarmshared.game.GameConstants
import botfarmshared.game.GameSimulationInfo
import botfarmshared.game.apidata.*
import botfarmshared.misc.buildShortRandomIdentifier
import botfarmshared.misc.getCurrentUnixTimeSeconds
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.Source
import kotlin.concurrent.thread

class AgentJavaScriptRuntime(
   val agentJavaScriptApi: AgentJavaScriptApi
) {
   val javaScriptContext: Context =
      Context.newBuilder("js")
         .option("js.strict", "true")
         .build()

   val bindings = this.javaScriptContext.getBindings("js").also {
      it.putMember("api", this.agentJavaScriptApi)
   }

   init {
      val sourceName = "scripted-agent-runtime.js"

      val runtimeSource =
         this::class.java.getResource("/$sourceName")?.readText()
            ?: throw Exception("Scripted agent runtime JavaScript resource not found")

      val javaScriptSource = Source.newBuilder("js", runtimeSource, sourceName).build()
      this.javaScriptContext.eval(javaScriptSource)
   }
}

class AgentIntegration(
   val simulation: GameSimulation,
   val entity: Entity,
   val agentType: String,
   val agentId: AgentId
) {
   private val allObservedMessageIds = mutableSetOf<String>()
   private val startedActionUniqueIds = mutableSetOf<String>()
   private val agentControlledComponent = this.entity.getComponent<AgentControlledComponentData>()
   private val actionResultsByActionUniqueId = mutableMapOf<String, ActionResult>()

   private var mostRecentSyncInput: AgentSyncInput? = null

   // Prevent new agents from seeing events from before they are started
   private var previousNewEventCheckSimulationTime = this.simulation.getCurrentSimulationTime()

   private var pendingObservations = PendingObservations()

   private var activeAction: Action? = null
   private val pendingActions = mutableListOf<Action>()

   private val agentJavaScriptApi = AgentJavaScriptApi(this)

   class RunningScript(
      val thread: Thread,
      val agentJavaScriptApi: AgentJavaScriptApi,
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
      callback: (AutoInteractType) -> Unit = {}
   ) {
      val simulation = this.simulation
      val entity = this.entity

      simulation.autoInteractWithEntity(
         entity = entity,
         targetEntity = targetEntity,
         callback = callback
      )
   }

   fun speak(whatToSay: String, reason: String? = null) {
      val simulation = this.simulation

      synchronized(simulation) {
         this.pendingObservations.selfSpokenMessages.add(
            SelfSpokenMessage(
               message = whatToSay,
               reason = reason,
               location = this.entity.resolvePosition(),
               time = simulation.getCurrentSimulationTime()
            )
         )

         simulation.addCharacterMessage(
            entity = this.entity,
            message = whatToSay
         )
      }
   }

   private fun recordThought(thought: String, reason: String? = null) {
      val simulation = this.simulation

      synchronized(simulation) {
         this.pendingObservations.selfThoughts.add(
            SelfThought(
               thought = thought,
               reason = reason,
               location = this.entity.resolvePosition(),
               time = simulation.getCurrentSimulationTime()
            )
         )

         simulation.broadcastAlertAsGameMessage(
            message = "Agent had thought: $thought"
         )
      }
   }

   suspend fun syncWithAgent(
      coroutineSystemContext: CoroutineSystemContext,
      simulation: GameSimulation,
      syncId: String
   ) {
      val entity = this.entity
      val agentId = this.agentId

      val agentServerIntegration = simulation.agentService

      val simulationTime = simulation.getCurrentSimulationTime()

      coroutineSystemContext.unwindIfNeeded()

      val newObservationsForInput = this.pendingObservations.toObservations()
      this.pendingObservations = PendingObservations()


      val agentControlledComponent = this.agentControlledComponent

      if (simulation.shouldPauseAi) {
         agentControlledComponent.modifyData {
            it.copy(
               agentIntegrationStatus = "paused",
               agentStatus = null,
               agentError = null,
               wasRateLimited = false
            )
         }

         return
      }


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
            observationDistance = agentControlledComponent.data.observationDistance,
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
            gameConstants = GameConstants,
            selfInfo = selfInfo,
            newObservations = newObservationsForInput,
            agentTypeScriptInterfaceString = this.agentTypeScriptInterfaceString,
            mostRecentCompletedScriptId = this.mostRecentCompletedScriptId
         )
      }

      agentControlledComponent.modifyData {
         it.copy(
            agentIntegrationStatus = "waiting_for_agent",
            totalRemoteAgentRequests = it.totalRemoteAgentRequests + 1,
            agentError = null
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

            synchronized(simulation) {
               agentControlledComponent.modifyData {
                  it.copy(
                     agentError = "Exception while handling agent step result (errorId = $errorId)"
                  )
               }
            }
         }
      }
   }

   fun recordObservationsForAgent() {
      val entity = this.entity
      val simulation = this.simulation
      val agentControlledComponent = this.agentControlledComponent

      val positionComponent = entity.getComponent<PositionComponentData>()

      val simulationTimeForStep: Double

      synchronized(simulation) {
         val observationDistance = agentControlledComponent.data.observationDistance

         simulationTimeForStep = simulation.getCurrentSimulationTime()
         val currentLocation = positionComponent.data.positionAnimation.resolve(simulationTimeForStep)

         simulation.entities.forEach { otherEntity ->
            val otherCharacterComponentData = otherEntity.getComponentOrNull<CharacterComponentData>()?.data
            val otherPositionComponent = otherEntity.getComponentOrNull<PositionComponentData>()

            if (otherEntity != entity && otherPositionComponent != null) {
               val otherPosition = otherPositionComponent.data.positionAnimation.resolve(simulationTimeForStep)

               val distance = otherPosition.distance(currentLocation)

               if (distance <= observationDistance) {
                  this.pendingObservations.entitiesById[otherEntity.entityId] = buildEntityInfoForAgent(
                     entity = otherEntity,
                     simulationTime = simulationTimeForStep
                  )

                  if (otherCharacterComponentData != null) {
                     val newMessages = otherCharacterComponentData.recentSpokenMessages.filter {
                        val messageAge = simulation.simulationTime - it.sentSimulationTime
                        messageAge < 15.0 && !this.allObservedMessageIds.contains(it.messageId)
                     }

                     newMessages.forEach { newMessage ->
                        println("Adding spoken message observation: " + newMessage.message)
                        this.allObservedMessageIds.add(newMessage.messageId)

                        this.pendingObservations.spokenMessages.add(
                           ObservedSpokenMessage(
                              messageId = newMessage.messageId,
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
         }

         val activityStream = simulation.getActivityStream()

         val newActivityStreamEntries = activityStream.filter { activityStreamEntry ->
            activityStreamEntry.time > this.previousNewEventCheckSimulationTime &&
                    activityStreamEntry.shouldReportToAi
         }

         this.pendingObservations.activityStreamEntries.addAll(newActivityStreamEntries.map {
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

         this.previousNewEventCheckSimulationTime = simulationTimeForStep
      }
   }

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

      agentControlledComponent.modifyData {
         it.copy(
            agentRemoteDebugInfo = agentSyncOutput.debugInfo ?: it.agentRemoteDebugInfo,
            agentStatus = agentSyncOutput.agentStatus ?: it.agentStatus,
            wasRateLimited = agentSyncOutput.wasRateLimited,
            statusDuration = agentSyncOutput.statusDuration,
            statusStartUnixTime = agentSyncOutput.statusStartUnixTime
         )
      }

      agentSyncOutput.promptUsages.forEach {
         updateComponentModelUsage(it, agentControlledComponent)
      }

      if (agentSyncOutput.error != null) {
         simulation.broadcastAlertMessage(
            AlertMode.ConsoleError,
            "Error from remote agent: " + agentSyncOutput.error
         )

         agentControlledComponent.modifyData {
            it.copy(
               agentError = agentSyncOutput.error
            )
         }

         return
      }

      if (scriptToRun != null) {
         val scriptId = scriptToRun.scriptId
         val script = scriptToRun.script

         println("Got new script from agent ($agentId, $scriptId)")

         if (entity.isMoving) {
            simulation.startEntityMovement(
               entity = entity,
               endPoint = entity.resolvePosition()
            )
         }

         this.activeAction = null
         this.pendingActions.clear()

         val threadStateLock: Any = this

         val previousRunningScript = synchronized(threadStateLock) {
            val runningScript = this.runningScript
            runningScript?.agentJavaScriptApi?.shouldEndScript = true
            runningScript
         }

         if (previousRunningScript != null) {
            if (previousRunningScript.thread.isAlive) {
               println("Agent script thread exists and is active, so attempting to end now ($scriptId)")

               val waitStartTime = getCurrentUnixTimeSeconds()
               val timeout = 5.0

               while (true) {
                  val waitedTime = getCurrentUnixTimeSeconds() - waitStartTime
                  if (!previousRunningScript.thread.isAlive) {
                     println("Agent script thread has now ended after waiting $waitedTime seconds ($scriptId)")
                     break
                  } else if (waitedTime > timeout) {
                     println("Agent script thread did not end after $timeout seconds, so interrupting ($scriptId)")
                     previousRunningScript.thread.interrupt()
                     break
                  } else {
                     coroutineSystemContext.delay(200)
                  }
               }

               coroutineSystemContext.delay(200)
            } else {
               println("Agent script thread exists, but is not active, so not interrupting ($scriptId)")
            }
         } else {
            println("No previous agent script thread exists, starting a new one now ($scriptId)")
         }

         synchronized(threadStateLock) {
            this.runningScript = null
         }

         val wrappedResponseScript = """
         (function() {
         $script
         })()
      """.trimIndent()

         agentControlledComponent.modifyData {
            it.copy(
               executingScriptId = scriptId,
               executingScript = script,
               scriptExecutionError = null,
               currentActionTimeline = null
            )
         }

         val craftingRecipeInfos = simulation.getCraftingRecipeInfos(
            crafterEntity = entity
         )

         synchronized(threadStateLock) {
            val agentJavaScriptApi = this.agentJavaScriptApi

            val newThread = thread {
               println("In activeScriptThread")
               agentJavaScriptApi.notifyJavaScriptThreadStarted()
               val bindings = this.agentJavaScriptRuntime.bindings

               val jsConversionContext = JsConversionContext(bindings)

               for (craftingRecipeInfo in craftingRecipeInfos) {
                  val variableName = "crafting_recipe_${craftingRecipeInfo.itemConfigKey.replace("-", "_")}"

                  bindings.putMember(variableName, agentJavaScriptApi.buildJsCraftingRecipe(
                     craftingRecipeInfo = craftingRecipeInfo,
                     jsConversionContext = jsConversionContext
                  ))
               }

               val inventoryItemStacks = agentJavaScriptApi.getCurrentInventoryItemStacks()

               for (jsInventoryItemStackInfo in inventoryItemStacks.values) {
                  val stackIndex = jsInventoryItemStackInfo.stackIndex

                  val itemStackVariableName = "inventory_item_${stackIndex}"

                  bindings.putMember(itemStackVariableName, jsInventoryItemStackInfo)
               }

               val selfJsEntity = agentJavaScriptApi.buildJsEntity(agentSyncInput.selfInfo.entityInfoWrapper.entityInfo)

               bindings.putMember("self", selfJsEntity)

               for (entityInfoWrapper in agentSyncInput.newObservations.entitiesById.values) {
                  val entityInfo = entityInfoWrapper.entityInfo
                  val jsEntity = agentJavaScriptApi.buildJsEntity(entityInfo)

                  val entityVariableName = entityInfoWrapper.javaScriptVariableName

                  bindings.putMember(entityVariableName, jsEntity)
               }

               val sourceName = "agent"
               val javaScriptSource = Source.newBuilder("js", wrappedResponseScript, sourceName).build()

               try {
                  println("Calling javaScriptContext.eval")
                  this.agentJavaScriptRuntime.javaScriptContext.eval(javaScriptSource)

                  synchronized(threadStateLock) {
                     println("Agent script thread complete: $scriptId")
                     if (this.runningScript?.scriptToRun?.scriptId == scriptId) {
                        this.runningScript = null
                        this.mostRecentCompletedScriptId = scriptId
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
                        }
                     }
                  } else {
                     println("Polyglot exception evaluating agent JavaScript ($scriptId): " + polyglotException.stackTraceToString() + "\nAgent script was: $wrappedResponseScript")

                     synchronized(threadStateLock) {
                        if (this.runningScript?.scriptToRun?.scriptId == scriptId) {
                           this.runningScript = null
                           this.mostRecentCompletedScriptId = scriptId

//                           synchronized(simulation) {
//                              agentControlledComponent.modifyData {
//                                 it.copy(
//                                    scriptExecutionError = polyglotException.stackTraceToString()
//                                 )
//                              }
//
//                              this.pendingObservations.scriptExecutionErrors.add(
//                                 ScriptExecutionError(
//                                    scriptId = scriptId,
//                                    error = polyglotException.stackTraceToString()
//                                 )
//                              )
//                           }
                        }
                     }
                  }
               } catch (exception: Exception) {
                  println("Exception evaluating agent JavaScript ($scriptId): " + exception.stackTraceToString() + "\nAgent script was: $wrappedResponseScript")

                  synchronized(threadStateLock) {
                     if (this.runningScript?.scriptToRun?.scriptId == scriptId) {
                        this.runningScript = null
                        this.mostRecentCompletedScriptId = scriptId


//                        synchronized(simulation) {
//                           this.pendingObservations.scriptExecutionErrors.add(
//                              ScriptExecutionError(
//                                 scriptId = scriptId,
//                                 error = exception.stackTraceToString()
//                              )
//                           )
//
//                           agentControlledComponent.modifyData {
//                              it.copy(
//                                 scriptExecutionError = exception.stackTraceToString()
//                              )
//                           }
//                        }
                     }
                  }
               }
            }

            this.runningScript = RunningScript(
               thread = newThread,
               scriptToRun = scriptToRun,
               agentJavaScriptApi = agentJavaScriptApi
            )
         }
      } else if (actions != null) {
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
         this.speak(speak)
         addActionResult()
      }

      if (recordThought != null) {
         this.recordThought(recordThought)
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

         val craftItemResult = simulation.craftItem(
            entity = entity,
            itemConfigKey = itemConfigKey
         )

         if (craftItemResult != GameSimulation.CraftItemResult.Success) {
            simulation.broadcastAlertAsGameMessage("Unable to use craft item for agent ${itemConfigKey} ($debugInfo): actionUniqueId = $actionUniqueId, result ${craftItemResult.name}")
         }

         this.pendingObservations.craftItemActionRecords.add(
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

            this.pendingObservations.actionOnInventoryItemActionRecords.add(
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

         this.pendingObservations.actionOnInventoryItemActionRecords.add(
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

         this.pendingObservations.actionOnInventoryItemActionRecords.add(
            ActionOnInventoryItemRecord(
               startedAtTime = simulationTimeForStep,
               reason = reason,
               itemConfigKey = itemConfigKey,
               amount = amount,
               actionId = "dropItem"
            )
         )
      }

      fun autoInteractWithEntity(
         targetEntityId: EntityId,
         expectedAutoInteractType: AutoInteractType
      ) {
         // jshmrsn: Currently, all actions on entities are executed by the single autoInteractWithEntity
         // The provided actionId is currently just a hint for agents to keep track of their intended interaction
         val targetEntity = simulation.getEntityOrNull(targetEntityId)

         if (targetEntity == null) {
            val destroyedTargetEntity = simulation.getDestroyedEntityOrNull(targetEntityId)
            if (destroyedTargetEntity != null) {
               println("Can't find entity for action from AI (but was destroyed AFTER prompt was generated) ($debugInfo): $expectedAutoInteractType, actionUniqueId = $actionUniqueId, targetEntityId = $targetEntityId")
            } else {
               simulation.broadcastAlertAsGameMessage("Can't find entity for action from AI ($debugInfo): $expectedAutoInteractType, actionUniqueId = $actionUniqueId, targetEntityId = $targetEntityId")
            }
            addActionResult()
         } else {
            this.autoInteractWithEntity(targetEntity) { autoInteractResult ->
               println("Agent autoInteractResult ($expectedAutoInteractType): " + autoInteractResult.name)

               this.pendingObservations.actionOnEntityRecords.add(
                  ActionOnEntityRecord(
                     startedAtTime = simulationTimeForStep,
                     targetEntityId = targetEntityId,
                     autoInteractType = autoInteractResult,
                     reason = reason
                  )
               )

               addActionResult()
            }
         }
      }

      if (pickUpEntity != null) {
         autoInteractWithEntity(
            targetEntityId = pickUpEntity.targetEntityId,
            expectedAutoInteractType = AutoInteractType.PickUp
         )
      }

      if (useEquippedToolItemOnEntity != null) {
         autoInteractWithEntity(
            targetEntityId = useEquippedToolItemOnEntity.targetEntityId,
            expectedAutoInteractType = AutoInteractType.AttackWithEquippedTool
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