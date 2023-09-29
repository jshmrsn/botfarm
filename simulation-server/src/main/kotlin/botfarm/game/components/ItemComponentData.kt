package botfarm.game.components

import botfarm.engine.simulation.EntityComponentData

data class ItemComponentData(
   val itemConfigKey: String,
   val amount: Int = 1
) : EntityComponentData()