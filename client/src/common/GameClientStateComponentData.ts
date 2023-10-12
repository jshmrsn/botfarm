import {EntityComponentData, EntityId} from "../engine/simulation/EntityData";

export interface GameClientStateComponentData extends EntityComponentData {
  perspectiveEntityIdOverride: EntityId | null
  shouldSpectateByDefault: boolean
}