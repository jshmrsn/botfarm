package botfarm.game.components

import botfarm.engine.simulation.Entity
import botfarm.engine.simulation.EntityComponentData

data class KillableComponentData(
   val hp: Int,
   val killedAtTime: Double? = null
) : EntityComponentData()

val Entity.killedAtTime
   get() = this.getComponentOrNull<KillableComponentData>()?.data?.killedAtTime

val Entity.isDead
   get() = this.isDestroyed || this.getComponentOrNull<KillableComponentData>()?.data?.killedAtTime != null


