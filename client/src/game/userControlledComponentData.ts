import {EntityComponentData} from "../simulation/EntityData";

export interface UserControlledComponentData extends EntityComponentData {
  userId: string
}