package botfarm.engine.simulation

import botfarmshared.engine.apidata.EntityId
import kotlinx.serialization.json.JsonElement

abstract class WebSocketMessage()

class SimulationSnapshotWebSocketMessage(
   val simulationData: ClientSimulationData
) : WebSocketMessage()

class EntityComponentWebSocketMessage(
   val entityId: EntityId,
   val componentTypeName: String,
   val diff: JsonElement,
   val simulationTime: Double
) : WebSocketMessage()

class EntityCreatedWebSocketMessage(
   val entityData: EntityData,
   val simulationTime: Double
) : WebSocketMessage()

class EntityDestroyedWebSocketMessage(
   val entityId: EntityId,
   val simulationTime: Double
) : WebSocketMessage()


enum class AlertMode {
   Alert,
   GameMessage,
   Console,
   ConsoleError
}

class AlertWebSocketMessage(
   val message: String,
   val mode: String
) : WebSocketMessage()


