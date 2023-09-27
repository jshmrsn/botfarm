package botfarm.simulationserver.common

import botfarm.misc.Vector2
import botfarm.misc.Vector2Animation
import botfarm.simulationserver.simulation.Entity
import botfarm.simulationserver.simulation.EntityComponentData

data class PositionComponentData(
   val positionAnimation: Vector2Animation = Vector2Animation()
) : EntityComponentData()

fun Entity.resolvePosition(): Vector2 {
   val positionComponent = this.getComponent<PositionComponentData>()
   return positionComponent.data.positionAnimation.resolve(this.simulation.getCurrentSimulationTime())
}

fun Entity.resolvePosition(simulationTime: Double): Vector2 {
   val positionComponent = this.getComponent<PositionComponentData>()
   return positionComponent.data.positionAnimation.resolve(simulationTime)
}