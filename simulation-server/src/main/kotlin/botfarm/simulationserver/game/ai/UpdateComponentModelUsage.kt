package botfarm.simulationserver.game.ai

import botfarm.apidata.PromptUsageInfo
import botfarm.simulationserver.game.AgentComponentData
import botfarm.simulationserver.simulation.EntityComponent

fun updateComponentModelUsage(
   usageInfo: PromptUsageInfo,
   agentComponent: EntityComponent<AgentComponentData>
) {
   val modelUsagePricing = usageInfo.modelUsagePricing
   val inputTokens = usageInfo.usage.promptTokens
   val outputTokens = usageInfo.usage.completionTokens

   val promptDollarCost = (modelUsagePricing.costPer1kInput * inputTokens + modelUsagePricing.costPer1kOutput * outputTokens) / 1000.0

   agentComponent.modifyData {
      it.copy(
         totalInputTokens = it.totalInputTokens + inputTokens,
         totalOutputTokens = it.totalOutputTokens + outputTokens,
         totalPrompts = it.totalPrompts + 1,
         costDollars = it.costDollars + promptDollarCost
      )
   }
}