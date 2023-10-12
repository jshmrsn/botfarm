package botfarm.game.components

import botfarm.engine.simulation.EntityComponentData
import botfarmshared.engine.apidata.EntityId

data class GameClientStateComponentData(
   val perspectiveEntityIdOverride: EntityId? = null,
   val shouldSpectateByDefault: Boolean = false
) : EntityComponentData()
