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
enum class AgentStatus {
   Idle,
   UpdatingMemory,
   Running,
   Error,
   RateLimited
}

@Serializable
class AgentSyncOutput(
   val agentStatus: AgentStatus? = null,
   val debugInfoByKey: Map<String, String>? = null,
   val startedRunningPrompt: RunningPromptInfo? = null,
   val promptResult: PromptResultInfo? = null,
   val actions: List<Action>? = null,
   val scriptToRun: ScriptToRun? = null,
   val error: String? = null,
   val promptUsages: List<PromptUsageInfo> = listOf()
)



@Serializable
class RunningPromptInfo(
   val prompt: String,
   val promptId: String,
   val description: String,
   val inputTokens: Int
)

@Serializable
class PromptResultInfo(
   val response: String,
   val promptId: String,
   val description: String,
   val completionTokens: Int
)



@Serializable
class ScriptToRun(
   val scriptId: String,
   val script: String
)

@Serializable
class Action(
   val actionUniqueId: String = buildShortRandomIdentifier(),
   val reason: String? = null,
   val walk: WalkAction? = null,
   val pickUpEntity: ActionOnEntity? = null,
   val useEquippedToolItemOnEntity: ActionOnEntity? = null,
   val useEquippedToolItem: UseEquippedToolItem? = null,
   val dropInventoryItem: ActionOnInventoryItem? = null,
   val equipInventoryItem: ActionOnInventoryItem? = null,
   val craftItem: CraftItemAction? = null,
   val speak: String? = null,
   val facialExpressionEmoji: String? = null,
   val recordThought: String? = null
)


@Serializable
class ScriptExecutionError(
   val error: String,
   val scriptId: String
)

@Serializable
class WalkAction(
   val location: Vector2
)

@Serializable
class UseEquippedToolItem(
)

@Serializable
class ActionOnEntity(
   val targetEntityId: EntityId
)

@Serializable
class ActionOnInventoryItem(
   val itemConfigKey: String,
   val stackIndex: Int? = null,
   val amount: Int? = null
)

@Serializable
class CraftItemAction(
   val itemConfigKey: String
)


