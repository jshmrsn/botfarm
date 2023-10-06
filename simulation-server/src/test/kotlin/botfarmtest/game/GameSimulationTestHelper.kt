package botfarmtest.game

import botfarm.engine.simulation.*
import botfarm.game.GameSimulation
import botfarm.game.agentintegration.MockAgent
import botfarm.game.agentintegration.MockAgentContext
import botfarm.game.components.ItemComponentData
import botfarm.game.setup.GameScenario
import botfarm.game.setup.addCharacterConfigs
import botfarm.game.setup.addItemConfigs
import botfarmshared.engine.apidata.SimulationId
import botfarmshared.game.apidata.AgentSyncOutput
import botfarmshared.game.apidata.AgentSyncResponse
import botfarmshared.game.apidata.ScriptToRun
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.math.min

class GameSimulationTestHelper(
   val simulation: GameSimulation,
   val simulationContainer: SimulationContainer
) {
   fun getItemEntities(itemConfigKey: String) = this.simulation.entities.filter {
      it.getComponentOrNull<ItemComponentData>()?.data?.itemConfigKey == itemConfigKey
   }

   fun getWoodEntities() = this.getItemEntities("wood")

   suspend fun simulateSeconds(
      duration: Double,
      delayMsPerTick: Int = 1,
      shouldStop: () -> Boolean = { false }
   ) {
      var durationRemaining = duration

      while (durationRemaining >= 0.00001) {
         val deltaTime = min(0.2, durationRemaining)
         durationRemaining -= deltaTime
         this.simulationContainer.tickOnCurrentThread(
            deltaTime = deltaTime
         )

         if (shouldStop()) {
            break
         }

         delay(delayMsPerTick.toLong())
      }
   }

   suspend fun simulateUntil(
      description: String = "default",
      delayMsPerTick: Int = 1,
      maxSeconds: Double = 60.0,
      until: () -> Boolean = { false }
   ) {
      println("simulateUntil: $description")

      var didSucceed = false
      simulateSeconds(
         duration = maxSeconds,
         delayMsPerTick = delayMsPerTick,
         shouldStop = {
            if (until()) {
               didSucceed = true
               println("simulateUntil succeeded: $description")
               true
            } else {
               false
            }
         }
      )

      if (!didSucceed) {
         throw Exception("simulateUntil: Max time reached (${maxSeconds.toInt()}) ($description)")
      }
   }
}

fun simulationTest(
   buildMockAgent: ((context: MockAgentContext) -> MockAgent) = {
      MockAgent {
         AgentSyncResponse(
            outputs = listOf()
         )
      }
   },
   test: suspend GameSimulationTestHelper.() -> Unit
) {
   val configs = mutableListOf<Config>()
   addCharacterConfigs(configs)
   addItemConfigs(configs)

   val simulationContainer = SimulationContainer(
      scenarioRegistration = ScenarioRegistration()
   )

   val scenario = GameScenario(
      identifier = "test",
      buildMockAgent = buildMockAgent
   )

   runBlocking {
      val simulation = simulationContainer.createSimulation(
         wasCreatedByAdmin = true,
         createdByUserSecret = UserSecret("test"),
         scenario = scenario,
         noClientsConnectedTerminationTimeoutSeconds = null,
         shouldMinimizeSleep = true,
         coroutineScope = this
      ) as GameSimulation

      val gameSimulationTestHelper = GameSimulationTestHelper(
         simulation = simulation,
         simulationContainer = simulationContainer
      )

      println("simulationTest: Calling test callback")
      test(gameSimulationTestHelper)
      println("simulationTest: Test callback complete")
      simulation.handleTermination()
      println("simulationTest: simulation.handleTermination returned")
   }

   println("simulationTest: After runBlocking")
}


