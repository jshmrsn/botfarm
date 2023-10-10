package botfarm.game.agentintegration

import botfarm.game.scripting.jsdata.AgentJavaScriptApi
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source

class AgentJavaScriptRuntime(
   val agentJavaScriptApi: AgentJavaScriptApi
) {
   val javaScriptContext: Context =
      Context.newBuilder("js")
         .option("js.strict", "true")
         .build()

   val bindings = this.javaScriptContext.getBindings("js").also {
      it.putMember("api", this.agentJavaScriptApi)
   }

   init {
      val sourceName = "scripted-agent-runtime.js"

      val runtimeSource =
         this::class.java.getResource("/$sourceName")?.readText()
            ?: throw Exception("Scripted agent runtime JavaScript resource not found")

      val javaScriptSource = Source.newBuilder("js", runtimeSource, sourceName).build()
      this.javaScriptContext.eval(javaScriptSource)
   }
}