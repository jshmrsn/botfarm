package botfarm.simulationserver.simulation

import botfarm.apidata.SimulationId
import botfarm.misc.buildShortRandomString
import kotlinx.serialization.Serializable

@Serializable
abstract class Config {
   abstract val key: String
}

@Serializable
data class SimulationData(
   val simulationId: SimulationId = SimulationId(buildShortRandomString()),
   val configs: List<Config>,
   val entities: List<EntityData> = listOf(),
   val tickedSimulationTime: Double = 0.0,
   val lastTickUnixTime: Double = 0.0
) {
   fun buildClientData(simulationTime: Double): ClientSimulationData {
      return ClientSimulationData(
         simulationId = this.simulationId,
         configs = this.configs,
         entities = this.entities,
         tickedSimulationTime = this.tickedSimulationTime,
         simulationTime = simulationTime
      )
   }
}
