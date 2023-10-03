package botfarmagent.game.agents.scripted

import org.graalvm.polyglot.HostAccess

class JsArray<T>(
   val values: List<T>
) {
   @HostAccess.Export
   fun getLength(): Int = this.values.size

   @HostAccess.Export
   fun get(index: Int): T {
      return this.values.get(index)
   }
}

fun <T> List<T>.toJs(): JsArray<T> {
   return JsArray(this)
}
