import {EntityId} from "../simulation/EntityData";
import {Simulation} from "../simulation/Simulation";
import {GameSimulationScene} from "../game/GameSimulationScene";
import React, {ReactElement} from "react";
import {CharacterComponent} from "../game/CharacterComponentData";

export function buildDivForProfileIconLayers(
  entityId: EntityId | null,
  simulation: Simulation,
  phaserScene: GameSimulationScene
): ReactElement | null {
  const entity = entityId != null ? simulation.getEntityOrNull(entityId) : null

  const bodySelections = entity != null
    ? CharacterComponent.getDataOrNull(entity)?.bodySelections
    : null

  if (bodySelections == null) {
    return null
  }

  const layers = phaserScene.getProfileIconLayerUrlsForBodySelections(bodySelections)

  if (layers.length === 0) {
    return null
  }

  const profileIconSize = 50;

  return <div
    key={"profile-icon-layers"}
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
            height: profileIconSize,
            width: profileIconSize,
            position: "absolute"
          }}
        />
      })}
    </div>
  </div>
}