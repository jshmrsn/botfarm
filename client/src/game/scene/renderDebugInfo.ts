import {DebugInfoComponentData} from "../simulation/DebugInfoComponentData";
import {Vector2} from "../../misc/Vector2";
import {GameSimulationScene} from "./GameSimulationScene";

export function renderDebugInfo(
  scene: GameSimulationScene
): boolean {
  const simulation = scene.simulation
  const mainCamera = scene.mainCamera

  const debugInfoEntity = simulation.getEntityOrNull("debug-info")
  if (debugInfoEntity == null) {
    return false
  }

  const debugInfoComponentData = debugInfoEntity.getComponent<DebugInfoComponentData>("DebugInfoComponentData").data
  const collisionMapDebugInfo = debugInfoComponentData.collisionMapDebugInfo

  const cameraScroll = new Vector2(mainCamera.scrollX, mainCamera.scrollY)
  const canvasSize = new Vector2(scene.game.canvas.width, scene.game.canvas.height)
  const zoom = mainCamera.zoom

  const cameraCenter = Vector2.plus(cameraScroll, Vector2.timesScalar(canvasSize, 0.5))

  const clampedCanvasSize = new Vector2(Math.min(canvasSize.x / zoom, 1000), Math.min(canvasSize.y / zoom, 1000))

  const cameraMin = Vector2.minus(cameraCenter, new Vector2(clampedCanvasSize.x * 0.5, clampedCanvasSize.y * 0.5))
  const cameraMax = Vector2.plus(cameraCenter, new Vector2(clampedCanvasSize.x * 0.5, clampedCanvasSize.y * 0.5))
  const spriteScale = Vector2.timesScalar(collisionMapDebugInfo.cellSize, 1.0 / 64.0)

  let cellIndex = 0
  for (let cell of collisionMapDebugInfo.cells) {
    const center = cell.center

    if (center.x > cameraMin.x &&
      center.x < cameraMax.x &&
      center.y > cameraMin.y &&
      center.y < cameraMax.y) {
      scene.renderContext.renderSprite("cell:" + cellIndex, {
        layer: scene.observationRingsLayer,
        textureName: "circle",
        position: cell.center,
        scale: spriteScale,
        alpha: cell.occupiedFlags.length > 0
          ? !cell.occupiedFlags.includes("Walking")
            ? 0.5
            : 0.9
          : 0.25,
        depth: 0
      })
    }

    ++cellIndex
  }

  return collisionMapDebugInfo.cells.length > 0
}
