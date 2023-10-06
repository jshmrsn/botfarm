package botfarm.game.scripting.jsdata

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

fun <T> List<T>.toJs(jsConversionContext: JsConversionContext?): Any {
   val jsArray = JsArray(this)

   if (jsConversionContext == null) {
      return jsArray
   }

   return jsConversionContext.convertJsArray.execute(jsArray)
}
