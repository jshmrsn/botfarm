import {Vector2} from "../../misc/Vector2";
import {EntityId} from "../../engine/simulation/EntityData";
import {ActionType} from "./CharacterComponentData";

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
  observedByEntityIds: EntityId[] | null
  spawnedItems: SpawnedItemEntity[] | null

  agentReason: string | null
  agentUniqueActionId: string | null


  actionType: ActionType | null
  actionResultType: string | null
  actionItemConfigKey: string | null

  sourceItemConfigKey: string | null
  sourceLocation: Vector2 | null
  sourceEntityId: EntityId | null

  targetItemConfigKey: string | null
  targetEntityId: EntityId | null

  resultItemConfigKey: string | null
  resultEntityId: string | null
}