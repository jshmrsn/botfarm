package botfarm.simulationserver.game

import botfarm.misc.Vector2
import botfarm.simulationserver.simulation.EntityComponentData
import botfarm.apidata.EntityId

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
