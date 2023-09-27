package botfarm.apidata

import kotlinx.serialization.Serializable

@Serializable
class PromptUsage(
   val promptTokens: Int = 0,
   val completionTokens: Int = 0,
   val totalTokens: Int = 0
)

@Serializable
class ModelUsagePricing(
   val modelId: String,
   val costPer1kInput: Double,
   val costPer1kOutput: Double
)

@Serializable
class PromptUsageInfo(
   val usage: PromptUsage,
   val modelUsagePricing: ModelUsagePricing
)