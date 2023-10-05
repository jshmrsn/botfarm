package botfarm.game.agentintegration

import botfarmshared.engine.apidata.PromptUsageInfo
import botfarm.game.components.AgentControlledComponentData
import botfarm.engine.simulation.EntityComponent

fun updateComponentModelUsage(
   usageInfo: PromptUsageInfo,
   agentControlledComponent: EntityComponent<AgentControlledComponentData>
) {
   val modelUsagePricing = usageInfo.modelUsagePricing
   val inputTokens = usageInfo.usage.promptTokens
   val outputTokens = usageInfo.usage.completionTokens

   val promptDollarCost = (modelUsagePricing.costPer1kInput * inputTokens + modelUsagePricing.costPer1kOutput * outputTokens) / 1000.0

   agentControlledComponent.modifyData {
      it.copy(
         totalInputTokens = it.totalInputTokens + inputTokens,
         totalOutputTokens = it.totalOutputTokens + outputTokens,
         totalPrompts = it.totalPrompts + 1,
         costDollars = it.costDollars + promptDollarCost
      )
   }
}