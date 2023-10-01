import {ClientSimulationData, Config, EntityId, ReplayData} from "./EntityData";
import {Entity} from "./Entity";
import {
  AlertWebSocketMessage,
  EntityComponentWebSocketMessage,
  EntityCreatedWebSocketMessage,
  EntityDestroyedWebSocketMessage
} from "../common/common";
import {deserializeDiff} from "../misc/serializationDiff";

export type UserId = string
export type UserSecret = string
export type ClientId = string
export type SimulationId = string

export class Simulation {
  private readonly replayData: ReplayData | null
  private receivedSimulationTime: number

  private smoothedSimulationTime: number

  readonly simulationId: SimulationId
  readonly configs: Config[]
  readonly configsByKeyByTypeName: Record<string, Record<string, Config>> = {}
  readonly entities: Entity[]
  readonly entitiesById: Record<string, Entity> = {}
  onSimulationDataChanged: () => void
  private sendMessageImplementation: (type: string, data: any) => void
  readonly isReplay: boolean
  isReplayPaused = false

  getCurrentSimulationTime(): number {
    return this.smoothedSimulationTime
  }

  constructor(
    initialSimulationData: ClientSimulationData,
    onSimulationDataChanged: () => void,
    sendMessageImplementation: (type: string, data: any) => void
  ) {
    this.sendMessageImplementation = sendMessageImplementation
    this.onSimulationDataChanged = onSimulationDataChanged

    this.isReplay = initialSimulationData.replayData != null
    this.replayData = initialSimulationData.replayData

    this.receivedSimulationTime = initialSimulationData.simulationTime
    this.smoothedSimulationTime = initialSimulationData.simulationTime
    this.simulationId = initialSimulationData.simulationId
    this.configs = initialSimulationData.configs

    for (let config of this.configs) {
      let configsByKey = this.configsByKeyByTypeName[config.type]

      if (!configsByKey) {
        configsByKey = {}
        this.configsByKeyByTypeName[config.type] = configsByKey
      }

      configsByKey[config.key] = config
    }

    this.entities = initialSimulationData.entities.map(entityData => new Entity(this, entityData))
    for (const entity of this.entities) {
      this.entitiesById[entity.entityId] = entity
    }

    this.handleInitialReplayData()
  }

  handleInitialReplayData() {
    if (this.replayData != null) {
      for (let sentMessage of this.replayData.sentMessages) {
        if (sentMessage.message["type"] === "EntityCreatedWebSocketMessage") {
          const entityCreatedMessage: EntityCreatedWebSocketMessage = sentMessage.message
          const characterComponent = entityCreatedMessage.entityData.components.find(it => it.type === "CharacterComponentData")

          if (characterComponent != null) {
            this.receivedSimulationTime = sentMessage.simulationTime
            this.smoothedSimulationTime = sentMessage.simulationTime
          }
        }
      }

      this.processReplayMessages()
    }
  }

  sendMessage(type: string, data: any) {
    this.sendMessageImplementation(type, data)
  }

  getEntityOrNull(entityId: EntityId): Entity | null {
    return this.entitiesById[entityId]
  }

  nextReplaySentMessageIndex = 0

  processReplayMessages() {
    const replayData = this.replayData

    if (replayData == null) {
      return
    }

    const simulationTime = this.getCurrentSimulationTime()
    let didChange = false
    while (true) {
      const nextSentMessage = replayData.sentMessages[this.nextReplaySentMessageIndex]

      if (!nextSentMessage || simulationTime < nextSentMessage.simulationTime) {
        break
      }

      ++this.nextReplaySentMessageIndex

      const messageType = nextSentMessage.message["type"]
      this.handleWebSocketMessage(messageType, nextSentMessage.message)
      didChange = true
    }

    if (didChange) {
      this.onSimulationDataChanged()
    }
  }

  getEntity(entityId: EntityId): Entity {
    const entity = this.getEntityOrNull(entityId)
    if (entity == null) {
      throw new Error("Entity not found: " + entityId)
    }
    return entity
  }

  getConfig<T extends Config>(configKey: string, serverTypeName: string): T {
    const configsByKey = this.configsByKeyByTypeName[serverTypeName]

    if (configsByKey == null) {
      throw new Error("No configs for server type name: " + serverTypeName)
    }

    const config = configsByKey[configKey]

    if (config == null) {
      throw new Error(`No config fir key: ${configKey} of type ${serverTypeName}`)
    }

    return config as any
  }


  handleWebSocketMessage(
    messageType: string,
    messageData: any
  ) {
    if (messageType === "EntityCreatedWebSocketMessage") {
      const entityCreatedMessage: EntityCreatedWebSocketMessage = messageData

      const newEntity = new Entity(this, entityCreatedMessage.entityData)
      this.entities.push(newEntity)
      this.entitiesById[newEntity.entityId] = newEntity

      this.receivedSimulationTime = entityCreatedMessage.simulationTime
    } else if (messageType === "EntityDestroyedWebSocketMessage") {
      const entityDestroyedMessage: EntityDestroyedWebSocketMessage = messageData

      const entityId = entityDestroyedMessage.entityId;
      const entity = this.getEntityOrNull(entityId)

      if (entity == null) {
        throw new Error("Can't find entity destroyed by server: " + entityId)
      }

      const index = this.entities.indexOf(entity)
      this.entities.splice(index, 1)
      delete this.entitiesById[entityId]

      this.receivedSimulationTime = entityDestroyedMessage.simulationTime
    } else if (messageType === "EntityComponentWebSocketMessage") {
      const entityComponentMessage: EntityComponentWebSocketMessage = messageData

      const componentTypeName = entityComponentMessage.componentTypeName
      const diffFromMessage = entityComponentMessage.diff

      const entityId = entityComponentMessage.entityId;

      const entity = this.getEntity(entityId)

      const component = entity.getComponent<any>(componentTypeName)

      const previousComponentData = component.data
      const newComponentData = deserializeDiff(previousComponentData, diffFromMessage)

      this.receivedSimulationTime = entityComponentMessage.simulationTime

      component.data = newComponentData
    } else if (messageType === "AlertWebSocketMessage") {
      const alertMessage: AlertWebSocketMessage = messageData

      if (alertMessage.mode === "Console") {
        console.log("From server: " + alertMessage.message)
      } else if (alertMessage.mode === "ConsoleError") {
        console.error("From server: " + alertMessage.message)
      } else {
        alert("From server: " + alertMessage.message)
      }
    }
  }

  update(deltaTime: number) {
    if (!this.isReplayPaused) {
      this.receivedSimulationTime += deltaTime
      this.smoothedSimulationTime += deltaTime
    }

    if (this.replayData == null) {
      this.smoothedSimulationTime += (this.receivedSimulationTime - this.smoothedSimulationTime) * Math.min(deltaTime * 3.0, 1.0)
    }

    this.processReplayMessages()

    if (this.replayData != null && this.smoothedSimulationTime >= this.replayData.replayGeneratedAtSimulationTime) {
      this.smoothedSimulationTime = this.replayData.replayGeneratedAtSimulationTime
    }
  }

  removeAllEntities() {
    for (let entity of this.entities) {
      delete this.entitiesById[entity.entityId]
    }
    this.entities.splice(0, this.entities.length)
  }

  seekReplayToSimulationTime(value: number) {
    const previousSimulationTime = this.smoothedSimulationTime
    this.smoothedSimulationTime = value
    this.receivedSimulationTime = value

    if (value < previousSimulationTime) {
      this.nextReplaySentMessageIndex = 0
      this.removeAllEntities()
    }

    this.processReplayMessages()
  }
}