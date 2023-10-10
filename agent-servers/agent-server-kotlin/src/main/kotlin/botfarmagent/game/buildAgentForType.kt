package botfarmagent.game

import botfarmagent.game.agents.jsonaction.JsonActionAgent
import botfarmagent.game.agents.scriptexecution.ScriptExecutionAgent

fun buildAgentForType(
   agentContext: AgentContext
): Agent {
   val agentType = agentContext.agentType

   if (agentType.startsWith("json")) {
      return JsonActionAgent(
         agentContext = agentContext,
         useGpt4 = !agentType.contains("gpt3"),
         useFunctionCalling = true
      )
   } else if (agentType.startsWith("script")) {
      return ScriptExecutionAgent(
         context = agentContext,
         shouldUseGpt4 = !agentType.contains("-gpt3"),
         shouldUseMockResponses = agentType.contains("-mock")
      )
   } else {
      throw Exception("Unknown agent type: $agentType")
   }
}