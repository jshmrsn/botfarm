import {GameSimulationScene} from "./GameSimulationScene";
import {RenderContext} from "../../common/RenderContext";
import {Vector2} from "../../misc/Vector2";

export function renderWorldBounds(
  scene: GameSimulationScene,
  renderContext: RenderContext
) {
  const worldBoundsCenter = Vector2.timesScalar(scene.simulation.worldBounds, 0.5)
  const openPercent = 0.5

  const extraFogOfWarSize = 10000.0
  const extraFogOfWarSideHeight = extraFogOfWarSize * 2 + worldBoundsCenter.y / openPercent
  const extraFogOfWarTextureSize = 128.0
  renderContext.renderSprite("bounds-left", {
    layer: scene.highlightRingLayer,
    textureName: "black-square",
    position: Vector2.plus(worldBoundsCenter, new Vector2(-(worldBoundsCenter.x / openPercent) * 0.5 - extraFogOfWarSize * 0.5, 0)),
    scale: new Vector2(extraFogOfWarSize / extraFogOfWarTextureSize, extraFogOfWarSideHeight / extraFogOfWarTextureSize),
    alpha: 0.5,
    depth: 10000
  })

  renderContext.renderSprite("bounds-right", {
    layer: scene.highlightRingLayer,
    textureName: "black-square",
    position: Vector2.plus(worldBoundsCenter, new Vector2((worldBoundsCenter.x / openPercent) * 0.5 + extraFogOfWarSize * 0.5, 0)),
    scale: new Vector2(extraFogOfWarSize / extraFogOfWarTextureSize, extraFogOfWarSideHeight / extraFogOfWarTextureSize),
    alpha: 0.5,
    depth: 10000
  })

  renderContext.renderSprite("bounds-top", {
    layer: scene.highlightRingLayer,
    textureName: "black-square",
    position: Vector2.plus(worldBoundsCenter, new Vector2(0, (worldBoundsCenter.y / openPercent) * 0.5 + extraFogOfWarSize * 0.5)),
    scale: new Vector2(worldBoundsCenter.x / openPercent / extraFogOfWarTextureSize, extraFogOfWarSize / extraFogOfWarTextureSize),
    alpha: 0.5,
    depth: 10000
  })

  renderContext.renderSprite("bounds-bottom", {
    layer: scene.highlightRingLayer,
    textureName: "black-square",
    position: Vector2.plus(worldBoundsCenter, new Vector2(0, -(worldBoundsCenter.y / openPercent) * 0.5 - extraFogOfWarSize * 0.5)),
    scale: new Vector2(worldBoundsCenter.x / openPercent / extraFogOfWarTextureSize, extraFogOfWarSize / extraFogOfWarTextureSize),
    alpha: 0.5,
    depth: 10000
  })
}