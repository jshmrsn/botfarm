import {EntityComponentData} from "../simulation/EntityData";
import {ActivityStreamEntry} from "./ActivityStreamEntry";
import {EntityComponentGetter} from "../simulation/EntityComponentGetter";
import {Vector2} from "../misc/Vector2";

export interface ActivityStreamComponentData extends EntityComponentData {
  activityStream: ActivityStreamEntry[]
}

export const ActivityStreamComponent = new EntityComponentGetter<ActivityStreamComponentData>("ActivityStreamComponentData")


