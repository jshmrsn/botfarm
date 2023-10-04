package botfarmagent.game

import botfarmshared.game.apidata.AgentId
import botfarmshared.game.apidata.AgentSyncInput
import botfarmshared.game.apidata.AgentSyncOutput

class AgentContext(
   val agentContainer: AgentContainer,
   val initialSyncInput: AgentSyncInput,
   val agentId: AgentId,
   val languageModelService: LanguageModelService,
   val agentType: String
)

abstract class Agent(
   val context: AgentContext
) {
   val agentContainer = this.context.agentContainer
   val initialSyncInput = this.context.initialSyncInput
   var mostRecentSyncInput = this.context.initialSyncInput
      private set

   val agentId = this.context.agentId

   private val pendingInputs = mutableListOf<AgentSyncInput>()
   private val pendingOutputs = mutableListOf<AgentSyncOutput>()

   fun addPendingInput(input: AgentSyncInput) {
      synchronized(this) {
         this.pendingInputs.add(input)
      }
   }

   fun consumePendingInputs(): List<AgentSyncInput> {
      synchronized(this) {
         val copy = this.pendingInputs.toList()
         this.pendingInputs.clear()
         return copy
      }
   }

   fun consumePendingOutputs(): List<AgentSyncOutput> {
      return synchronized(this) {
         val copy = this.pendingOutputs.toList()
         this.pendingOutputs.clear()
         copy
      }
   }

   fun notifyWillConsumeInput(
      input: AgentSyncInput
   ) {
      this.mostRecentSyncInput = input
   }

   abstract fun consumeInput(
      input: AgentSyncInput
   )

   abstract suspend fun step(
      input: AgentSyncInput
   )

   fun addPendingOutput(output: AgentSyncOutput) {
      synchronized(this) {
         this.pendingOutputs.add(output)
      }
   }
}