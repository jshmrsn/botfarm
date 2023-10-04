package botfarmagent.game

import botfarmshared.engine.apidata.ModelUsagePricing
import botfarmshared.engine.apidata.PromptUsage
import botfarmshared.engine.apidata.PromptUsageInfo
import com.knuddels.jtokkit.api.ModelType

class ModelInfo(
   val modelId: String,
   val costPer1kInput: Double,
   val costPer1kOutput: Double,
   val isCompletionModel: Boolean = false,
   val closestTikTokenModelType: ModelType
) {
   val maxTokenCount = this.closestTikTokenModelType.maxContextLength
   companion object {
      val gpt_4 = ModelInfo(
         modelId = "gpt-4",
         costPer1kInput = 0.03,
         costPer1kOutput = 0.06,
         closestTikTokenModelType = ModelType.GPT_4
      )

      val gpt_3_5_turbo = ModelInfo(
         modelId = "gpt-3.5-turbo",
         costPer1kInput = 0.0015,
         costPer1kOutput = 0.002,
         closestTikTokenModelType = ModelType.GPT_3_5_TURBO
      )

      val gpt_3_5_turbo_instruct = ModelInfo(
         modelId = "gpt-3.5-turbo-instruct",
         costPer1kInput = 0.0015,
         costPer1kOutput = 0.002,
         isCompletionModel = true,
         closestTikTokenModelType = ModelType.GPT_3_5_TURBO
      )

      val default = gpt_3_5_turbo_instruct
   }
}

fun buildPromptUsageInfo(
   usage: PromptUsage,
   modelInfo: ModelInfo
) = PromptUsageInfo(
   usage = usage,
   modelUsagePricing = ModelUsagePricing(
      modelId = modelInfo.modelId,
      costPer1kInput = modelInfo.costPer1kInput,
      costPer1kOutput = modelInfo.costPer1kOutput
   )
)
