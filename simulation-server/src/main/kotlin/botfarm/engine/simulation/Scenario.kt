package botfarm.engine.simulation

import botfarm.game.ai.AgentServerIntegration

class ScenarioContext(
   val simulationContainer: SimulationContainer,
   val agentServerIntegration: AgentServerIntegration
)

abstract class Scenario(
   val identifier: String,
   val gameIdentifier: String,
   val name: String? = null,
   val description: String? = null
) {
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

