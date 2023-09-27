package botfarm.simulationserver.common.ai

import kotlinx.serialization.Serializable

@Serializable
class PromptUsage(
   val promptTokens: Int = 0,
   val completionTokens: Int = 0,
   val totalTokens: Int = 0
)