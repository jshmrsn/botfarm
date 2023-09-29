import {Vector2} from "../misc/Vector2";
import {EntityId} from "../simulation/EntityData";
import {CharacterBodySelections} from "./CharacterComponentData";

export interface ActivityStreamEntry {
  time: number
  title: string
  message: string | null
  sourceLocation: Vector2 | null
  sourceIconPath: string | null
  actionType: string | null
  actionIconPath: string | null
  targetIconPath: string | null
  sourceEntityId: EntityId | null
  targetEntityId: EntityId | null
}