import {ClientSimulationData, Config, EntityId, ReplayData} from "./EntityData";
import {Entity} from "./Entity";
import {deserializeDiff} from "../../misc/serializationDiff";
import {
  AlertWebSocketMessage,
  EntityComponentWebSocketMessage,
  EntityCreatedWebSocketMessage,
  EntityDestroyedWebSocketMessage
} from "./handleWebSocketMessage";

export type UserId = string
export type UserSecret = string
export type ClientId = string
export type SimulationId = string

export interface SimulationContext {
  clientId: ClientId
  userId: UserId
  onSimulationDataChanged: () => void
  sendMessageImplementation: (type: string, data: any) => void
  initialSimulationData: ClientSimulationData
}

export class Simulation {
  private readonly replayData: ReplayData | null
  private receivedSimulationTime: number

  private smoothedSimulationTime: number

  readonly simulationId: SimulationId
  readonly configs: Config[]
  readonly configsByKeyByTypeName: Record<string, Record<string, Config>> = {}
  readonly entities: Entity[]
  readonly entitiesById: Record<string, Entity> = {}
  readonly onSimulationDataChanged: () => void
  private sendMessageImplementation: (type: string, data: any) => void
  readonly isReplay: boolean
  isReplayPaused = false

  readonly clientId: ClientId

  readonly context: SimulationContext

  getCurrentSimulationTime(): number {
    return this.smoothedSimulationTime
  }

  constructor(
    context: SimulationContext,
  ) {
    this.context = context
    this.clientId = context.clientId
    this.sendMessageImplementation = context.sendMessageImplementation
    this.onSimulationDataChanged = context.onSimulationDataChanged

    this.isReplay = context.initialSimulationData.replayData != null
    this.replayData = context.initialSimulationData.replayData

    this.receivedSimulationTime = context.initialSimulationData.simulationTime
    this.smoothedSimulationTime = context.initialSimulationData.simulationTime
    this.simulationId = context.initialSimulationData.simulationId
    this.configs = context.initialSimulationData.configs

    for (let config of this.configs) {
      let configsByKey = this.configsByKeyByTypeName[config.type]

      if (!configsByKey) {
        configsByKey = {}
        this.configsByKeyByTypeName[config.type] = configsByKey
      }

      configsByKey[config.key] = config
    }

    this.entities = context.initialSimulationData.entities.map(entityData => new Entity(this, entityData))
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

      const existingEntity = this.entitiesById[entityCreatedMessage.entityData.entityId]

      if (existingEntity != null) {
        // jshmrsn: Adding this case for certain error react-dev-server error reload cases.
        // Clearing entities in advanced would be more robust
        for (let component of entityCreatedMessage.entityData.components) {
          const componentType = component.type
          existingEntity.getComponent(componentType).ingestNewData(component)
        }
      } else {
        const newEntity = new Entity(this, entityCreatedMessage.entityData)
        this.entities.push(newEntity)
        this.entitiesById[newEntity.entityId] = newEntity
      }

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