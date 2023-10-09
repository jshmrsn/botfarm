import {ClientId, UserId} from "../simulation/Simulation";
import {GameSimulationScene} from "../game/GameSimulationScene";
import {GameSimulation} from "../game/GameSimulation";
import {EntityId} from "../simulation/EntityData";
import {generateId} from "../misc/utils";
import {AdminRequest} from "./AdminRequest";
import {Entity} from "../simulation/Entity";



export interface DynamicStateContext {
  setSelectedEntityId: (entityId: EntityId | null) => void
  setPerspectiveEntityIdOverride: (entityId: EntityId | null) => void
  setForceUpdateCounter: (counter: number) => void
}

export class DynamicState {
  forceRenderIndex: number = 0
  userId: UserId
  chatTextArea: HTMLTextAreaElement | null = null
  scene: GameSimulationScene | null = null
  webSocket: WebSocket | null = null
  simulation: GameSimulation | null = null
  selectedEntityId: EntityId | null = null
  perspectiveEntity: Entity | null = null
  userControlledEntity: Entity | null = null
  setSelectedEntityId: (entityId: EntityId | null) => void
  setPerspectiveEntityIdOverride: (entityId: EntityId | null) => void
  context: DynamicStateContext

  setForceUpdateCounter: (counter: number) => void

  readonly clientId: ClientId = generateId()

  readonly debugOverlayValuesByKey: Record<string, string> = {}

  hasSentGetReplayRequest = false
  buildAdminRequest: () => AdminRequest | null = () => null

  constructor(
    userId: UserId,
    context: DynamicStateContext
  ) {
    this.context = context
    this.userId = userId
    this.setForceUpdateCounter = context.setForceUpdateCounter
    this.setSelectedEntityId = context.setSelectedEntityId
    this.setPerspectiveEntityIdOverride = context.setPerspectiveEntityIdOverride
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
    this.setDebugOverlayValueForKeyWithoutForceUpdate(key, value)

    this.forceUpdate()
  }

  setDebugOverlayValueForKeyWithoutForceUpdate(key: string, value: any | null) {
    if (value == null) {
      delete this.debugOverlayValuesByKey[key]
    } else {
      this.debugOverlayValuesByKey[key] = "" + value
    }
  }
}