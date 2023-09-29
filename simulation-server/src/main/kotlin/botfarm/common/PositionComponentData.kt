package botfarm.common

import botfarmshared.misc.Vector2
import botfarmshared.misc.Vector2Animation
import botfarm.engine.simulation.Entity
import botfarm.engine.simulation.EntityComponentData

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