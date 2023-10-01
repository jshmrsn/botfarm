package botfarm.engine

import botfarm.engine.ktorplugins.configureMonitoring
import botfarm.engine.ktorplugins.configureRouting
import botfarm.engine.ktorplugins.configureSerialization
import botfarm.engine.ktorplugins.configureSockets
import botfarm.engine.simulation.SimulationContainer
import io.ktor.server.application.Application
import kotlin.concurrent.thread

fun Application.module() {
   println("Application.module")

   val simulationContainer = SimulationContainer()

   configureSerialization()
   configureSockets(simulationContainer)
   configureMonitoring()
   configureRouting(simulationContainer)

   thread {
      while (true) {
         simulationContainer.tick()

         val tickIntervalSeconds = 1.0 / 30.0
         Thread.sleep((tickIntervalSeconds * 1000).toLong())
      }
   }
}
