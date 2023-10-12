import {Entity} from "../../engine/simulation/Entity";
import {CharacterComponentData} from "../simulation/CharacterComponentData";
import {PositionComponentData, resolvePositionForTime} from "../../common/PositionComponentData";
import {ItemComponentData} from "../simulation/ItemComponentData";
import {lerp, Vector2} from "../../misc/Vector2";
import {AgentControlledComponent} from "../simulation/AgentControlledComponentData";
import {renderCharacter} from "./renderCharacter";
import {renderItem} from "./renderItem";
import {GameSimulationScene} from "./GameSimulationScene";
import {FogOfWarInfo} from "./renderFogWar";

export function renderEntities(
  scene: GameSimulationScene,
  didRenderDebugInfo: boolean,
  fogOfWarInfo: FogOfWarInfo | null, 
  perspectiveEntity: Entity | null
) {
  scene.fogOfWarVisibleEntities.splice(0, scene.fogOfWarVisibleEntities.length)
  const previousFogOfWarVisibleEntitiesById = scene.fogOfWarVisibleEntitiesById
  scene.fogOfWarVisibleEntitiesById = {}

  const simulationTime = scene.getCurrentSimulationTime()

  const renderContext = scene.renderContext

  const userControlledEntity = scene.dynamicState.userControlledEntity

  const playerCharacterComponent = userControlledEntity != null ? userControlledEntity.getComponentOrNull<CharacterComponentData>("CharacterComponentData") : null

  for (const entity of scene.simulation.entities) {
    const positionComponent = entity.getComponentOrNull<PositionComponentData>("PositionComponentData")

    if (positionComponent == null) {
      continue
    }

    const itemComponent = entity.getComponentOrNull<ItemComponentData>("ItemComponentData")
    const characterComponent = entity.getComponentOrNull<CharacterComponentData>("CharacterComponentData")

    let position = resolvePositionForTime(positionComponent, simulationTime)

    let fogOfWarOccludePercent
    if (fogOfWarInfo == null) {
      fogOfWarOccludePercent = 0.0
    } else {
      const distanceFromFogOfWarCenter = Vector2.distance(position, fogOfWarInfo.center)
      fogOfWarOccludePercent = Math.min(Math.max(0, (distanceFromFogOfWarCenter - fogOfWarInfo.radius) / 50.0), 1.0)
    }

    if (fogOfWarOccludePercent < 0.1) {
      scene.fogOfWarVisibleEntitiesById[entity.entityId] = entity
      scene.fogOfWarVisibleEntities.push(entity)
    }

    const fogOfWarAlpha = lerp(1.0, 0.0, fogOfWarOccludePercent)

    if (didRenderDebugInfo) {
      const spriteScale = Vector2.uniform(5.0 / 64.0)

      renderContext.renderSprite("center-debug:" + entity.entityId, {
        layer: scene.highlightRingLayer,
        textureName: "circle",
        position: position,
        scale: spriteScale,
        alpha: 0.8,
        depth: 10000
      })
    }

    if (playerCharacterComponent != null &&
      playerCharacterComponent.data.pendingInteractionTargetEntityId === entity.entityId) {
      renderContext.renderSprite("selected-entity-circle-pending-interaction:" + entity.entityId, {
        layer: scene.highlightRingLayer,
        textureName: "ring",
        position: position,
        scale: new Vector2(0.25, 0.25),
        alpha: 1,
        depth: 0
      })
    }

    if (scene.dynamicState.selectedEntityId === entity.entityId) {
      renderContext.renderSprite("selected-entity-circle:" + entity.entityId, {
        layer: scene.highlightRingLayer,
        textureName: "ring",
        position: position,
        scale: new Vector2(0.2, 0.2),
        alpha: 1,
        depth: 0
      })
    }

    if (scene.calculatedAutoInteraction?.targetEntity === entity) {
      renderContext.renderSprite("auto-interact-entity-circle:" + entity.entityId, {
        layer: scene.highlightRingLayer,
        textureName: "ring",
        position: position,
        scale: new Vector2(0.2, 0.2),
        alpha: 1,
        depth: 0
      })
    }

    if (characterComponent != null) {
      const agentControlledComponentData = AgentControlledComponent.getDataOrNull(entity)

      renderCharacter(
        scene,
        scene.mainCamera,
        positionComponent.data,
        simulationTime,
        entity,
        renderContext,
        characterComponent.data,
        agentControlledComponentData,
        position,
        fogOfWarAlpha,
        perspectiveEntity
      )
    } else if (itemComponent != null) {
      renderItem(
        scene,
        simulationTime,
        entity,
        renderContext,
        itemComponent.data,
        position,
        fogOfWarAlpha
      )
    }
  }

  if (scene.dynamicState.selectedEntityId != null) {
    const selectedVisibleFogOfWarEntity = scene.fogOfWarVisibleEntitiesById[scene.dynamicState.selectedEntityId]

    if (!selectedVisibleFogOfWarEntity) {
      scene.context.setSelectedEntityId(null)
    }
  }

  let anyChangedFogOfWarEntities = false

  for (let fogOfWarVisibleEntityId in scene.fogOfWarVisibleEntitiesById) {
    if (!previousFogOfWarVisibleEntitiesById[fogOfWarVisibleEntityId]) {
      anyChangedFogOfWarEntities = true
    }
  }

  for (let previousFogOfWarVisibleEntityId in previousFogOfWarVisibleEntitiesById) {
    if (!scene.fogOfWarVisibleEntitiesById[previousFogOfWarVisibleEntityId]) {
      anyChangedFogOfWarEntities = true
    }
  }

  if (anyChangedFogOfWarEntities) {
    scene.dynamicState.forceUpdate()
  }
}
