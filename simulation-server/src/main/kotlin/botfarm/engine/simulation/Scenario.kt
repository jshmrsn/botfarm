package botfarm.engine.simulation

import botfarm.game.ai.AgentServerIntegration
import kotlinx.serialization.Serializable

@Serializable
class ScenarioInfo(
   val identifier: String,
   val gameIdentifier: String,
   val name: String? = null,
   val description: String? = null
)

abstract class Scenario(
   val identifier: String,
   val gameIdentifier: String,
   val name: String? = null,
   val description: String? = null
) {
   fun buildInfo(): ScenarioInfo = ScenarioInfo(
      identifier = identifier,
      gameIdentifier = gameIdentifier,
      name = name,
      description = description
   )

   abstract fun createSimulation(
      simulationContainer: SimulationContainer,
      agentServerIntegration: AgentServerIntegration
   ): Simulation
}

object ScenarioRegistration {
   private val mutableRegisteredScenarios = mutableListOf<Scenario>()
   val registeredScenarios: List<Scenario> = mutableRegisteredScenarios

   fun registerScenario(scenario: Scenario) {
      mutableRegisteredScenarios.add(scenario)
   }
}

