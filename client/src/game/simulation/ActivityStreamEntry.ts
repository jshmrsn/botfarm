import {Vector2} from "../../misc/Vector2";
import {EntityId} from "../../engine/simulation/EntityData";

export interface SpawnedItemEntity {
  name: string
  amount: number
  itemConfigKey: string
  entityId: EntityId
}

export interface ActivityStreamEntry {
  time: number
  title: string
  message: string | null
  longMessage: string | null
  onlyShowForPerspectiveEntity: boolean
  readonly sourceLocation: Vector2 | null
  sourceIconPath: string | null
  actionType: string | null
  actionIconPath: string | null
  targetIconPath: string | null
  sourceEntityId: EntityId | null
  targetEntityId: EntityId | null
  observedByEntityIds: EntityId[] | null
  spawnedItems: SpawnedItemEntity[] | null
}