package botfarm.game.agentintegration

import botfarm.common.isMoving
import botfarm.common.resolvePosition
import botfarm.engine.simulation.AlertMode
import botfarm.game.components.AgentComponentData
import botfarm.game.GameSimulation
import botfarmshared.game.apidata.*
import botfarm.engine.simulation.Entity
import botfarm.engine.simulation.EntityComponent
import botfarm.game.codeexecution.JavaScriptCodeSerialization
import botfarm.game.codeexecution.UnwindScriptThreadThrowable
import botfarm.game.codeexecution.jsdata.JsConversionContext
import botfarmshared.misc.getCurrentUnixTimeSeconds
import kotlinx.coroutines.delay
import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.Source
import kotlin.concurrent.thread

suspend fun handleAgentSyncOutput(
   syncId: String,
   agentSyncInput: AgentSyncInput,
   agentSyncOutput: AgentSyncOutput,
   agentComponent: EntityComponent<AgentComponentData>,
   simulation: GameSimulation,
   state: AgentSyncState,
   entity: Entity
) {
   val actions = agentSyncOutput.actions
   val script = agentSyncOutput.script
   val agentId = state.agentId

   synchronized(simulation) {
      agentComponent.modifyData {
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
         updateComponentModelUsage(it, agentComponent)
      }
   }

   if (agentSyncOutput.error != null) {
      synchronized(simulation) {
         simulation.broadcastAlertMessage(AlertMode.ConsoleError, "Error from remote agent: " + agentSyncOutput.error)

         agentComponent.modifyData {
            it.copy(
               agentError = agentSyncOutput.error
            )
         }
      }

      return
   }

   if (script != null) {
      println("Got new script from agent ($agentId, $syncId)")

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
            println("Agent script thread exists and is active, so attempting to end now ($syncId)")

            val waitStartTime = getCurrentUnixTimeSeconds()
            val timeout = 5.0

            while (true) {
               val waitedTime = getCurrentUnixTimeSeconds() - waitStartTime
               if (!previousThread.isAlive) {
                  println("Agent script thread has now ended after waiting $waitedTime seconds ($syncId)")
                  break
               } else if (waitedTime > timeout) {
                  println("Agent script thread did not end after $timeout seconds, so interrupting ($syncId)")
                  previousThread.interrupt()
                  break
               } else {
                  delay(200)
               }
            }

            delay(200)
         } else {
            println("Agent script thread exists, but is not active, so not interrupting ($syncId)")
         }
      }

      val wrappedResponseScript = """
         (function() {
         $script
         })()
      """.trimIndent()

      val agentJavaScriptApi = state.agentJavaScriptApi

      synchronized(simulation) {
         state.activeScriptPromptId = syncId
         state.mostRecentScript = wrappedResponseScript
         state.agentJavaScriptApi.shouldEndScript = false

         val bindingsToAdd = mutableListOf<Pair<String, (conversionContext: JsConversionContext) -> Any>>()

         for (craftingRecipe in simulation.getCraftingRecipes()) {
            val variableName = "crafting_recipe_${craftingRecipe.itemConfigKey.replace("-", "_")}"

            bindingsToAdd.add(variableName to {
               agentJavaScriptApi.buildJsCraftingRecipe(
                  craftingRecipe = craftingRecipe,
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

         val selfJsEntity = agentJavaScriptApi.buildJsEntity(agentSyncInput.selfInfo.entityInfo)

         bindingsToAdd.add("self" to {
            selfJsEntity
         })

         for (entityInfo in agentSyncInput.newObservations.entitiesById.values) {
            val jsEntity = agentJavaScriptApi.buildJsEntity(entityInfo)

            val variableTypeName = if (entityInfo.characterInfo != null) {
               "character"
            } else if (entityInfo.itemInfo != null) {
               entityInfo.itemInfo.itemConfigKey.replace("-", "_")
            } else {
               ""
            }

            val entityVariableName = "${variableTypeName}_entity_${entityInfo.entityId.value}"

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
               println("Agent script thread complete: $syncId")
               synchronized(simulation) {
                  if (state.activeScriptPromptId == syncId) {
                     state.activeScriptPromptId = null
                  }
               }
            } catch (unwindScriptThreadThrowable: UnwindScriptThreadThrowable) {
               println("Got unwind agent script thread throwable ($syncId): ${unwindScriptThreadThrowable.reason}")
               synchronized(simulation) {
                  if (state.activeScriptPromptId == syncId) {
                     state.activeScriptPromptId = null
                  }
               }
            } catch (polyglotException: PolyglotException) {
               val hostException = polyglotException.asHostException()
               if (hostException is UnwindScriptThreadThrowable) {
                  println("Got unwind agent script thread throwable via PolyglotException ($syncId): ${hostException.reason}")

                  synchronized(simulation) {
                     if (state.activeScriptPromptId == syncId) {
                        state.activeScriptPromptId = null
                     }
                  }
               } else {
                  println("Polyglot exception evaluating agent JavaScript ($syncId): " + polyglotException.stackTraceToString() + "\nAgent script was: $wrappedResponseScript")

                  synchronized(simulation) {
//                     this.addPendingOutput(
//                        AgentSyncOutput(
//                           error = "Polyglot exception evaluating JavaScript ($syncId)",
//                           agentStatus = "script-exception"
//                        )
//                     )
                  }
               }
            } catch (exception: Exception) {
               println("Exception evaluating agent JavaScript ($syncId): " + exception.stackTraceToString() + "\nAgent script was: $wrappedResponseScript")
//               synchronized(simulation) {
//                  this.addPendingOutput(
//                     AgentSyncOutput(
//                        error = "Exception evaluating JavaScript ($syncId)",
//                        agentStatus = "script-exception"
//                     )
//                  )
//               }
            }
         }
      }
   }

   if (actions != null) {
      if (entity.isMoving) {
         simulation.startEntityMovement(
            entity = entity,
            endPoint = entity.resolvePosition()
         )
      }

      state.activeAction = null
      state.pendingActions.clear()
      state.pendingActions.addAll(agentSyncOutput.actions)
   }
}

