package botfarm.simulationserver.game

import botfarm.simulationserver.simulation.EntityComponentData
import botfarm.simulationserver.simulation.UserId

data class UserControlledComponentData(
   val userId: UserId
) : EntityComponentData()