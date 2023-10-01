import {ClientId, UserId} from "../simulation/Simulation";
import {SimulationScene} from "../game/SimulationScene";
import {GameSimulation} from "../game/GameSimulation";
import {EntityId} from "../simulation/EntityData";
import {generateId} from "../misc/utils";

export class DynamicState {
  forceRenderIndex: number = 0
  userId: UserId
  chatTextArea: HTMLTextAreaElement | null = null
  phaserScene: SimulationScene | null = null
  webSocket: WebSocket | null = null
  simulation: GameSimulation | null = null
  selectedEntityId: EntityId | null = null
  setForceUpdateCounter: (counter: number) => void

  readonly clientId: ClientId = generateId()

  hasSentGetReplayRequest = false

  constructor(userId: UserId, setForceUpdateCounter: (counter: number) => void) {
    this.userId = userId
    this.setForceUpdateCounter = setForceUpdateCounter
  }

  sendWebSocketMessage(type: string, data: object) {
    if (this.webSocket == null) {
      throw new Error("Attempt to send web socket message while no web socket is available: " + type + "\n" + JSON.stringify(data))
    }

    this.webSocket.send(JSON.stringify({
      "type": type,
      "data": data
    }))
  }

  forceUpdate() {
    ++this.forceRenderIndex
    this.setForceUpdateCounter(this.forceRenderIndex)
  }
}