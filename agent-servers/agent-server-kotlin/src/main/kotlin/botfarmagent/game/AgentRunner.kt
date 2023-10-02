package botfarmagent.game

import botfarmshared.game.apidata.AgentSyncInputs
import botfarmshared.game.apidata.AgentStepResult
import botfarmshared.misc.buildShortRandomString
import botfarmshared.misc.getCurrentUnixTimeSeconds
import kotlinx.coroutines.*

class AgentRunner(
   val agent: Agent,
   val agentContainer: AgentContainer,
   val coroutineDispatcher: CoroutineDispatcher
) {
   val simulationId = this.agent.initialInputs
   val agentId = this.agent.agentId

   private var job: Job? = null
   private var shouldTerminate = false
   private var lastSyncUnixTimeSeconds = getCurrentUnixTimeSeconds()

   companion object {
      val unusedAgentCleanupMinutes = 15
   }

   fun addPendingInput(inputs: AgentSyncInputs) {
      synchronized(this) {
         this.lastSyncUnixTimeSeconds = getCurrentUnixTimeSeconds()
         this.agent.addPendingInput(inputs)
      }
   }

   fun terminate() {
      synchronized(this) {
         this.job?.cancel()
         this.job = null
      }
   }

   fun softTerminate() {
      synchronized(this) {
         this.shouldTerminate = true
      }
   }

   fun startRunningInBackground() {
      val agentId = this.agentId
      val simulationId = this.simulationId
      val agent = this.agent
      val self = this

      this.job = CoroutineScope(this.coroutineDispatcher).launch {
         try {
            while (true) {
               if (self.shouldTerminate) {
                  println("Agent runner breaking loop because shouldTerminate is true: ${self.agentId}")
                  break
               }

               val secondsSinceLastSync = getCurrentUnixTimeSeconds() - self.lastSyncUnixTimeSeconds

               if (secondsSinceLastSync > 60 * Companion.unusedAgentCleanupMinutes) {
                  println("Agent runner breaking loop because time since last sync exceeded cleanup time: ${self.agentId} ${secondsSinceLastSync.toInt()}s")
                  break
               }

               val inputsList = agent.consumePendingInputs()

               inputsList.forEach { inputs ->
                  synchronized(agent) {
                     agent.notifyWillConsumeInputs(inputs)
                     agent.consumeInputs(
                        inputs = inputs
                     )
                  }
               }

               val lastInput = inputsList.lastOrNull()

               if (lastInput != null) {
                  try {
                     agent.step(
                        inputs = lastInput
                     )
                  } catch (exception: Exception) {
                     val syncId = lastInput.syncId
                     val errorId = buildShortRandomString()
                     println("RemoteAgentServer: Exception while running agent step (simulationId = $simulationId agentId = $agentId, syncId = $syncId, errorId = $errorId):\n${exception.stackTraceToString()}")

                     agent.addPendingResult(
                        AgentStepResult(
                           error = "Error on agent server: $errorId"
                        )
                     )
                  }
               }

               delay(250)
            }

            println("Agent runner loop has soft terminated: ${self.agentId}")
         } catch (exception: Exception) {
            println("Exception in coroutine system logic for agent: simulationId = $simulationId, agentId = $agentId\nException was : $exception")
         }
      }
   }

   fun consumePendingResults(): List<AgentStepResult> {
      return this.agent.consumePendingResults()
   }
}