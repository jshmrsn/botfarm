package botfarmshared.game.apidata

import botfarmshared.engine.apidata.EntityId
import botfarmshared.engine.apidata.PromptUsageInfo
import botfarmshared.misc.Vector2
import botfarmshared.misc.buildShortRandomIdentifier
import kotlinx.serialization.Serializable

@Serializable
class AgentSyncResponse(
   val outputs: List<AgentSyncOutput>
)

@Serializable
class AgentSyncRequest(
   val input: AgentSyncInput
)

@Serializable
class AgentSyncOutput(
   val statusStartUnixTime: Double? = null,
   val statusDuration: Double? = null,
   val agentStatus: String? = null,
   val debugInfo: String? = null,
   val actions: List<Action>? = null,
   val error: String? = null,
   val wasRateLimited: Boolean = false,
   val promptUsages: List<PromptUsageInfo> = listOf()
)

@Serializable
class Action(
   val actionUniqueId: String = buildShortRandomIdentifier(),
   val walk: WalkAction? = null,
   val actionOnEntity: ActionOnEntity? = null,
   val useEquippedToolItem: UseEquippedToolItem? = null,
   val actionOnInventoryItem: ActionOnInventoryItem? = null,
   val craftItemAction: CraftItemAction? = null,
   val speak: String? = null,
   val facialExpressionEmoji: String? = null
)

@Serializable
class ActionResult(
   val actionUniqueId: String
)

@Serializable
class WalkAction(
   val location: Vector2,
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

