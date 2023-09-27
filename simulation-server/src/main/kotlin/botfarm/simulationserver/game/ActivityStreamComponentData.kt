package botfarm.simulationserver.game

import botfarm.misc.Vector2
import botfarm.simulationserver.simulation.EntityComponentData

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
   val sourceEntityId: String? = null,
   val targetEntityId: String? = null
)

data class ActivityStreamComponentData(
   val activityStream: List<ActivityStreamEntry> = listOf()
) : EntityComponentData()
