package botfarmagent.game

import botfarmagent.game.ktorplugins.configureMonitoring
import botfarmagent.game.ktorplugins.configureRouting
import botfarmagent.game.ktorplugins.configureSerialization
import botfarmagent.misc.MockLanguageModelService
import botfarmagent.misc.OpenAiLanguageModelService
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.client.LoggingConfig
import com.aallam.openai.client.OpenAI
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlin.time.Duration.Companion.seconds

fun main() {
   embeddedServer(
      Netty,
      port = System.getenv()["BOTFARM_AGENT_SERVER_PORT"]?.toInt() ?: 5002,
      host = "0.0.0.0",
      module = Application::module
   ).start(wait = true)
}

fun Application.module() {
   val aiLanguageModelService = System.getenv("BOTFARM_OPENAI_API_KEY")?.let {
      OpenAiLanguageModelService(OpenAI(
         token = it,
         timeout = Timeout(socket = 120.seconds),
         logging = LoggingConfig(
            logLevel = LogLevel.None
         )
      ))
   } ?: MockLanguageModelService(
      handleCompletionRequest = {
         throw Exception("No OpenAI key provided, reached mock language model.")
      },
      handleChatCompletionRequest = {
         throw Exception("No OpenAI key provided, reached mock language model.")
      }
   )

   val agentContainer = AgentContainer(
      languageModelService = aiLanguageModelService
   )

   configureSerialization()

   configureMonitoring()
   configureRouting(agentContainer)
}

