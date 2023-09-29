package botfarmagent.game

import botfarmshared.game.apidata.AgentSyncInputs
import botfarmshared.game.apidata.AgentStepResult
import com.aallam.openai.client.OpenAI

abstract class Agent {
   abstract val agentContainer: AgentContainer
   abstract val initialInputs: AgentSyncInputs

   val agentId
      get() = this.initialInputs.selfInfo.agentId

   abstract fun consumeInputs(
      inputs: AgentSyncInputs
   )

   abstract suspend fun step(
      inputs: AgentSyncInputs,
      openAI: OpenAI,
      provideResult: (AgentStepResult) -> Unit
   )
}