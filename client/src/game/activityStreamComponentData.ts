import {EntityComponentData} from "../simulation/EntityData";
import {ActivityStreamEntry} from "./ActivityStreamEntry";

export interface ActivityStreamComponentData extends EntityComponentData {
  activityStream: ActivityStreamEntry[]
}