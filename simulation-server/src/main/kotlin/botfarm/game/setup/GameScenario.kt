package botfarm.game.setup

import botfarm.engine.simulation.*
import botfarm.game.GameSimulation
import botfarm.game.agentintegration.AgentServerIntegration
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
   val spawnPlayersEntityMode: SpawnPlayersMode = SpawnPlayersMode.All,
   requiresAdmin: Boolean = true,
   val buildMockAgent: ((context: MockAgentContext) -> MockAgent)? = null,
   val configureGameSimulationCallback: (GameSimulation) -> Unit = {}
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

      addCharacterConfigs(configs)
      addItemConfigs(configs)

      val unixTime = getCurrentUnixTimeSeconds()

      val simulationData = SimulationData(
         lastTickUnixTime = unixTime,
         scenarioInfo = this.buildInfo(),
         configs = configs
      )

      val agentServerIntegration = AgentServerIntegration(
         agentServerEndpoint = System.getenv()["BOTFARM_AGENT_SERVER_ENDPOINT"] ?: "http://localhost:5002",
         buildMockAgent = this.buildMockAgent
      )

      val simulation = GameSimulation(
         context = context,
         data = simulationData,
         agentServerIntegration = agentServerIntegration,
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