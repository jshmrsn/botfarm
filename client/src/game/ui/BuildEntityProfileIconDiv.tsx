import {EntityId} from "../../engine/simulation/EntityData";
import {Simulation} from "../../engine/simulation/Simulation";
import {GameSimulationScene} from "../scene/GameSimulationScene";
import React, {ReactElement} from "react";
import {CharacterComponent} from "../simulation/CharacterComponentData";
import {ItemComponent, ItemConfig} from "../simulation/ItemComponentData";

export function buildEntityProfileIconDiv(
  entityId: EntityId | null,
  simulation: Simulation,
  scene: GameSimulationScene,
  options?: {
    profileIconSize?: number
    alpha?: number,
    fallbackItemConfigKey?: string
  }
): ReactElement | null {
  options = options || {}
  const profileIconSize = options.profileIconSize || 50
  const alpha = options.alpha ?? 1
  const entity = entityId != null ? simulation.getEntityOrNull(entityId) : null
  const fallbackItemConfigKey = options.fallbackItemConfigKey

  if (!entity) {
    if (fallbackItemConfigKey) {
      const itemConfig = simulation.getConfig<ItemConfig>(fallbackItemConfigKey, "ItemConfig")

      return <div
        key={"item-icon:" + entityId}
        style={{
          flexBasis: profileIconSize,
          height: profileIconSize,
          width: profileIconSize,
          alignItems: "center",
          display: "flex"
        }}
      >
        <img
          key={"layer"}
          src={itemConfig.iconUrl}
          alt={"Source profile icon layer"}
          style={{
            opacity: alpha,
            maxHeight: profileIconSize,
            maxWidth: profileIconSize
          }}
        />
      </div>
    }

    return null
  }

  const itemComponent = ItemComponent.getOrNull(entity)

  const bodySelections = CharacterComponent.getDataOrNull(entity)?.bodySelections

  if (bodySelections != null) {
    const layers = scene.assetLoader.getProfileIconLayerUrlsForBodySelections(bodySelections)

    if (layers.length === 0) {
      return null
    }

    return <div
      key={"profile-icon-layers:" + entityId}
      style={{
        flexBasis: profileIconSize,
        height: profileIconSize,
        width: profileIconSize
      }}
    >
      <div
        key={"relative-container"}
        style={{
          width: 0,
          height: 0,
          position: "relative"
        }}
      >
        {layers.map((layerUrl, layerIndex) => {
          return <img
            key={"layer:" + layerIndex}
            src={layerUrl}
            alt={"Source profile icon layer"}
            style={{
              opacity: alpha,
              height: profileIconSize,
              width: profileIconSize,
              position: "absolute"
            }}
          />
        })}
      </div>
    </div>
  } else if (itemComponent != null) {
    const itemConfig = simulation.getConfig<ItemConfig>(itemComponent.data.itemConfigKey, "ItemConfig")

    return <div
      key={"item-icon:" + entityId}
      style={{
        flexBasis: profileIconSize,
        height: profileIconSize,
        width: profileIconSize,
        alignItems: "center",
        display: "flex"
      }}
    >
      <img
        key={"layer"}
        src={itemConfig.iconUrl}
        alt={"Source profile icon layer"}
        style={{
          opacity: alpha,
          maxHeight: profileIconSize,
          maxWidth: profileIconSize
        }}
      />
    </div>
  } else {
    return null
  }
}