package botfarm.game.components

import botfarm.engine.simulation.EntityComponentData
import botfarm.engine.simulation.UserId

data class UserControlledComponentData(
   val userId: UserId
) : EntityComponentData()