package botfarm.game.agentintegration

import botfarm.common.isMoving
import botfarm.common.resolvePosition
import botfarm.engine.simulation.AlertMode
import botfarm.engine.simulation.CoroutineSystemContext
import botfarm.game.components.AgentControlledComponentData
import botfarm.game.GameSimulation
import botfarmshared.game.apidata.*
import botfarm.engine.simulation.Entity
import botfarm.engine.simulation.EntityComponent
import botfarm.game.codeexecution.UnwindScriptThreadThrowable
import botfarm.game.codeexecution.jsdata.JsConversionContext
import botfarmshared.misc.getCurrentUnixTimeSeconds
import kotlinx.coroutines.delay
import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.Source
import kotlin.concurrent.thread

suspend fun handleAgentSyncOutput(
   syncId: String,
   coroutineSystemContext: CoroutineSystemContext,
   agentSyncInput: AgentSyncInput,
   agentSyncOutput: AgentSyncOutput,
   agentControlledComponent: EntityComponent<AgentControlledComponentData>,
   simulation: GameSimulation,
   state: AgentSyncState,
   entity: Entity
) {
   val actions = agentSyncOutput.actions
   val scriptToRun = agentSyncOutput.scriptToRun

   val agentId = state.agentId

   synchronized(simulation) {
      agentControlledComponent.modifyData {
         it.copy(
            agentRemoteDebugInfo = agentSyncOutput.debugInfo ?: it.agentRemoteDebugInfo,
            agentStatus = agentSyncOutput.agentStatus ?: it.agentStatus,
            wasRateLimited = agentSyncOutput.wasRateLimited,
            statusDuration = agentSyncOutput.statusDuration,
            statusStartUnixTime = agentSyncOutput.statusStartUnixTime
         )
      }
   }

   agentSyncOutput.promptUsages.forEach {
      synchronized(simulation) {
         updateComponentModelUsage(it, agentControlledComponent)
      }
   }

   if (agentSyncOutput.error != null) {
      synchronized(simulation) {
         simulation.broadcastAlertMessage(AlertMode.ConsoleError, "Error from remote agent: " + agentSyncOutput.error)

         agentControlledComponent.modifyData {
            it.copy(
               agentError = agentSyncOutput.error
            )
         }
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

      state.activeAction = null
      state.pendingActions.clear()

      val previousThread = state.activeScriptThread

      state.agentJavaScriptApi.shouldEndScript = true

      if (previousThread != null) {
         if (previousThread.isAlive) {
            println("Agent script thread exists and is active, so attempting to end now ($scriptId)")

            val waitStartTime = getCurrentUnixTimeSeconds()
            val timeout = 5.0

            while (true) {
               val waitedTime = getCurrentUnixTimeSeconds() - waitStartTime
               if (!previousThread.isAlive) {
                  println("Agent script thread has now ended after waiting $waitedTime seconds ($scriptId)")
                  break
               } else if (waitedTime > timeout) {
                  println("Agent script thread did not end after $timeout seconds, so interrupting ($scriptId)")
                  previousThread.interrupt()
                  break
               } else {
                  coroutineSystemContext.delay(200)
               }
            }

            coroutineSystemContext.delay(200)
         } else {
            println("Agent script thread exists, but is not active, so not interrupting ($scriptId)")
         }
      }

      val wrappedResponseScript = """
         (function() {
         $script
         })()
      """.trimIndent()

      val agentJavaScriptApi = state.agentJavaScriptApi

      synchronized(simulation) {
         state.activeScriptToRun = scriptToRun
         state.agentJavaScriptApi.shouldEndScript = false

         agentControlledComponent.modifyData {
            it.copy(
               executingScriptId = scriptId,
               executingScript = script,
               scriptExecutionError = null,
               currentActionTimeline = null
            )
         }

         val bindingsToAdd = mutableListOf<Pair<String, (conversionContext: JsConversionContext) -> Any>>()

         val craftingRecipeInfos = simulation.getCraftingRecipeInfos(
            crafterEntity = entity
         )

         for (craftingRecipeInfo in craftingRecipeInfos) {
            val variableName = "crafting_recipe_${craftingRecipeInfo.itemConfigKey.replace("-", "_")}"

            bindingsToAdd.add(variableName to {
               agentJavaScriptApi.buildJsCraftingRecipe(
                  craftingRecipeInfo = craftingRecipeInfo,
                  jsConversionContext = it
               )
            })
         }

         val inventoryItemStacks = agentJavaScriptApi.getCurrentInventoryItemStacks()

         for (jsInventoryItemStackInfo in inventoryItemStacks.values) {
            val stackIndex = jsInventoryItemStackInfo.stackIndex

            val itemStackVariableName = "inventory_item_${stackIndex}"

            bindingsToAdd.add(itemStackVariableName to {
               jsInventoryItemStackInfo
            })
         }

         val selfJsEntity = agentJavaScriptApi.buildJsEntity(agentSyncInput.selfInfo.entityInfoWrapper.entityInfo)

         bindingsToAdd.add("self" to {
            selfJsEntity
         })

         for (entityInfoWrapper in agentSyncInput.newObservations.entitiesById.values) {
            val entityInfo = entityInfoWrapper.entityInfo
            val jsEntity = agentJavaScriptApi.buildJsEntity(entityInfo)

            val entityVariableName = entityInfoWrapper.javaScriptVariableName

            bindingsToAdd.add(entityVariableName to {
               jsEntity
            })
         }

         state.activeScriptThread = thread {
            val bindings = state.javaScriptContext.getBindings("js")

            val jsConversionContext = JsConversionContext(bindings)

            bindingsToAdd.forEach {
               bindings.putMember(it.first, it.second(jsConversionContext))
            }

            val sourceName = "agent"
            val javaScriptSource = Source.newBuilder("js", wrappedResponseScript, sourceName).build()

            try {
               state.javaScriptContext.eval(javaScriptSource)
               println("Agent script thread complete: $scriptId")
               synchronized(simulation) {
                  if (state.activeScriptToRun?.scriptId == scriptId) {
                     state.activeScriptToRun = null
                     state.mostRecentCompletedScriptId = scriptId
                  }
               }
            } catch (unwindScriptThreadThrowable: UnwindScriptThreadThrowable) {
               println("Got unwind agent script thread throwable ($scriptId): ${unwindScriptThreadThrowable.reason}")
               synchronized(simulation) {
                  if (state.activeScriptToRun?.scriptId == scriptId) {
                     state.activeScriptToRun = null
                     state.mostRecentCompletedScriptId = scriptId
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

                  synchronized(simulation) {
                     if (state.activeScriptToRun?.scriptId == scriptId) {
                        state.activeScriptToRun = null
                        state.mostRecentCompletedScriptId = scriptId
                     }
                  }
               } else {
                  println("Polyglot exception evaluating agent JavaScript ($scriptId): " + polyglotException.stackTraceToString() + "\nAgent script was: $wrappedResponseScript")

                  synchronized(simulation) {
                     if (state.activeScriptToRun?.scriptId == scriptId) {
                        state.activeScriptToRun = null
                        state.mostRecentCompletedScriptId = scriptId
                     }

                     agentControlledComponent.modifyData {
                        it.copy(
                           scriptExecutionError = polyglotException.stackTraceToString()
                        )
                     }

                     state.mutableObservations.scriptExecutionErrors.add(ScriptExecutionError(
                        scriptId = scriptId,
                        error = polyglotException.stackTraceToString()
                     ))
                  }
               }
            } catch (exception: Exception) {
               println("Exception evaluating agent JavaScript ($scriptId): " + exception.stackTraceToString() + "\nAgent script was: $wrappedResponseScript")

               synchronized(simulation) {
                  if (state.activeScriptToRun?.scriptId == scriptId) {
                     state.activeScriptToRun = null
                     state.mostRecentCompletedScriptId = scriptId
                  }

                  state.mutableObservations.scriptExecutionErrors.add(ScriptExecutionError(
                     scriptId = scriptId,
                     error = exception.stackTraceToString()
                  ))

                  agentControlledComponent.modifyData {
                     it.copy(
                        scriptExecutionError = exception.stackTraceToString()
                     )
                  }
               }
            }
         }
      }
   }

   if (actions != null) {
      synchronized(simulation) {
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
      }

      state.activeAction = null
      state.pendingActions.clear()
      state.pendingActions.addAll(agentSyncOutput.actions)
   }
}

