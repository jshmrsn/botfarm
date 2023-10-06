package botfarm.game.scripting.jsdata

import org.graalvm.polyglot.Value

class JsConversionContext(
   val bindings: Value
) {
   val convertJsArray = this.bindings.getMember("convertJsArray")
}