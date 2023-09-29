package botfarm.common.ai

import kotlinx.serialization.Serializable

@Serializable
class ModelInfo(
   val modelId: String = "",
   val isCompletion: Boolean = false,
   val costPer1kInput: Double = 0.0,
   val costPer1kOutput: Double = 0.0
)