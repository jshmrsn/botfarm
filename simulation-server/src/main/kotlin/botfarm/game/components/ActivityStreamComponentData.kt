package botfarm.game.components

import botfarmshared.misc.Vector2
import botfarm.engine.simulation.EntityComponentData
import botfarmshared.engine.apidata.EntityId

class ActivityStreamEntry(
   val time: Double,
   val title: String,
   val message: String? = null,
   val actionType: String? = null,
   val shouldReportToAi: Boolean = true,
   val actionIconPath: String? = null,
   val sourceLocation: Vector2? = null,
   val sourceIconPath: String? = null,
   val targetIconPath: String? = null,
   val sourceEntityId: EntityId? = null,
   val targetEntityId: EntityId? = null
)

data class ActivityStreamComponentData(
   val activityStream: List<ActivityStreamEntry> = listOf()
) : EntityComponentData()

class CollisionMapCellDebugInfo(
   val center: Vector2,
   val occupied: Boolean,
   val row: Int,
   val col: Int
)

data class CollisionMapDebugInfo(
   val rowCount: Int,
   val columnCount: Int,
   val bounds: Vector2,
   val cellSize: Vector2,
   val cells: List<CollisionMapCellDebugInfo> = listOf()
)

data class DebugInfoComponentData(
   val collisionMapDebugInfo: CollisionMapDebugInfo
) : EntityComponentData()
