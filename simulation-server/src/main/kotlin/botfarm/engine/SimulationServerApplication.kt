package botfarm.engine

import botfarm.engine.ktorplugins.configureMonitoring
import botfarm.engine.ktorplugins.configureRouting
import botfarm.engine.ktorplugins.configureSerialization
import botfarm.engine.ktorplugins.configureSockets
import botfarm.engine.simulation.ScenarioRegistration
import botfarm.engine.simulation.SimulationContainer
import botfarmshared.misc.getCurrentUnixTimeSeconds
import io.ktor.server.application.Application
import kotlin.concurrent.thread

fun configureSimulationServerModule(
   application: Application,
   scenarioRegistration: ScenarioRegistration
) {
   println("Application.module")

   val simulationContainer = SimulationContainer(
      scenarioRegistration = scenarioRegistration
   )

   application.configureSerialization()
   application.configureSockets(simulationContainer)
   application.configureMonitoring()
   application.configureRouting(
      simulationContainer = simulationContainer
   )

   thread {
      var lastTickUnixTime = getCurrentUnixTimeSeconds()

      while (true) {
         val currentUnixTimeSeconds = getCurrentUnixTimeSeconds()
         val deltaTime = Math.max(currentUnixTimeSeconds - lastTickUnixTime, 0.00001)
         lastTickUnixTime = currentUnixTimeSeconds

         simulationContainer.tickOnCurrentThread(deltaTime)

         val tickIntervalSeconds = 1.0 / 30.0
         Thread.sleep((tickIntervalSeconds * 1000).toLong())
      }
   }
}
