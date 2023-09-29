package botfarm.agentserver

import botfarm.agentserver.plugins.configureMonitoring
import botfarm.agentserver.plugins.configureRouting
import botfarm.agentserver.plugins.configureSerialization
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
   embeddedServer(
      Netty,
      port = System.getenv()["BOTFARM_AGENT_SERVER_PORT"]?.toInt() ?: 5002,
      host = "0.0.0.0",
      module = Application::module
   ).start(wait = true)
}

fun Application.module() {
   val remoteAgentContainer = AgentContainer()

   configureSerialization()

   configureMonitoring()
   configureRouting(remoteAgentContainer)
}
