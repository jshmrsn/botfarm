import {ClientSimulationData, EntityData, EntityId} from "./EntityData";
import {Simulation} from "./Simulation";
import {SerializationDiff} from "../../misc/serializationDiff";


export interface SimulationSnapshotWebSocketMessage {
  simulationData: ClientSimulationData
}

export interface EntityComponentWebSocketMessage {
  entityId: EntityId
  componentTypeName: string
  diff: SerializationDiff
  simulationTime: number
}

export interface AlertWebSocketMessage {
  message: string
  mode: string
}

export interface EntityCreatedWebSocketMessage {
  entityData: EntityData
  simulationTime: number
}

export interface EntityDestroyedWebSocketMessage {
  entityId: EntityId
  simulationTime: number
}



export function handleWebSocketMessage(
  data: string,
  handleSimulationDataSnapshotReceived: (initialSimulationData: ClientSimulationData) => void,
  simulation: Simulation | null
) {
  try {
    const messageData = JSON.parse(data)
    // console.log("WebSocket messageData:", messageData)

    const messageType: string | null = messageData["type"]

    if (messageType == null) {
      console.error("No type found in websocket message", messageData)
    } else if (messageType === "SimulationSnapshotWebSocketMessage") {
      const snapshotMessage: SimulationSnapshotWebSocketMessage = messageData
      handleSimulationDataSnapshotReceived(snapshotMessage.simulationData)
    } else if (simulation != null) {
      simulation.handleWebSocketMessage(messageType, messageData)
    } else {
      console.error("Unhandled websocket message type", messageType)
    }
  } catch (e) {
    console.error("Error handling websocket data", e)
    return
  }
}