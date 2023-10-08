import {ClientId, UserId} from "../simulation/Simulation";
import {SimulationScene} from "../game/SimulationScene";
import {GameSimulation} from "../game/GameSimulation";
import {EntityId} from "../simulation/EntityData";
import {generateId} from "../misc/utils";
import {AdminRequest, buildAdminRequestForSecret} from "./AdminRequest";

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

  readonly debugOverlayValuesByKey: Record<string, string> = {}

  hasSentGetReplayRequest = false
  buildAdminRequest: () => AdminRequest | null = () => null

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
      "data": data,
      "adminRequest": this.buildAdminRequest()
    }))
  }

  forceUpdate() {
    ++this.forceRenderIndex
    this.setForceUpdateCounter(this.forceRenderIndex)
  }

  setDebugOverlayValueForKey(key: string, value: any | null) {
    if (value == null) {
      delete this.debugOverlayValuesByKey[key]
    } else {
      this.debugOverlayValuesByKey[key] = "" + value
    }

    this.forceUpdate()
  }
}