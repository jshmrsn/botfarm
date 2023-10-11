package botfarm.game.components

import botfarmshared.game.apidata.AgentId
import botfarm.engine.simulation.EntityComponentData
import botfarmshared.game.apidata.AgentStatus

data class AgentControlledComponentData(
   val agentId: AgentId,
   val corePersonality: String,
   val initialMemories: List<String> = listOf(),
   val agentType: String = defaultAgentType,
   val lastAgentResponseUnixTime: Double? = null,
   val agentStatus: AgentStatus? = null,
   val agentIntegrationStatus: String? = null,
   val agentRemoteDebugInfo: String = "",
   val currentActionTimeline: String? = null,
   val executingScriptId: String? = null,
   val executingScript: String? = null,

   val totalInputTokens: Int = 0,
   val totalOutputTokens: Int = 0,
   val totalPrompts: Int = 0,
   val totalRemoteAgentRequests: Int = 0,
   val costDollars: Double = 0.0
) : EntityComponentData() {
   companion object {
      val defaultAgentType = "script"
   }
}