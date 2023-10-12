import {ClientId, UserId} from "../../engine/simulation/Simulation";
import {GameSimulationScene} from "../scene/GameSimulationScene";
import {GameSimulation} from "../simulation/GameSimulation";
import {EntityId} from "../../engine/simulation/EntityData";
import {generateId} from "../../misc/utils";
import {AdminRequest} from "./AdminRequest";
import {Entity} from "../../engine/simulation/Entity";



export interface DynamicStateContext {
  selectEntity: (entityId: EntityId | null) => void
  setPerspectiveEntityIdOverride: (entityId: EntityId | null) => void
  setForceUpdateCounter: (counter: number) => void
  setIsInForceSpectateMode: (value: boolean) => void
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
  rawUserControlledEntity: Entity | null = null
  selectEntity: (entityId: EntityId | null) => void
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
    this.selectEntity = context.selectEntity
    this.setPerspectiveEntityIdOverride = context.setPerspectiveEntityIdOverride
  }

  sendWebSocketMessage(type: string, data: object) {
    if (this.webSocket == null) {
      console.error("Attempt to send web socket message while no web socket is available: " + type + "\n" + JSON.stringify(data))
      return
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