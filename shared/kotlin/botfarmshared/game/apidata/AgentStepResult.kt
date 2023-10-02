package botfarmshared.game.apidata

import botfarmshared.engine.apidata.EntityId
import botfarmshared.engine.apidata.PromptUsageInfo
import kotlinx.serialization.Serializable


@Serializable
class AgentSyncResponse(
   val stepResults: List<AgentStepResult>
)

@Serializable
class AgentSyncRequest(
   val inputs: AgentSyncInputs
)

@Serializable
class AgentStepResult(
   val statusStartUnixTime: Double? = null,
   val statusDuration: Double? = null,
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
   val useEquippedToolItem: UseEquippedToolItem? = null,
   val actionOnInventoryItem: ActionOnInventoryItem? = null,
   val craftItemAction: CraftItemAction? = null,
   val speak: String? = null,
   val facialExpressionEmoji: String? = null
)

@Serializable
class ReasonToWalkToAndReason(
   val location: List<Double>,
   val reason: String? = null
)

@Serializable
class UseEquippedToolItem(
   val reason: String? = null
)

@Serializable
class ActionOnEntity(
   val actionId: String,
   val targetEntityId: EntityId,
   val reason: String? = null
)

@Serializable
class ActionOnInventoryItem(
   val actionId: String,
   val itemConfigKey: String,
   val stackIndex: Int? = null,
   val amount: Int? = null,
   val reason: String? = null
)

@Serializable
class CraftItemAction(
   val itemConfigKey: String,
   val reason: String? = null
)

