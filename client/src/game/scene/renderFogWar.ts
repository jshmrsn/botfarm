import {RenderContext} from "../../engine/RenderContext";
import {Vector2} from "../../misc/Vector2";
import {GameSimulationScene} from "./GameSimulationScene";

export interface FogOfWarInfo {
  center: Vector2
  radius: number
}

export function renderFogOfWar(
  scene: GameSimulationScene,
  fogOfWarInfo: FogOfWarInfo,
  renderContext: RenderContext
) {
  const fogOfWarDistance = fogOfWarInfo.radius * 2.0
  const fogOfWarTextureSize = 2048.0
  const fogOfWarTextureOpenPercent = 1024.0 / fogOfWarTextureSize

  const spriteScale = Vector2.uniform(fogOfWarDistance / fogOfWarTextureSize / fogOfWarTextureOpenPercent)

  renderContext.renderSprite("fog-of-war", {
    layer: scene.highlightRingLayer,
    textureName: "fog-of-war",
    position: fogOfWarInfo.center,
    scale: spriteScale,
    alpha: 0.5,
    depth: 10000
  })

  const extraFogOfWarSize = 10000.0
  const extraFogOfWarSideHeight = extraFogOfWarSize * 2 + fogOfWarDistance / fogOfWarTextureOpenPercent
  const extraFogOfWarTextureSize = 128.0
  renderContext.renderSprite("fog-of-war-left", {
    layer: scene.highlightRingLayer,
    textureName: "black-square",
    position: Vector2.plus(fogOfWarInfo.center, new Vector2(-(fogOfWarDistance / fogOfWarTextureOpenPercent) * 0.5 - extraFogOfWarSize * 0.5, 0)),
    scale: new Vector2(extraFogOfWarSize / extraFogOfWarTextureSize, extraFogOfWarSideHeight / extraFogOfWarTextureSize),
    alpha: 0.5,
    depth: 10000
  })

  renderContext.renderSprite("fog-of-war-right", {
    layer: scene.highlightRingLayer,
    textureName: "black-square",
    position: Vector2.plus(fogOfWarInfo.center, new Vector2((fogOfWarDistance / fogOfWarTextureOpenPercent) * 0.5 + extraFogOfWarSize * 0.5, 0)),
    scale: new Vector2(extraFogOfWarSize / extraFogOfWarTextureSize, extraFogOfWarSideHeight / extraFogOfWarTextureSize),
    alpha: 0.5,
    depth: 10000
  })

  renderContext.renderSprite("fog-of-war-top", {
    layer: scene.highlightRingLayer,
    textureName: "black-square",
    position: Vector2.plus(fogOfWarInfo.center, new Vector2(0, (fogOfWarDistance / fogOfWarTextureOpenPercent) * 0.5 + extraFogOfWarSize * 0.5)),
    scale: new Vector2(fogOfWarDistance / fogOfWarTextureOpenPercent / extraFogOfWarTextureSize, extraFogOfWarSize / extraFogOfWarTextureSize),
    alpha: 0.5,
    depth: 10000
  })

  renderContext.renderSprite("fog-of-war-bottom", {
    layer: scene.highlightRingLayer,
    textureName: "black-square",
    position: Vector2.plus(fogOfWarInfo.center, new Vector2(0, -(fogOfWarDistance / fogOfWarTextureOpenPercent) * 0.5 - extraFogOfWarSize * 0.5)),
    scale: new Vector2(fogOfWarDistance / fogOfWarTextureOpenPercent / extraFogOfWarTextureSize, extraFogOfWarSize / extraFogOfWarTextureSize),
    alpha: 0.5,
    depth: 10000
  })
}
