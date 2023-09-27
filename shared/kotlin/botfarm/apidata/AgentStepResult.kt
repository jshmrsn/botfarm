package botfarm.apidata

import kotlinx.serialization.Serializable



@Serializable
class AgentStepResult(
   val agentStatus: String? = null,
   val newDebugInfo: String? = null,
   val interactions: Interactions? = null,
   val error: String? = null,
   val wasRateLimited: Boolean = false,
   val promptUsages: List<PromptUsageInfo> = listOf()
)

@Serializable
class Interactions(
   val locationToWalkToAndReason: ReasonToWalkToAndReason? = null,
   val actionOnEntity: ActionOnEntity? = null,
   val actionOnInventoryItem: ActionOnInventoryItem? = null,
   val craftItemAction: CraftItemAction? = null,
   val iWantToSay: String? = null,
   val facialExpressionEmoji: String? = null
)

@Serializable
class ReasonToWalkToAndReason(
   val location: List<Double>,
   val reason: String? = null
)

@Serializable
class ActionOnEntity(
   val actionId: String,
   val targetEntityId: String,
   val reason: String? = null
)

@Serializable
class ActionOnInventoryItem(
   val actionId: String,
   val itemConfigKey: String,
   val amount: Int? = null,
   val reason: String? = null
)

@Serializable
class CraftItemAction(
   val itemConfigKey: String,
   val reason: String? = null
)

