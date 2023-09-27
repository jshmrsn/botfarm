import {ClientSimulationData} from "../simulation/EntityData";
import {SimulationSnapshotWebSocketMessage} from "./common";
import {Simulation} from "../simulation/Simulation";

export function handleWebSocketMessage(
  data: string,
  handleSimulationDataSnapshotReceived: (initialSimulationData: ClientSimulationData) => void,
  simulation: Simulation | null
) {
  try {
    const messageData = JSON.parse(data)
    console.log("WebSocket messageData:", messageData)

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