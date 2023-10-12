import {Entity} from "../../engine/simulation/Entity";
import {RenderContext} from "../../engine/RenderContext";
import {GrowerComponent, ItemComponentData, ItemConfig, KillableComponent} from "../simulation/ItemComponentData";
import {clampZeroOne, lerp, Vector2} from "../../misc/Vector2";
import {SpriteConfig} from "../../common/common";
import {GameSimulationScene} from "./GameSimulationScene";


export function renderItem(
  scene: GameSimulationScene,
  simulationTime: number,
  entity: Entity,
  renderContext: RenderContext,
  itemComponent: ItemComponentData,
  position: Vector2,
  fogOfWarAlpha: number
) {
  const killedAtTime = KillableComponent.getDataOrNull(entity)?.killedAtTime
  const grower = GrowerComponent.getDataOrNull(entity)

  const itemConfig = scene.getConfig<ItemConfig>(itemComponent.itemConfigKey, "ItemConfig")
  const spriteConfigKey = itemConfig.spriteConfigKey
  const spriteConfig = scene.getConfig<SpriteConfig>(spriteConfigKey, "SpriteConfig")

  const timeSinceKilled = killedAtTime != null ? Math.max(0, simulationTime - killedAtTime) : null

  const deathAnimationTime = 0.5

  const baseDepth = scene.calculateDepthForPosition(position)

  renderContext.renderSprite("item_" + entity.entityId + "_" + spriteConfig.key, {
    layer: scene.mainLayer,
    depth: baseDepth + spriteConfig.depthOffset,
    textureName: spriteConfigKey,
    position: Vector2.plus(position, spriteConfig.baseOffset),
    animation: null,
    scale: timeSinceKilled != null
      ? Vector2.timesScalar(spriteConfig.baseScale, lerp(1, 0.7, timeSinceKilled / deathAnimationTime))
      : spriteConfig.baseScale,
    alpha: (timeSinceKilled != null ?
      lerp(1, 0.0, timeSinceKilled / deathAnimationTime)
      : 1.0) * fogOfWarAlpha
  })

  if (grower != null && grower.activeGrowth != null) {
    const activeGrowthItemConfig = scene.getConfig<ItemConfig>(grower.activeGrowth.itemConfigKey, "ItemConfig")
    const growableConfig = activeGrowthItemConfig.growableConfig

    if (growableConfig == null) {
      throw new Error("growableConfig is null for an active growth: " + activeGrowthItemConfig.key)
    }

    const timeToGrow = growableConfig?.timeToGrow ?? 1
    const growAge = Math.max(0, simulationTime - grower.activeGrowth.startTime)
    const growPercent = clampZeroOne(growAge / timeToGrow)

    const growIntoItemConfig = scene.getConfig<ItemConfig>(growableConfig.growsIntoItemConfigKey, "ItemConfig")
    const growIntoSpriteConfig = scene.getConfig<SpriteConfig>(growIntoItemConfig.spriteConfigKey, "SpriteConfig")

    renderContext.renderSprite("item_" + entity.entityId + "_" + growIntoSpriteConfig.key + "_activeGrowth", {
      layer: scene.mainLayer,
      depth: baseDepth + 0.001,
      textureName: growIntoSpriteConfig.key,
      position: Vector2.plus(position, spriteConfig.baseOffset),
      animation: null,
      scale: Vector2.timesScalar(growIntoSpriteConfig.baseScale, lerp(0.5, 1.0, growPercent)),
      alpha: lerp(0.5, 1.0, growPercent) * fogOfWarAlpha
    })
  }

  if (scene.dynamicState.selectedEntityId === entity.entityId ||
    scene.calculatedAutoInteraction?.targetEntity === entity) {
    renderContext.renderText("item-name:" + entity.entityId, {
      depth: baseDepth,
      layer: scene.characterNameLayer,
      text: itemConfig.name + (itemComponent.amount > 1 ? ` (x${itemComponent.amount})` : ""),
      strokeThickness: 3,
      fontSize: 20,
      useUiCamera: false,
      position: Vector2.plus(position, new Vector2(0, 15)),
      origin: new Vector2(0.5, 0),
      scale: Vector2.timesScalar(Vector2.one, 1.0 / scene.mainCamera.zoom)
    })
  }
}
  