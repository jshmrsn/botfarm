package botfarm.common

import botfarmshared.misc.Vector2
import botfarmshared.misc.Vector2Animation
import botfarm.engine.simulation.Entity
import botfarm.engine.simulation.EntityComponentData

data class PositionComponentData(
   val positionAnimation: Vector2Animation = Vector2Animation(),
   val movementId: String = "default"
) : EntityComponentData()

fun Entity.resolvePosition(): Vector2 {
   val positionComponent = this.getComponent<PositionComponentData>()
   return positionComponent.data.positionAnimation.resolve(this.simulation.getCurrentSimulationTime())
}

val Entity.isMoving: Boolean
   get() {
      val positionComponent = this.getComponent<PositionComponentData>()
      val keyFrames = positionComponent.data.positionAnimation.keyFrames

      if (keyFrames.isEmpty()) {
         return false
      }

      return this.simulation.getCurrentSimulationTime() < keyFrames.last().time
   }

fun Entity.resolvePosition(simulationTime: Double): Vector2 {
   val positionComponent = this.getComponent<PositionComponentData>()
   return positionComponent.data.positionAnimation.resolve(simulationTime)
}