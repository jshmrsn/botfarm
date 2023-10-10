package botfarm.game.components

import botfarmshared.game.apidata.AgentId
import botfarm.engine.simulation.EntityComponentData

data class AgentControlledComponentData(
   val agentId: AgentId,
   val corePersonality: String,
   val initialMemories: List<String> = listOf(),
   val agentType: String = defaultAgentType,
   val statusStartUnixTime: Double? = null,
   val statusDuration: Double? = null,
   val agentStatus: String? = null,
   val agentIntegrationStatus: String? = null,
   val wasRateLimited: Boolean = false,
   val agentRemoteDebugInfo: String = "",
   val agentError: String? = null,
   val currentActionTimeline: String? = null,
   val executingScriptId: String? = null,
   val executingScript: String? = null,
   val scriptExecutionError: String? = null,

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