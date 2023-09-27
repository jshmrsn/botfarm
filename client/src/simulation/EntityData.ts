import {ActivityStreamEntry} from "../game/ActivityStreamEntry";

export interface EntityComponentData {
  type: string
}

export class EntityData {
  entityId: string = ""
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
  simulationId: string
  entities: EntityData[]
  configs: Config[]
  simulationTime: number
}

