import {EntityComponentData} from "../simulation/EntityData";
import {Vector2} from "../misc/Vector2";


interface CollisionMapCellDebugInfo {
  center: Vector2
  occupied: boolean
  row: number
  col: number
}

interface CollisionMapDebugInfo {
  rowCount: number
  columnCount: number
  bounds: Vector2
  cellSize: Vector2
  cells: CollisionMapCellDebugInfo[]
}

export interface DebugInfoComponentData extends EntityComponentData {
  collisionMapDebugInfo: CollisionMapDebugInfo
}
