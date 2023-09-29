package botfarm.agentserver

import botfarmshared.game.apidata.AgentStepInputs
import botfarmshared.game.apidata.AgentStepResult
import com.aallam.openai.client.OpenAI

abstract class Agent {
   abstract val agentContainer: AgentContainer
   abstract val initialInputs: AgentStepInputs

   val agentId
      get() = this.initialInputs.selfInfo.agentId

   abstract fun consumeInputs(
      inputs: AgentStepInputs
   )

   abstract suspend fun step(
      inputs: AgentStepInputs,
      openAI: OpenAI,
      provideResult: (AgentStepResult) -> Unit
   )
}