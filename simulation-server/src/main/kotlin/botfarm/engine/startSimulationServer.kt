package botfarm.engine

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun startSimulationServer(
   configureModule: (application: Application) -> Unit
) {
   embeddedServer(
      Netty,
      port = System.getenv()["BOTFARM_SIMULATION_SERVER_PORT"]?.toInt() ?: 5001,
      host = "0.0.0.0",
      module = {
         configureModule(this)
      }
   ).start(wait = true)
}
