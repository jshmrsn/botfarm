import React, {ReactElement, useState} from "react";
import {DynamicState} from "./DynamicState";
import {Entity} from "../simulation/Entity";
import styled from "styled-components";
import {CharacterComponent} from "../game/CharacterComponentData";
import {buildDivForProfileIconLayers} from "./BuildDivForProfileIconLayers";

export interface TopHeaderProps {
  dynamicState: DynamicState
  useMobileLayout: boolean
  perspectiveEntity: Entity | null
  userControlledEntity: Entity | null
  forceUpdateCounter: number
}

const ListButton = styled.div`
  display: flex;
  flex-direction: row;
  gap: 10px;
  align-items: center;
  border-radius: 3px;
  padding: 5px;
  padding-left: 10px;
  background-color: rgba(255, 255, 255, 0.5);

  &:hover {
    background-color: rgba(255, 255, 255, 1);
  }

  cursor: pointer;
`;

export function TopHeader(props: TopHeaderProps): ReactElement | null {
  const dynamicState = props.dynamicState
  const simulation = dynamicState.simulation
  const scene = dynamicState.scene
  const [forceUpdateCounter, setForceUpdateCounter] = useState(0)

  setTimeout(() => {
    setForceUpdateCounter(forceUpdateCounter + 1)
  }, 0)

  if (simulation == null || scene == null) {
    return null
  }

  if (dynamicState.userControlledEntity != null) {
    return null
  }

  const renderedCharacters = scene.fogOfWarVisibleEntities
    .filter(entity => CharacterComponent.getDataOrNull(entity) != null &&
      entity != dynamicState.userControlledEntity)
    .map(entity => {
      const characterComponentData = CharacterComponent.getData(entity) != null

      return {
        characterComponentData: characterComponentData,
        entity: entity
      }
    })

  return <div
    key="top-header"
    style={{
      display: "flex",
      flexDirection: "row",
      position: "absolute",
      top: 5,
      left: 100,
      right: 100,
      gap: 10,
      pointerEvents: "none",
      justifyContent: "center"
    }}
  >
    <div
      key="character-container"
      style={{
        display: "flex",
        flexDirection: "row",
        alignItems: "center",
        backgroundColor: "rgba(255, 255, 255, 0.5)",
        flexBasis: 50,
        backdropFilter: "blur(5px)",
        WebkitBackdropFilter: "blur(5px)",
        borderRadius: 5,
        gap: 10,
        padding: 4
      }}
    >
      {renderedCharacters.map(renderedCharacter => {
        return <ListButton
          key={"rendered-character:" + renderedCharacter.entity.entityId}
          style={{
            display: "flex",
            flexDirection: "row",
            padding: 0,
            alignItems: "center",
            justifyContent: "center",
            height: 60,
            width: 60,
            backdropFilter: "blur(5px)",
            WebkitBackdropFilter: "blur(5px)",
            borderRadius: 5,
            gap: 10,
            pointerEvents: "auto",
            paddingBottom: 10,
            backgroundColor: dynamicState.perspectiveEntity === renderedCharacter.entity ?
              "#228BE6" : undefined
          }}
          onClick={() => {
            if (dynamicState.perspectiveEntity === renderedCharacter.entity) {
              dynamicState.setPerspectiveEntityIdOverride(null)
            } else {
              dynamicState.setPerspectiveEntityIdOverride(renderedCharacter.entity.entityId)
            }
          }}
        >
          {buildDivForProfileIconLayers(
            renderedCharacter.entity.entityId,
            simulation,
            scene
          )}
        </ListButton>
      })}
    </div>
  </div>
}