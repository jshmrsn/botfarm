import {ActivityStreamEntry} from "../game/ActivityStreamEntry";
import {ActionIcon, Button, Text} from "@mantine/core";
import React, {useState} from "react";
import {apiRequest} from "../api";
import {IconArrowDown, IconArrowLeft, IconTrashFilled} from "@tabler/icons-react";
import ReactMarkdown from "react-markdown";
import {EntityId} from "../simulation/EntityData";
import {CharacterBodySelections, CharacterComponent, CharacterComponentData} from "../game/CharacterComponentData";
import {Simulation} from "../simulation/Simulation";
import {SimulationScene} from "../game/SimulationScene";
import {DynamicState} from "./DynamicState";
import styled from "styled-components";
import {resolveEntityPositionForCurrentTime} from "../common/PositionComponentData";

interface Props {
  activityStream: ActivityStreamEntry[]
  dynamicState: DynamicState
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

function buildDivForProfileIconLayers(
  entityId: EntityId | null,
  simulation: Simulation,
  phaserScene: SimulationScene
): JSX.Element | null {
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


export function ActivityStreamList(props: Props): JSX.Element {
  const activityStream = props.activityStream
  const dynamicState = props.dynamicState
  const simulation = dynamicState.simulation!

  let lastEntryRef: HTMLElement | null = null

  const [wasScrolledToBottom, setWasScrolledToBottom] = useState(true)
  const [isScrolledFarFromBottom, setIsScrolledFarFromBottom] = useState(false)
  const [previousEntryCount, setPreviousEntryCount] = useState(activityStream.length)
  const [shouldShowNewMessagesBadge, setShouldShowNewMessagesBadge] = useState(false)

  function scrollToBottom() {
    setShouldShowNewMessagesBadge(false)
    lastEntryRef?.scrollIntoView({
      block: "nearest",
      inline: "nearest",
      behavior: "smooth"
    })
  }

  const content = activityStream.map((activityStreamEntry, activityStreamIndex) => {
    const phaserScene = props.dynamicState.phaserScene!

    const sourceProfileIconDiv = buildDivForProfileIconLayers(
      activityStreamEntry.sourceEntityId,
      simulation,
      phaserScene
    )

    const targetProfileIconDiv = buildDivForProfileIconLayers(
      activityStreamEntry.targetEntityId,
      simulation,
      phaserScene
    )

    return <ListButton
      key={"activity-stream-entry-" + activityStreamIndex}
      ref={activityStreamIndex === (activityStream.length - 1)
        ? (entry => {
          lastEntryRef = entry

          if (activityStream.length !== previousEntryCount) {
            if (wasScrolledToBottom) {
              scrollToBottom()
              setPreviousEntryCount(activityStream.length)
            } else {
              setShouldShowNewMessagesBadge(true)
            }
          }
        })
        : null}
      onClick={(event) => {
        event.stopPropagation()

        if (activityStreamEntry.sourceLocation != null) {
          props.dynamicState.phaserScene?.centerCameraOnLocation(activityStreamEntry.sourceLocation)
        } else if (activityStreamEntry.sourceEntityId != null) {
          const sourceEntity = simulation.getEntityOrNull(activityStreamEntry.sourceEntityId)

          if (sourceEntity != null) {
            const sourceEntityPosition = resolveEntityPositionForCurrentTime(sourceEntity)
            props.dynamicState.phaserScene?.centerCameraOnLocation(sourceEntityPosition)
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
      {activityStreamEntry.sourceIconPath != null ? <img
        src={activityStreamEntry.sourceIconPath}
        alt={"Source icon"}
        style={{
          flexBasis: 40,
          height: 40
        }}
      /> : null}
      <div
        style={{
          display: "flex",
          flexDirection: "column",
          gap: 5,
          padding: 5,
          flexBasis: 0,
          flexGrow: 1
        }}
      >
        <Text><b>{activityStreamEntry.title}</b></Text>
        {/*<Text><b>{activityStreamEntry.time.toFixed()}</b></Text>*/}

        <div
          style={{
            display: "flex",
            flexDirection: "row",
            gap: 5
          }}
        >
          {activityStreamEntry.message != null ? <div
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
            {activityStreamEntry.message.split("\n").map((line, index) => {
              return <Text key={"line-" + index}>{line}</Text>
            })}
          </div> : null}

          {activityStreamEntry.actionIconPath != null ? <img
            src={activityStreamEntry.actionIconPath}
            alt={"Action icon"}
            style={{
              flexBasis: 40,
              height: 40
            }}
          /> : null}
          {targetProfileIconDiv}
          {activityStreamEntry.targetIconPath != null ? <img
            src={activityStreamEntry.targetIconPath}
            alt={"Target icon"}
            style={{
              flexBasis: 40,
              height: 40
            }}
          /> : null}
        </div>
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