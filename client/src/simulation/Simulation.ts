import {ClientSimulationData, Config, EntityId} from "./EntityData";
import {Entity} from "./Entity";
import {
  AlertWebSocketMessage,
  EntityComponentWebSocketMessage,
  EntityCreatedWebSocketMessage,
  EntityDestroyedWebSocketMessage
} from "../common/common";
import {deserializeDiff} from "../misc/serializationDiff";

export type UserId = string
export type ClientId = string
export type SimulationId = string

export class Simulation {
  private readonly initialData: ClientSimulationData
  private receivedSimulationTime: number

  private smoothedSimulationTime: number

  readonly simulationId: SimulationId
  readonly configs: Config[]
  readonly configsByKeyByTypeName: Record<string, Record<string, Config>> = {}
  readonly entities: Entity[]
  readonly entitiesById: Record<string, Entity> = {}
  onSimulationDataChanged: (newData: ClientSimulationData) => void
  private sendMessageImplementation: (type: string, data: any) => void

  getCurrentSimulationTime(): number {
    return this.smoothedSimulationTime
  }

  constructor(
    initialSimulationData: ClientSimulationData,
    onSimulationDataChanged: (newData: ClientSimulationData) => void,
    sendMessageImplementation: (type: string, data: any) => void
  ) {
    this.sendMessageImplementation = sendMessageImplementation
    this.onSimulationDataChanged = onSimulationDataChanged
    this.initialData = initialSimulationData
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
  }

  sendMessage(type: string, data: any) {
    this.sendMessageImplementation(type, data)
  }

  getEntityOrNull(entityId: EntityId): Entity | null {
    return this.entitiesById[entityId]
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
      console.log("Handling EntityCreatedWebSocketMessage", entityCreatedMessage)

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
      // console.log("componentTypeNameFromMessage", componentTypeNameFromMessage)
      const diffFromMessage = entityComponentMessage.diff
      // console.log("diffFromMessage", diffFromMessage)

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
    this.receivedSimulationTime += deltaTime
    this.smoothedSimulationTime += deltaTime
    this.smoothedSimulationTime += (this.receivedSimulationTime - this.smoothedSimulationTime) * Math.min(deltaTime * 3.0, 1.0)
  }
}