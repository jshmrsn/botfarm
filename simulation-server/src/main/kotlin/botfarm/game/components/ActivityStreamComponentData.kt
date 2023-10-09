package botfarm.game.components

import botfarm.engine.simulation.EntityComponentData
import botfarmshared.game.apidata.ActivityStreamEntry
import botfarmshared.misc.Vector2

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
   val aiPaused: Boolean = false,
   val collisionMapDebugInfo: CollisionMapDebugInfo
) : EntityComponentData()
