package botfarm.simulationserver

import botfarm.simulationserver.game.ai.AgentServerIntegration
import botfarm.simulationserver.game.registerSystems
import botfarm.simulationserver.plugins.*
import botfarm.simulationserver.simulation.SimulationContainer
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlin.concurrent.thread

fun main() {
   embeddedServer(
      Netty,
      port = System.getenv()["BOTFARM_SIMULATION_SERVER_PORT"]?.toInt() ?: 5001,
      host = "0.0.0.0",
      module = Application::module
   ).start(wait = true)
}

fun Application.module() {
   println("Application.module")

   val simulationContainer = SimulationContainer()
   val agentServerIntegration = AgentServerIntegration()

   configureSerialization()
   registerSystems()
   configureSockets(simulationContainer)
   configureMonitoring()
   configureHTTP()
   configureRouting(simulationContainer, agentServerIntegration)

   thread {
      while (true) {
         synchronized(simulationContainer) {
            simulationContainer.tick()
         }

         val tickIntervalSeconds = 1.0
         Thread.sleep((tickIntervalSeconds * 1000).toLong())
      }
   }
}
