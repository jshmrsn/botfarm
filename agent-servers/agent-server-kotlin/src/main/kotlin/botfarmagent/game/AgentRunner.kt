package botfarm.agentserver

import botfarmshared.game.apidata.AgentStepInputs
import botfarmshared.game.apidata.AgentStepResult
import botfarmshared.misc.buildShortRandomString
import kotlinx.coroutines.*

class AgentRunner(
   val agent: Agent,
   val agentContainer: AgentContainer,
   val coroutineDispatcher: CoroutineDispatcher
) {
   val simulationId = this.agent.initialInputs
   val agentId = this.agent.agentId

   private val pendingInputsList = mutableListOf<AgentStepInputs>()
   private val pendingResultsList = mutableListOf<AgentStepResult>()


   private var job: Job? = null

   fun addPendingInput(inputs: AgentStepInputs) {
      synchronized(this) {
         this.pendingInputsList.add(inputs)
      }
   }

   fun terminate() {
      synchronized(this) {
         this.job?.cancel()
         this.job = null
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
               val inputsList = synchronized(self) {
                  val copy = self.pendingInputsList.toList()
                  self.pendingInputsList.clear()
                  copy
               }

               inputsList.forEach { inputs ->
                  agent.consumeInputs(
                     inputs = inputs
                  )
               }

               val lastInput = inputsList.lastOrNull()

               if (lastInput != null) {
                  try {
                     agent.step(
                        inputs = lastInput,
                        openAI = self.agentContainer.openAI,
                        provideResult = {
                           synchronized(this) {
                              self.pendingResultsList.add(it)
                           }
                        }
                     )
                  } catch (exception: Exception) {
                     val stepId = lastInput.stepId
                     val errorId = buildShortRandomString()
                     println("RemoteAgentServer: Exception while running agent step (simulationId = $simulationId agentId = $agentId, stepId = $stepId, errorId = $errorId):\n${exception.stackTraceToString()}")

                     synchronized(this) {
                        self.pendingResultsList.add(
                           AgentStepResult(
                           error = "Error on agent server: $errorId"
                        )
                        )
                     }
                  }
               }

               delay(250)
            }
         } catch (exception: Exception) {
            println("Exception in coroutine system logic for agent: simulationId = $simulationId, agentId = $agentId\nException was : $exception")
         }
      }
   }

   fun consumePendingResults(): List<AgentStepResult> {
//      println("consumePendingResults: ${this.agent.agentId}")
      return synchronized(this) {
         val copy = this.pendingResultsList.toList()
         this.pendingResultsList.clear()
         copy
      }
   }
}