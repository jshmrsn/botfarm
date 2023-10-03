package botfarmagent.game.agents.scripted

import botfarmshared.misc.Vector2
import org.graalvm.polyglot.HostAccess

class JsVector2(
   val value: Vector2
) {
   @HostAccess.Export
   @JvmField
   val x = this.value.x

   @HostAccess.Export
   @JvmField
   val y = this.value.y

   override fun toString(): String {
      return "JsVector2(${this.x}, ${this.y})"
   }

   @HostAccess.Export
   fun getMagnitude(): Double {
      return this.value.magnitude()
   }

   @HostAccess.Export
   fun distanceTo(other: JsVector2): Double {
      return this.value.distance(other.value)
   }

   @HostAccess.Export
   fun plus(other: JsVector2): JsVector2 {
      return JsVector2(this.value + other.value)
   }

   @HostAccess.Export
   fun minus(other: JsVector2): JsVector2 {
      return JsVector2(this.value - other.value)
   }
}

fun Vector2.toJs(): JsVector2 {
   return JsVector2(this)
}

