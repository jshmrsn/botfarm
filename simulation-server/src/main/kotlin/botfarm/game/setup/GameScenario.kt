package botfarm.game.setup

import botfarm.common.SpriteConfig
import botfarm.engine.simulation.*
import botfarm.game.*
import botfarm.game.agentintegration.AgentServerIntegration
import botfarm.game.components.CompositeAnimationSelection
import botfarm.game.config.*
import botfarmshared.game.apidata.ItemCollection
import botfarmshared.game.apidata.ItemCollectionEntry
import botfarmshared.misc.RandomConfig
import botfarmshared.misc.Vector2
import botfarmshared.misc.getCurrentUnixTimeSeconds

open class GameScenario(
   identifier: String,
   name: String? = null,
   description: String? = null,
   spawnPlayersMode: SpawnPlayersMode = SpawnPlayersMode.All,
   requiresAdmin: Boolean = true
) : Scenario(
   identifier = identifier,
   gameIdentifier = "game",
   name = name,
   description = description,
   spawnPlayersEntityMode = spawnPlayersMode,
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

      val simulation = GameSimulation(
         context = context,
         data = simulationData
      )

      synchronized(simulation) {
         this.configureGameSimulation(simulation)
      }

      return simulation
   }

   open fun configureGameSimulation(simulation: GameSimulation) {}
}