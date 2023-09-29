package botfarm.engine.simulation

import botfarmshared.engine.apidata.SimulationId
import botfarmshared.misc.buildShortRandomString
import kotlinx.serialization.Serializable

@Serializable
abstract class Config {
   abstract val key: String
}

@Serializable
data class SimulationData(
   val scenarioInfo: ScenarioInfo,
   val simulationId: SimulationId = SimulationId(buildShortRandomString()),
   val configs: List<Config>,
   val entities: List<EntityData> = listOf(),
   val simulationTime: Double = 0.0,
   val lastTickUnixTime: Double = 0.0
) {
   fun buildClientData(): ClientSimulationData {
      return ClientSimulationData(
         simulationId = this.simulationId,
         configs = this.configs,
         entities = this.entities,
         simulationTime = this.simulationTime
      )
   }
}
