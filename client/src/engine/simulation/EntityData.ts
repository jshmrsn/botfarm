import {SimulationId} from "./Simulation";

export interface EntityComponentData {
  type: string
}

export type EntityId = string

export class EntityData {
  entityId: EntityId = ""
  components: EntityComponentData[] = []

  static getComponentOrNull<T>(entity: EntityData, serverSerializationTypeName: string): T | null {
    for (let component of entity.components) {
      if (component.type === serverSerializationTypeName) {
        return component as T
      }
    }

    return null
  }

  static getComponent<T>(entity: EntityData, serverSerializationTypeName: string): T {
    const component = this.getComponentOrNull<T>(entity, serverSerializationTypeName)
    if (component == null) {
      throw new Error("Component not found: " + serverSerializationTypeName)
    }
    return component
  }
}

export interface Config {
  type: string
  key: string
}

export interface ClientSimulationData {
  simulationId: SimulationId
  entities: EntityData[]
  configs: Config[]
  simulationTime: number
  replayData: ReplayData | null
}

interface ReplaySentMessage {
  message: any
  simulationTime: number
}


export interface ScenarioInfo {
  identifier: string
  gameIdentifier: string
  name: string
  description: string | null
}

export interface ReplayData {
  compatibilityVersion: number
  simulationStartedAtUnixTime: number
  replayGeneratedAtSimulationTime: number
  scenarioInfo: ScenarioInfo
  configs: Config[]
  simulationId: SimulationId,
  sentMessages: ReplaySentMessage[]
}
