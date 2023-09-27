package botfarm.simulationserver.common.ai

import kotlinx.serialization.Serializable

@Serializable
class ModelUsagePricing(
   val modelId: String,
   val costPer1kInput: Double,
   val costPer1kOutput: Double
)