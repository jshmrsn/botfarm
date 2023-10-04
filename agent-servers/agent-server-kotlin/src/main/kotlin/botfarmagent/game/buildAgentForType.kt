package botfarmagent.game

import botfarmagent.game.agents.jsonaction.JsonActionAgent
import botfarmagent.game.agents.codeexecution.CodeExecutionAgent

fun buildAgentForType(
   agentContext: AgentContext
): Agent {
   val agentType = agentContext.agentType

   if (agentType.startsWith("json")) {
      return JsonActionAgent(
         agentContext = agentContext,
         useGpt4 = !agentType.contains("gpt3"),
         useFunctionCalling = agentType.contains("func")
      )
   } else if (agentType.startsWith("code")) {
      return CodeExecutionAgent(
         context = agentContext,
         useGpt4 = !agentType.contains("gpt3")
      )
   } else {
      throw Exception("Unknown agent type: $agentType")
   }
}