package botfarm.engine

import botfarm.engine.ktorplugins.*
import botfarm.game.ai.AgentServerIntegration
import botfarm.engine.simulation.SimulationContainer
import io.ktor.server.application.Application
import kotlin.concurrent.thread

fun Application.module() {
   println("Application.module")

   val simulationContainer = SimulationContainer()
   val agentServerIntegration = AgentServerIntegration()

   configureSerialization()
   configureSockets(simulationContainer)
   configureMonitoring()
   configureRouting(simulationContainer, agentServerIntegration)

   thread {
      while (true) {
         simulationContainer.tick()

         val tickIntervalSeconds = 1.0 / 30.0
         Thread.sleep((tickIntervalSeconds * 1000).toLong())
      }
   }
}
