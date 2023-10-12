import React, {ReactElement, useState} from "react";
import {DynamicState} from "./DynamicState";
import {Entity} from "../../engine/simulation/Entity";
import styled from "styled-components";
import {CharacterComponent} from "../simulation/CharacterComponentData";
import {buildIconDiv} from "./BuildIconDiv";
import {ActionIcon} from "@mantine/core";
import {IconEye} from "@tabler/icons-react";

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
  //align-items: center;
  padding: 5px;
  padding-left: 10px;;

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

  const renderedCharacters = simulation.entities // scene.fogOfWarVisibleEntities
    .filter(entity => CharacterComponent.getDataOrNull(entity) != null)
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
        backgroundColor: "rgba(255, 255, 255, 0.4)",
        flexBasis: 50,
        backdropFilter: "blur(5px)",
        WebkitBackdropFilter: "blur(5px)",
        borderRadius: 5,
        gap: 8,
        padding: 6,
        pointerEvents: "auto"
      }}
    >
      <ActionIcon
        size={50}
        variant={dynamicState.perspectiveEntity == null ? "filled" : "subtle"}
        color={dynamicState.perspectiveEntity == null ? "blue" : "gray"}
        onClick={() => {
          if (dynamicState.perspectiveEntity == null) {
            dynamicState.context.setIsInForceSpectateMode(false)
          } else {
            dynamicState.context.setIsInForceSpectateMode(true)
          }

          dynamicState.setPerspectiveEntityIdOverride(null)
        }}
      >
        <IconEye size={25} color={dynamicState.perspectiveEntity !== null ? "rgba(0, 0, 0, 0.5)" : undefined}/>
      </ActionIcon>

      {renderedCharacters.map(renderedCharacter => {
        const entityId = renderedCharacter.entity.entityId;
        return <ListButton
          key={"rendered-character:" + entityId}
          style={{
            display: "flex",
            flexDirection: "row",
            padding: 4,
            alignItems: "center",
            justifyContent: "center",

            // backdropFilter: "blur(5px)",
            // WebkitBackdropFilter: "blur(5px)",
            borderRadius: 5,
            pointerEvents: "auto",
            backgroundColor: dynamicState.perspectiveEntity === renderedCharacter.entity ?
              "#228BE6" : "rgba(255, 255, 255, 0.4)",
            outlineColor: "white",
            outlineWidth: 2,
            outlineStyle: dynamicState.selectedEntityId === entityId ?
              "solid" : "none"
          }}
          onClick={() => {
            if (dynamicState.selectedEntityId === entityId) {
              dynamicState.selectEntity(null)
            } else {
              dynamicState.selectEntity(entityId)
              scene.centerCameraOnEntityId(entityId)
            }
          }}
        >
          {buildIconDiv(
            entityId,
            simulation,
            scene,
            {
              profileIconSize: 40,
              alpha: scene.fogOfWarVisibleEntitiesById[entityId] ? 1.0 : 0.5
            }
          )}
        </ListButton>
      })}
    </div>
  </div>
}