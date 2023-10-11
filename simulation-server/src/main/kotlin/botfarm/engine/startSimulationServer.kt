package botfarm.engine

import botfarm.engine.ktorplugins.ServerEnvironmentGlobals
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun startSimulationServer(
   configureModule: (application: Application) -> Unit
) {
   embeddedServer(
      Netty,
      port = ServerEnvironmentGlobals.port,
      host = "0.0.0.0",
      module = {
         configureModule(this)
      }
   ).start(wait = true)
}
