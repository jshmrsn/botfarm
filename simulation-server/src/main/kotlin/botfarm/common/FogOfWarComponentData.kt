package botfarm.common

import botfarm.engine.simulation.EntityComponentData

data class FogOfWarComponentData(
   val isStale: Boolean = false,
   val isVisible: Boolean = true
) : EntityComponentData()