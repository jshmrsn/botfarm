package botfarm.engine.simulation

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
   val description: String? = null,
   val requiresAdmin: Boolean = true
) {
   fun buildInfo(): ScenarioInfo = ScenarioInfo(
      identifier = identifier,
      gameIdentifier = gameIdentifier,
      name = name,
      description = description
   )

   abstract fun createSimulation(
      context: SimulationContext
   ): Simulation
}

class ScenarioRegistration {
   private val mutableRegisteredScenarios = mutableListOf<Scenario>()
   val registeredScenarios: List<Scenario> = mutableRegisteredScenarios

   fun registerScenario(scenario: Scenario) {
      this.mutableRegisteredScenarios.forEach {
         if (it.identifier == scenario.identifier &&
            it.gameIdentifier == scenario.gameIdentifier) {
            throw Exception("Scenario identifier already registered: ${scenario.identifier}")
         }
      }

      this.mutableRegisteredScenarios.add(scenario)
   }
}

