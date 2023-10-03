package botfarmagent.game

import botfarmagent.game.agents.legacy.LegacyAgent
import botfarmagent.game.agents.scripted.ScriptedAgent

fun buildAgentForType(
   agentContext: AgentContext
): Agent {
   val agentType = agentContext.agentType

   if (agentType.startsWith("default")) {
      return LegacyAgent(
         agentContext = agentContext,
         useGpt4 = agentType.contains("gpt4") || agentType.contains("gpt-4"),
         useFunctionCalling = agentType.contains("func")
      )
   } else if (agentType.startsWith("scripted")) {
      return ScriptedAgent(
         context = agentContext,
         useGpt4 = agentType.contains("gpt4") || agentType.contains("gpt-4"),
         useFunctionCalling = agentType.contains("func")
      )
   } else {
      throw Exception("Unknown agent type: $agentType")
   }
}