import {EntityComponentData} from "../../engine/simulation/EntityData";
import {ActivityStreamEntry} from "./ActivityStreamEntry";
import {EntityComponentGetter} from "../../engine/simulation/EntityComponentGetter";

export interface ActivityStreamComponentData extends EntityComponentData {
  activityStream: ActivityStreamEntry[]
}

export const ActivityStreamComponent = new EntityComponentGetter<ActivityStreamComponentData>("ActivityStreamComponentData")


