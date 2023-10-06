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
      var didSucceed = false
      simulateSeconds(
         duration = maxSeconds,
         delayMsPerTick = delayMsPerTick,
         shouldStop = {
            if (until()) {
               didSucceed = true
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
   val agentService = createTestAgentService(
      buildMockAgent = buildMockAgent
   )

   val configs = mutableListOf<Config>()
   addCharacterConfigs(configs)
   addItemConfigs(configs)

   val simulationData = SimulationData(
      scenarioInfo = ScenarioInfo(
         identifier = "test",
         gameIdentifier = "game"
      ),
      simulationId = SimulationId(value = "test-simulation-1"),
      configs = configs
   )

   val simulationContainer = SimulationContainer(
      scenarioRegistration = ScenarioRegistration()
   )

   val scenario = GameScenario(
      identifier = "test"
   )

   val simulationContext = SimulationContext(
      wasCreatedByAdmin = true,
      simulationContainer = simulationContainer,
      createdByUserSecret = UserSecret("test"),
      scenario = scenario,
      noClientsConnectedTerminationTimeoutSeconds = null,
      coroutineDelayImplementation = {
//         println("test delay: " + it)
         delay(1)
      }
   )


   val simulation = GameSimulation(
      context = simulationContext,
      data = simulationData,
      agentService = agentService,
      gameScenario = scenario
   )

   simulationContainer.createSimulation(simulation, shouldTickInBackground = false)

   val gameSimulationTestHelper = GameSimulationTestHelper(
      simulation = simulation,
      simulationContainer = simulationContainer
   )

   runBlocking {
      test(gameSimulationTestHelper)
   }
}


