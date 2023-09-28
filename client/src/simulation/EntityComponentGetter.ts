import {Entity} from "./Entity";
import {EntityComponent} from "./EntityComponent";
import {EntityComponentData, EntityData} from "./EntityData";

export class EntityComponentGetter<COMPONENT_DATA_TYPE extends EntityComponentData> {
  readonly componentServerTypeName: string

  constructor(componentServerTypeName: string) {
    this.componentServerTypeName = componentServerTypeName
  }

  get(entity: Entity): EntityComponent<COMPONENT_DATA_TYPE> {
    return entity.getComponent<COMPONENT_DATA_TYPE>(this.componentServerTypeName)
  }

  getOrNull(entity: Entity): EntityComponent<COMPONENT_DATA_TYPE> | null {
    return entity.getComponentOrNull<COMPONENT_DATA_TYPE>(this.componentServerTypeName)
  }

  getData(entity: Entity): COMPONENT_DATA_TYPE {
    return entity.getComponentData<COMPONENT_DATA_TYPE>(this.componentServerTypeName)
  }

  getDataOrNull(entity: Entity): COMPONENT_DATA_TYPE | null {
    return entity.getComponentDataOrNull<COMPONENT_DATA_TYPE>(this.componentServerTypeName)
  }

  getDataFromEntityData(entityData: EntityData): COMPONENT_DATA_TYPE {
    return EntityData.getComponent<COMPONENT_DATA_TYPE>(entityData, this.componentServerTypeName)
  }

  getDataFromEntityDataOrNull(entityData: EntityData): COMPONENT_DATA_TYPE | null {
    return EntityData.getComponentOrNull<COMPONENT_DATA_TYPE>(entityData, this.componentServerTypeName)
  }
}