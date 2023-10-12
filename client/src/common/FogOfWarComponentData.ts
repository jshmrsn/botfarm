import {EntityComponentData} from "../engine/simulation/EntityData";
import {EntityComponentGetter} from "../engine/simulation/EntityComponentGetter";

export interface FogOfWarComponentData extends EntityComponentData {
  isStale: boolean
}

export const FogOfWarComponent = new EntityComponentGetter<FogOfWarComponentData>("FogOfWarComponentData")


