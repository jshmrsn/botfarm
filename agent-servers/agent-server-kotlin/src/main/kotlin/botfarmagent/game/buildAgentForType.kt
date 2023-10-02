package botfarmagent.game

import botfarmagent.game.agents.default.DefaultAgent
import botfarmagent.game.agents.scripted.ScriptedAgent
import botfarmshared.game.apidata.AgentSyncInputs

fun buildAgentForType(
   agentType: String,
   initialInputs: AgentSyncInputs,
   agentContainer: AgentContainer
): Agent {
   if (agentType.startsWith("default")) {
      return DefaultAgent(
         agentContainer = agentContainer,
         initialInputs = initialInputs,
         useGpt4 = agentType.contains("gpt4") || agentType.contains("gpt-4"),
         useFunctionCalling = agentType.contains("func")
      )
   } else if (agentType.startsWith("scripted")) {
      return ScriptedAgent(
         agentContainer = agentContainer,
         initialInputs = initialInputs,
         useGpt4 = agentType.contains("gpt4") || agentType.contains("gpt-4"),
         useFunctionCalling = agentType.contains("func")
      )
   } else {
      throw Exception("Unknown agent type: $agentType")
   }
}