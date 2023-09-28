import {EntityComponentData} from "../simulation/EntityData";
import {ActivityStreamEntry} from "./ActivityStreamEntry";
import {EntityComponentGetter} from "../simulation/EntityComponentGetter";

export interface ActivityStreamComponentData extends EntityComponentData {
  activityStream: ActivityStreamEntry[]
}

export const ActivityStreamComponent = new EntityComponentGetter<ActivityStreamComponentData>("ActivityStreamComponentData")