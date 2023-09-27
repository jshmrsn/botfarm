import {EntityComponentData, EntityData} from "./EntityData";
import {Simulation} from "./Simulation";
import {EntityComponent} from "./EntityComponent";

export class Entity {
  data: EntityData

  readonly components: EntityComponent<any>[]
  readonly componentByServerType: Record<string, EntityComponent<any>>

  readonly simulation: Simulation;
  readonly entityId: string

  constructor(
    simulation: Simulation,
    initialData: EntityData
  ) {
    this.data = initialData
    this.simulation = simulation
    this.components = initialData.components.map(componentData => new EntityComponent<any>(this, componentData))
    this.entityId = initialData.entityId

    const componentByServerType: Record<string, EntityComponent<any>> = {}
    for (let component of this.components) {
      componentByServerType[component.data.type] = component
    }

    this.componentByServerType = componentByServerType
  }

  getComponentDataOrNull<T extends EntityComponentData>(serverTypeName: string): T | null {
    const component = this.getComponentOrNull<T>(serverTypeName)

    if (component == null) {
      return null
    }

    return component.data
  }

  getComponentData<T extends EntityComponentData>(serverTypeName: string): T {
    return this.getComponent<T>(serverTypeName).data
  }

  getComponentOrNull<T extends EntityComponentData>(serverTypeName: string): EntityComponent<T> | null {
    return this.componentByServerType[serverTypeName]
  }

  getComponent<T extends EntityComponentData>(serverTypeName: string): EntityComponent<T> {
    const result = this.getComponentOrNull<T>(serverTypeName)

    if (result == null) {
      throw new Error(`Component not found on entity: ${serverTypeName} (${this.entityId})`)
    }

    return result
  }

  handleNewComponentData(data: EntityData) {
    this.data = data
  }
}