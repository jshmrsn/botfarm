import {ActivityStreamEntry} from "../game/ActivityStreamEntry";
import {ActionIcon, Button, Text} from "@mantine/core";
import React, {ReactElement, useState} from "react";
import {IconArrowDown} from "@tabler/icons-react";
import {DynamicState} from "./DynamicState";
import styled from "styled-components";
import {resolveEntityPositionForCurrentTime} from "../common/PositionComponentData";
import {formatSeconds} from "./ReplayControls";
import {Entity} from "../simulation/Entity";
import {ItemConfig} from "../game/ItemComponentData";
import {buildDivForProfileIconLayers} from "./BuildDivForProfileIconLayers";

interface Props {
  activityStream: ActivityStreamEntry[]
  dynamicState: DynamicState
  perspectiveEntity: Entity | null
}

const ListButton = styled.div`
  display: flex;
  flex-direction: row;
  gap: 10px;
  align-items: center;
  border-radius: 3px;
  padding: 5px;
  padding-left: 10px;

  &:hover {
    background-color: rgba(255, 255, 255, 0.75);
  }

  cursor: pointer;
`;


export function ActivityStreamList(props: Props): ReactElement | null {
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
    const sourceProfileIconDiv = buildDivForProfileIconLayers(
      entry.sourceEntityId,
      simulation,
      phaserScene
    )

    const targetProfileIconDiv = buildDivForProfileIconLayers(
      entry.targetEntityId,
      simulation,
      phaserScene
    )

    return <ListButton
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
      onClick={(event) => {
        event.stopPropagation()

        if (entry.sourceLocation != null) {
          props.dynamicState.scene?.centerCameraOnLocation(entry.sourceLocation)
        } else if (entry.sourceEntityId != null) {
          const sourceEntity = simulation.getEntityOrNull(entry.sourceEntityId)

          if (sourceEntity != null) {
            const sourceEntityPosition = resolveEntityPositionForCurrentTime(sourceEntity)
            props.dynamicState.scene?.centerCameraOnLocation(sourceEntityPosition)
          }
        }
      }}
      style={{
        display: "flex",
        flexDirection: "row",
        gap: 5,
        padding: 5
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
          {entry.longMessage != null ? <Button
            onClick={() => {
              alert(entry.longMessage)
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
        </div>

        {entry.spawnedItems != null ? <div
          key={"spawn-items-list"}
          style={{
            display: "flex",
            flexDirection: "row",
            gap: 5
          }}
        >
          {entry.spawnedItems.map(spawnedItem => {
            const itemConfig = simulation.getConfig<ItemConfig>(spawnedItem.itemConfigKey, "ItemConfig")

            return <img
              src={itemConfig.iconUrl}
              alt={"Spawned item icon"}
              style={{
                flexBasis: 25,
                height: 25
              }}
            />
          })}
        </div> : null}
      </div>
    </ListButton>
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
      backdropFilter: "blur(5px)",
      WebkitBackdropFilter: "blur(5px)",
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

  // joshr: I haven't figured out how to insert a container div that doesn't cause the parent to grow to fit
  // the scrolling content, so I'm using a fragment for now...
  return <React.Fragment>
  {/*  key="container2"*/}
  {/*  style={{*/}
  {/*    backgroundColor: "blue",*/}
  {/*    flexGrow: 1.0,*/}
  {/*    display: "flex",*/}
  {/*    height: "100%"*/}
  {/*  }}*/}
  {/*>*/}
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