import {ActivityStreamEntry} from "../game/ActivityStreamEntry";
import {ActionIcon, Button, Text} from "@mantine/core";
import React, {ReactElement, useState} from "react";
import {IconArrowDown, IconMapPin} from "@tabler/icons-react";
import {DynamicState} from "./DynamicState";
import styled from "styled-components";
import {formatSeconds} from "./ReplayControls";
import {Entity} from "../simulation/Entity";
import {buildEntityProfileIconDiv} from "./BuildEntityProfileIconDiv";
import {EntityId} from "../simulation/EntityData";
import {Simulation} from "../simulation/Simulation";
import {GameSimulationScene} from "../game/GameSimulationScene";
import {LongMessage} from "./GameSimulationComponent";


const ListItem = styled.div`
  display: flex;
  flex-direction: row;
  //align-items: center;
  border-radius: 8px;
  padding: 5px;
  padding-left: 10px;
`;

const ObservedCharacterButton = styled.div`
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-items: center;
  border-radius: 10px;
  padding: 2px;
  background-color: rgba(255, 255, 255, 0.0);

  &:hover {
    background-color: rgba(255, 255, 255, 0.75);
  }

  cursor: pointer;
`;

export function buildCharacterProfileIconButton(
  entityId: EntityId | null,
  simulation: Simulation,
  scene: GameSimulationScene,
  options?: {
    profileIconSize?: number,
    alpha?: number,
    onClick?: () => void,
    fallbackItemConfigKey?: string
  }
): ReactElement | null {
  options = options || {}

  const entity = entityId !== null ? scene.fogOfWarVisibleEntitiesById[entityId] : null

  const alpha = options.alpha || (entity ? 1 : 0.3)

  const iconLayersDiv = buildEntityProfileIconDiv(
    entityId,
    simulation,
    scene,
    {
      profileIconSize: options.profileIconSize,
      alpha: alpha,
      fallbackItemConfigKey: options.fallbackItemConfigKey
    }
  )

  if (iconLayersDiv != null) {
    const onClick = options.onClick || (() => {
      if (entity != null) {
        scene.dynamicState.selectEntity(entity.entityId)
        scene.centerCameraOnEntityId(entity.entityId)
      }
    })

    return <ObservedCharacterButton
      style={{
        height: (options.profileIconSize || 50) + 4
      }}
      onClick={(event) => {
        event.stopPropagation()
        onClick()
      }}
    >
      {iconLayersDiv}
    </ObservedCharacterButton>
  } else {
    return null
  }
}


export function ActivityStreamList(props: {
  activityStream: ActivityStreamEntry[]
  dynamicState: DynamicState
  setViewingLongMessage: (longMessage: LongMessage) => void
  perspectiveEntity: Entity | null
}): ReactElement | null {
  const activityStream = props.activityStream
  const dynamicState = props.dynamicState
  const simulation = dynamicState.simulation!

  let lastEntryRef: HTMLElement | null = null

  const phaserScene = props.dynamicState.scene

  const filteredActivityStream = activityStream.filter(it => {
    if (it.onlyShowForPerspectiveEntity) {
      return props.perspectiveEntity != null && props.perspectiveEntity.entityId === it.sourceEntityId
    } else if (props.perspectiveEntity == null || it.observedByEntityIds == null) {
      return true
    } else {
      return it.observedByEntityIds.includes(props.perspectiveEntity.entityId)
    }
  })

  const [wasScrolledToBottom, setWasScrolledToBottom] = useState(true)
  const [isScrolledFarFromBottom, setIsScrolledFarFromBottom] = useState(false)
  const [previousEntryCount, setPreviousEntryCount] = useState(filteredActivityStream.length)
  const [shouldShowNewMessagesBadge, setShouldShowNewMessagesBadge] = useState(false)

  if (phaserScene == null) {
    return null
  }

  function scrollToBottom() {
    setShouldShowNewMessagesBadge(false)
    lastEntryRef?.scrollIntoView({
      block: "nearest",
      inline: "nearest",
      behavior: "smooth"
    })
  }

  const content = filteredActivityStream.map((entry, activityStreamIndex) => {
    const longMessage = entry.longMessage
    const sourceLocation = entry.sourceLocation;

    const sourceProfileIconDiv = buildCharacterProfileIconButton(
      entry.sourceEntityId,
      simulation,
      phaserScene
    )

    const targetProfileIconDiv = buildCharacterProfileIconButton(
      entry.targetEntityId,
      simulation,
      phaserScene
    )

    return <ListItem
      key={"activity-stream-entry-" + activityStreamIndex}
      ref={activityStreamIndex === (filteredActivityStream.length - 1)
        ? (entry => {
          lastEntryRef = entry

          if (filteredActivityStream.length !== previousEntryCount) {
            if (wasScrolledToBottom) {
              scrollToBottom()
              setPreviousEntryCount(filteredActivityStream.length)
            } else {
              setShouldShowNewMessagesBadge(true)
            }
          }
        })
        : null}
      style={{
        display: "flex",
        flexDirection: "row",
        gap: 5,
        padding: 5,
        alignItems: "top",
        alignContent: "top",
        justifyContent: "top"
      }}
    >
      {sourceProfileIconDiv}

      {entry.sourceIconPath != null ? <img
        src={entry.sourceIconPath}
        alt={"Source icon"}
        style={{
          flexBasis: 40,
          height: 40
        }}
      /> : null}

      <div
        key={"main-column"}
        style={{
          display: "flex",
          flexDirection: "column",
          gap: 5,
          padding: 5,
          flexBasis: 0,
          flexGrow: 1
        }}
      >
        <Text><b>{entry.title}</b> <span style={{
          fontSize: 11,
          color: "rgba(0, 0, 0, 0.5)",
        }}>({formatSeconds(entry.time)})</span></Text>

        <div
          key={"main-row"}
          style={{
            display: "flex",
            flexDirection: "row",
            gap: 5
          }}
        >
          {longMessage != null ? <Button
            onClick={() => {
              props.setViewingLongMessage({
                title: entry.title,
                message: longMessage
              })
            }}
          >
            View
          </Button> : null}

          {entry.message != null ? <div
            style={{
              display: "flex",
              flexDirection: "column",
              gap: 5,
              backgroundColor: "white",
              borderRadius: 8,
              paddingTop: 5,
              paddingBottom: 5,
              paddingLeft: 10,
              paddingRight: 10
            }}
          >
            {entry.message.split("\n").map((line, index) => {
              return <Text key={"line-" + index}>{line}</Text>
            })}
          </div> : null}

          {entry.actionIconPath != null ? <img
            src={entry.actionIconPath}
            alt={"Action icon"}
            style={{
              flexBasis: 40,
              height: 40
            }}
          /> : null}

          {targetProfileIconDiv}

          {entry.targetIconPath != null ? <img
            src={entry.targetIconPath}
            alt={"Target icon"}
            style={{
              flexBasis: 40,
              height: 40
            }}
          /> : null}

          {entry.spawnedItems != null ? <div
            key={"spawn-items-list"}
            style={{
              display: "flex",
              flexDirection: "row",
              gap: 5
            }}
          >
            {entry.spawnedItems.map(spawnedItem => {
              return buildCharacterProfileIconButton(
                spawnedItem.entityId,
                simulation,
                phaserScene,
                {
                  profileIconSize: 25,
                  fallbackItemConfigKey: spawnedItem.itemConfigKey
                }
              )
            })}
          </div> : null}

          <div style={{
            flexGrow: 1.0
          }}/>

          {sourceLocation != null ?
            <ObservedCharacterButton
              onClick={(event) => {
                event.stopPropagation()
                props.dynamicState.scene?.centerCameraOnLocation(sourceLocation)
              }}
            >
              <IconMapPin color={"rgba(0, 0, 0, 0.25)"} size={20}/>
            </ObservedCharacterButton> : null}
        </div>

        {entry.observedByEntityIds != null || sourceLocation != null ? <div
          key={"observed-by-entities-list"}
          style={{
            display: "flex",
            flexDirection: "row",
            gap: 5,
            justifyContent: "right"
          }}
        >
          {(entry.observedByEntityIds || [])
            .filter(entityId => entityId !== entry.sourceEntityId && entityId !== props.perspectiveEntity?.entityId)
            .map(entityId => {
              return buildCharacterProfileIconButton(
                entityId,
                simulation,
                phaserScene,
                {
                  profileIconSize: 28
                }
              )
            })}
        </div> : null}


      </div>
    </ListItem>
  }).reverse()

  const scrollForNewMessagesButton = <div
    style={{
      position: "absolute",
      bottom: 10,
      left: 0,
      right: 0,
      display: "flex",
      flexDirection: "column",
      alignItems: "center"
    }}
  >
    <Button
      style={{}}
      color={"red"}
      variant={"filled"}
      rightIcon={
        <IconArrowDown size={20}/>
      }
      onClick={() => {
        if (lastEntryRef != null) {
          setShouldShowNewMessagesBadge(false)
          lastEntryRef.scrollIntoView({
            block: "nearest",
            inline: "nearest",
            behavior: "smooth"
          })
        }
      }}
    >
      New Activity
    </Button>
  </div>

  const scrollFromFarButton = <div
    key="close-button-container"
    style={{
      position: "absolute",
      bottom: 10,
      right: 30,
      display: "flex",
      flexDirection: "row",
      padding: 0,
      alignItems: "center",
      backgroundColor: "rgba(255, 255, 255, 0.75)",
      height: 40,
      borderRadius: 5,
      gap: 10
    }}
  >
    <ActionIcon size={40} variant={"subtle"} onClick={() => {
      scrollToBottom()
    }}>
      <IconArrowDown size={20}/>
    </ActionIcon>
  </div>

  return <React.Fragment>
    <div
      key={"activity-stream-scroll"}
      onScroll={event => {
        const scrollTop = event.currentTarget.scrollTop
        const newIsScrolledToBottom = scrollTop >= -5.0

        if (newIsScrolledToBottom) {
          setShouldShowNewMessagesBadge(false)
        }

        setWasScrolledToBottom(scrollTop >= -5.0)
        setIsScrolledFarFromBottom(scrollTop < -95.0)
      }}
      style={{
        flexGrow: 1.0,
        overflowY: "auto",
        display: "flex",
        flexDirection: "column-reverse"
      }}
    >
      {content}

    </div>

    <div
      key="invisble-relative-container"
      style={{
        position: "relative",
        backgroundColor: "red",
        width: "100%",
        height: 0
      }}
    >
      {(!wasScrolledToBottom && shouldShowNewMessagesBadge)
        ? scrollForNewMessagesButton
        : isScrolledFarFromBottom ? scrollFromFarButton
          : null}
    </div>
  </React.Fragment>
}