package botfarm.simulationserver.game

import botfarm.simulationserver.simulation.EntityComponentData

data class UserControlledComponentData(
   val userId: String
) : EntityComponentData()