package botfarm.agentserver

import botfarm.agentserver.agents.default.DefaultAgent
import botfarm.apidata.AgentStepInputs

fun buildAgentForType(
   agentType: String,
   initialInputs: AgentStepInputs,
   agentContainer: AgentContainer
): Agent {
   if (agentType.startsWith("default")) {
      return DefaultAgent(
         agentContainer = agentContainer,
         initialInputs = initialInputs,
         useGpt4 = agentType.contains("gpt4") || agentType.contains("gpt-4"),
         useFunctionCalling = agentType.contains("func")
      )
//   } else if (agentType == "gpt-4") {
//      return Gpt_35_Instruct_Agent(
//         agentContainer = agentContainer,
//         initialInputs = initialInputs
//      )
   } else {
      throw Exception("Unknown agent type: $agentType")
   }
}