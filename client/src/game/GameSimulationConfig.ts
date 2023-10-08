import {Config} from "../simulation/EntityData";
import {Vector2} from "../misc/Vector2";

export interface GameSimulationConfig extends Config {
  worldBounds: Vector2
}