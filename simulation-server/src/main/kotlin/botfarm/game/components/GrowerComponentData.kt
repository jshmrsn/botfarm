package botfarm.game.components

import botfarm.engine.simulation.EntityComponentData
import kotlinx.serialization.Serializable

@Serializable
data class ActiveGrowth(
   val startTime: Double,
   val itemConfigKey: String
) : EntityComponentData()

data class GrowerComponentData(
   val activeGrowth: ActiveGrowth? = null
) : EntityComponentData()