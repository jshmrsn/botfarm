package botfarm.engine

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun startSimulationServer() {
   embeddedServer(
      Netty,
      port = System.getenv()["BOTFARM_SIMULATION_SERVER_PORT"]?.toInt() ?: 5001,
      host = "0.0.0.0",
      module = Application::module
   ).start(wait = true)
}