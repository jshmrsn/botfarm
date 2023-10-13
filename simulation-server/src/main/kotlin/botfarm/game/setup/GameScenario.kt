package botfarm.game.setup

import botfarm.engine.ktorplugins.ServerEnvironmentGlobals
import botfarm.engine.simulation.*
import botfarm.game.GameSimulation
import botfarm.game.agentintegration.AgentService
import botfarm.game.agentintegration.MockAgent
import botfarm.game.agentintegration.MockAgentContext
import botfarmshared.misc.getCurrentUnixTimeSeconds

enum class SpawnPlayersMode {
   None,
   NonCreator,
   All
}

open class GameScenario(
   identifier: String,
   name: String? = null,
   description: String? = null,
   val autoSpawnPlayersEntityMode: SpawnPlayersMode = SpawnPlayersMode.All,
   requiresAdmin: Boolean = true,
   val buildMockAgent: ((context: MockAgentContext) -> MockAgent)? = null,
   val configureGameSimulationCallback: (GameSimulation) -> Unit = {},
   val autoPauseAiPerSpentDollars: Double? = ServerEnvironmentGlobals.defaultPauseAiUsd
) : Scenario(
   identifier = identifier,
   gameIdentifier = "game",
   name = name,
   description = description,
   requiresAdmin = requiresAdmin
) {
   override fun createSimulation(
      context: SimulationContext
   ): Simulation {
      val configs = mutableListOf<Config>()

      addBasicConfigs(configs)
      addCharacterConfigs(configs)
      addItemConfigs(configs)

      val unixTime = getCurrentUnixTimeSeconds()

      val simulationData = SimulationData(
         lastTickUnixTime = unixTime,
         scenarioInfo = this.buildInfo(),
         configs = configs
      )

      val agentServerIntegration = AgentService(
         agentServerEndpoint = ServerEnvironmentGlobals.agentServerEndpoint,
         buildMockAgent = this.buildMockAgent
      )

      val simulation = GameSimulation(
         context = context,
         data = simulationData,
         agentService = agentServerIntegration,
         gameScenario = this
      )

      synchronized(simulation) {
         this.configureGameSimulationCallback(simulation)
         this.configureGameSimulation(simulation)
      }

      return simulation
   }

   open fun configureGameSimulation(simulation: GameSimulation) {}
}