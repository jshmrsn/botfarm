package botfarmagent.game

import botfarmshared.game.apidata.AgentId
import botfarmshared.game.apidata.AgentSyncInputs
import botfarmshared.game.apidata.AgentStepResult
import com.aallam.openai.client.OpenAI

class AgentContext(
   val agentContainer: AgentContainer,
   val initialInputs: AgentSyncInputs,
   val agentId: AgentId,
   val openAI: OpenAI,
   val agentType: String
)

abstract class Agent(
   val context: AgentContext
) {
   val agentContainer = this.context.agentContainer
   val initialInputs = this.context.initialInputs
   var mostRecentInputs = this.context.initialInputs
      private set
   val agentId = this.context.agentId

   private val pendingInputsList = mutableListOf<AgentSyncInputs>()
   private val pendingResultsList = mutableListOf<AgentStepResult>()

   fun addPendingInput(inputs: AgentSyncInputs) {
      synchronized(this) {
         this.pendingInputsList.add(inputs)
      }
   }

   fun consumePendingInputs(): List<AgentSyncInputs> {
      synchronized(this) {
         val copy = this.pendingInputsList.toList()
         this.pendingInputsList.clear()
         return copy
      }
   }

   fun consumePendingResults(): List<AgentStepResult> {
      return synchronized(this) {
         val copy = this.pendingResultsList.toList()
         this.pendingResultsList.clear()
         copy
      }
   }

   fun notifyWillConsumeInputs(
      inputs: AgentSyncInputs
   ) {
      this.mostRecentInputs = inputs
   }

   abstract fun consumeInputs(
      inputs: AgentSyncInputs
   )

   abstract suspend fun step(
      inputs: AgentSyncInputs
   )

   fun addPendingResult(stepResult: AgentStepResult) {
      synchronized(this) {
         this.pendingResultsList.add(stepResult)
      }
   }
}