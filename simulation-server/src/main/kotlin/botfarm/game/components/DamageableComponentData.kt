package botfarm.game.components

import botfarm.engine.simulation.Entity
import botfarm.engine.simulation.EntityComponentData

data class DamageableComponentData(
   val hp: Int,
   val killedAtTime: Double? = null
) : EntityComponentData()

val Entity.killedAtTime
   get() = this.getComponentOrNull<DamageableComponentData>()?.data?.killedAtTime

val Entity.isDead
   get() = this.isDestroyed || this.getComponentOrNull<DamageableComponentData>()?.data?.killedAtTime != null
