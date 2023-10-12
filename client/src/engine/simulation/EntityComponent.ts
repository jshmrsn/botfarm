import {EntityComponentData} from "./EntityData";
import {Entity} from "./Entity";


export class EntityComponent<T extends EntityComponentData> {
  data: T
  readonly entity: Entity
  readonly serverTypeName: string

  constructor(entity: Entity, initialData: T) {
    this.entity = entity
    this.data = initialData
    this.serverTypeName = initialData.type
  }

  ingestNewData(data: T) {
    this.data = data
  }
}