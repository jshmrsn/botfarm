package botfarm.engine

import botfarm.engine.ktorplugins.configureMonitoring
import botfarm.engine.ktorplugins.configureRouting
import botfarm.engine.ktorplugins.configureSerialization
import botfarm.engine.ktorplugins.configureSockets
import botfarm.engine.simulation.SimulationContainer
import botfarm.game.agentintegration.AgentServerIntegration
import botfarmshared.misc.getCurrentUnixTimeSeconds
import io.ktor.server.application.Application
import kotlin.concurrent.thread

fun Application.module() {
   println("Application.module")

   val simulationContainer = SimulationContainer()
   val agentServerIntegration = AgentServerIntegration(
      agentServerEndpoint = System.getenv()["BOTFARM_AGENT_SERVER_ENDPOINT"] ?: "http://localhost:5002"
   )

   configureSerialization()
   configureSockets(simulationContainer)
   configureMonitoring()
   configureRouting(
      simulationContainer = simulationContainer,
      agentServerIntegration = agentServerIntegration
   )

   thread {
      var lastTickUnixTime = getCurrentUnixTimeSeconds()

      while (true) {
         val currentUnixTimeSeconds = getCurrentUnixTimeSeconds()
         val deltaTime = Math.max(currentUnixTimeSeconds - lastTickUnixTime, 0.00001)
         lastTickUnixTime = currentUnixTimeSeconds

         simulationContainer.tick(deltaTime)

         val tickIntervalSeconds = 1.0 / 30.0
         Thread.sleep((tickIntervalSeconds * 1000).toLong())
      }
   }
}
