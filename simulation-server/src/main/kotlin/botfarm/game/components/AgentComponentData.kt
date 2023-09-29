package botfarm.game.components

import botfarmshared.game.apidata.AgentId
import botfarm.engine.simulation.EntityComponentData

data class AgentComponentData(
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
   val observationDistance: Double = 1200.0,
   val totalInputTokens: Int = 0,
   val totalOutputTokens: Int = 0,
   val totalPrompts: Int = 0,
   val totalRemoteAgentRequests: Int = 0,
   val costDollars: Double = 0.0
) : EntityComponentData() {
   companion object {
      val defaultAgentType = "default"
   }
}