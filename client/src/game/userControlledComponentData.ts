import {EntityComponentData} from "../simulation/EntityData";
import {UserId} from "../simulation/Simulation";

export interface UserControlledComponentData extends EntityComponentData {
  userId: UserId
}