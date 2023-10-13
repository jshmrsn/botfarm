package botfarm.engine.ktorplugins

object ServerEnvironmentGlobals {
   val adminSecret: String? = System.getenv("BOTFARM_ADMIN_SECRET")?.trim()
   val hasAdminSecret = !AdminRequest.serverAdminSecret.isNullOrBlank()

   val port = System.getenv()["BOTFARM_SIMULATION_SERVER_PORT"]?.toInt() ?: 5001
   val agentServerEndpoint = System.getenv()["BOTFARM_AGENT_SERVER_ENDPOINT"] ?: "http://localhost:5002"

   val defaultPauseAiUsd = System.getenv()["BOTFARM_DEFAULT_PAUSE_AI_USD_CENTS"]
      ?.toInt()?.let { it.toDouble() / 100.0 }
      ?: 1.0

   val hideErrorDetailsFromClients = System.getenv()["BOTFARM_HIDE_ERROR_DETAILS"].let {
      if (it.isNullOrBlank()) {
         this.hasAdminSecret
      } else {
         !it.looksLikeFalse
      }
   }

   val hidePromptDetailsFromClients = System.getenv()["BOTFARM_HIDE_ERROR_DETAILS"].let {
      if (it.isNullOrBlank()) {
         this.hideErrorDetailsFromClients
      } else {
         !it.looksLikeFalse
      }
   }
}

private val falseStrings = listOf("no", "false", "0")

val String.looksLikeFalse
   get() = this.lowercase() in falseStrings
