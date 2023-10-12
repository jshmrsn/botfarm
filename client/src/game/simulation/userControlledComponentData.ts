import {EntityComponentData} from "../../engine/simulation/EntityData";
import {UserId} from "../../engine/simulation/Simulation";
import {EntityComponentGetter} from "../../engine/simulation/EntityComponentGetter";

export interface UserControlledComponentData extends EntityComponentData {
  userId: UserId
}

export const UserControlledComponent = new EntityComponentGetter<UserControlledComponentData>("UserControlledComponentData")