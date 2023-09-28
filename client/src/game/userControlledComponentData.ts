import {EntityComponentData} from "../simulation/EntityData";
import {UserId} from "../simulation/Simulation";
import {EntityComponentGetter} from "../simulation/EntityComponentGetter";

export interface UserControlledComponentData extends EntityComponentData {
  userId: UserId
}

export const UserControlledComponent = new EntityComponentGetter<UserControlledComponentData>("UserControlledComponentData")