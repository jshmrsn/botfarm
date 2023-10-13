import {ActivityStreamEntry} from "../simulation/ActivityStreamEntry";
import {ActionIcon, Button, Text} from "@mantine/core";
import React, {ReactElement, useState} from "react";
import {IconArrowDown, IconMapPin} from "@tabler/icons-react";
import {DynamicState} from "./DynamicState";
import styled from "styled-components";
import {formatSeconds} from "./ReplayControls";
import {Entity} from "../../engine/simulation/Entity";
import {buildIconDiv} from "./BuildIconDiv";
import {EntityId} from "../../engine/simulation/EntityData";
import {Simulation} from "../../engine/simulation/Simulation";
import {GameSimulationScene} from "../scene/GameSimulationScene";
import {LongMessage} from "./GameSimulationComponent";
import {ItemConfig} from "../simulation/ItemComponentData";
import {ActionTypes, CharacterComponent} from "../simulation/CharacterComponentData";


const ListItem = styled.div`
  display: flex;
  flex-direction: row;
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

export function buildIconButton(
  entityId: EntityId | null,
  simulation: Simulation,
  scene: GameSimulationScene,
  options?: {
    profileIconSize?: number,
    alpha?: number,
    onClick?: () => void,
    fallbackItemConfigKey?: string | null,
    keySuffix?: string | null,
    expectedToNotExist?: boolean
  }
): ReactElement | null {
  options = options || {}

  const entity = entityId !== null ? simulation.getEntityOrNull(entityId) : null

  const visibleEntity = entityId !== null ? scene.fogOfWarVisibleEntitiesById[entityId] : null

  const alpha = options.alpha || ((entityId == null || options.expectedToNotExist || visibleEntity != null) ? 1 : 0.3)

  const iconLayersDiv = buildIconDiv(
    entityId,
    simulation,
    scene,
    {
      profileIconSize: options.profileIconSize,
      alpha: alpha,
      fallbackItemConfigKey: options.fallbackItemConfigKey,
      keySuffix: options.keySuffix
    }
  )

  if (iconLayersDiv != null) {
    if (visibleEntity != null && !options.expectedToNotExist) {
      const onClick = options.onClick || (() => {
        if (entity != null) {
          scene.dynamicState.selectEntity(entity.entityId)
          scene.centerCameraOnEntityId(entity.entityId)
        }
      })

      return <ObservedCharacterButton
        key={"profile-icon-button:" + (entityId ?? options.fallbackItemConfigKey)}
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
      return iconLayersDiv
    }
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

  const scene = props.dynamicState.scene

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

  if (scene == null) {
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

  function buildIconDiv(
    entityId: EntityId | null,
    options?: {
      profileIconSize?: number,
      alpha?: number,
      onClick?: () => void,
      fallbackItemConfigKey?: string | null,
      keySuffix?: string | null,
      expectedToNotExist?: boolean
    }
  ): ReactElement | null {
    return buildIconButton(
      entityId,
      simulation,
      scene!,
      options
    )
  }

  const content = filteredActivityStream.map((entry, activityStreamIndex) => {
    const longMessage = entry.longMessage // JSON.stringify(entry, null, 2)
    const sourceLocation = entry.sourceLocation;


    function resolveInfo(
      options: {
        entityId: EntityId | null,
        itemConfigKey: string | null,
        keySuffix?: string | null,
        expectedToNotExist?: boolean
      }
    ): {
      entity: Entity | null
      itemConfig: ItemConfig | null
      name: string | null
      nameOrYou: string | null
      nameOrLowercaseYou: string | null,
      iconDiv: ReactElement | null
      isEntityVisible: boolean
    } {
      const entity = options.entityId != null ? simulation.getEntityOrNull(options.entityId) : null
      const isEntityVisible = options.entityId != null ? scene?.fogOfWarVisibleEntitiesById[options.entityId] != null : true
      const itemConfig = options.itemConfigKey != null ? simulation.getConfig<ItemConfig>(options.itemConfigKey, "ItemConfig") : null
      const characterComponent = entity != null ? CharacterComponent.getOrNull(entity) : null

      const name = itemConfig != null
        ? itemConfig.name
        : characterComponent != null ?
          characterComponent.data.name
          : "?"

      const nameOrYou = options.entityId != null && options.entityId === dynamicState.perspectiveEntity?.entityId
        ? "You"
        : name

      const nameOrLowercaseYou = options.entityId != null && options.entityId === dynamicState.perspectiveEntity?.entityId
        ? "you"
        : name

      return {
        itemConfig: itemConfig,
        entity: entity,
        isEntityVisible: isEntityVisible,
        name: name,
        nameOrYou: nameOrYou,
        nameOrLowercaseYou: nameOrLowercaseYou,
        iconDiv: buildIconDiv(
          options.entityId,
          {
            keySuffix: options.keySuffix,
            fallbackItemConfigKey: options.itemConfigKey,
            expectedToNotExist: options.expectedToNotExist,
            profileIconSize: 45
          }
        )
      }
    }

    const actionType = entry.actionType

    const sourceInfo = resolveInfo({
      entityId: entry.sourceEntityId,
      itemConfigKey: entry.sourceItemConfigKey,
      keySuffix: "source"
    })

    const targetInfo = resolveInfo({
      entityId: entry.targetEntityId,
      itemConfigKey: entry.targetItemConfigKey,
      keySuffix: "target",
      expectedToNotExist: actionType === ActionTypes.PickUpItem || actionType === ActionTypes.UseToolToKillEntity
    })

    const resultInfo = resolveInfo({
      entityId: entry.resultEntityId,
      itemConfigKey: entry.resultItemConfigKey,
      keySuffix: "result"
    })

    const actionInfo = resolveInfo({
      entityId: null,
      itemConfigKey: entry.actionItemConfigKey,
      keySuffix: "action"
    })

    const spawnedItemInfos = entry.spawnedItems != null ? entry.spawnedItems.map((spawnedItem, spawnedItemIndex) => {
      return resolveInfo({
        entityId: spawnedItem.entityId,
        itemConfigKey: spawnedItem.itemConfigKey,
        keySuffix: "spawned-item:" + spawnedItemIndex
      })
    }) : []

    function buildMessageBubbleDiv(message: string | null): ReactElement | null {
      if (message == null) {
        return null
      }

      return <div
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
        {message.split("\n").map((line, index) => {
          return <Text key={"line-" + index}>{line}</Text>
        })}
      </div>
    }

    function buildTitleDiv(title: string | null): ReactElement | null {
      if (title == null) {
        return null
      }

      return <Text><b>{title}</b></Text>
    }

    let title = entry.title || ""

    const actionResultType = entry.actionResultType
    let actionMessage: string | null = null

    if (actionType != null) {
      let actionTitle;

      if (actionResultType !== "Success") {
        actionTitle = `${actionType} action failed ${actionResultType}`
      } else if (actionType === ActionTypes.UseToolToKillEntity) {
        actionTitle = `${sourceInfo.nameOrYou} harvested a ${targetInfo.name} using a ${actionInfo.name}`
      } else if (actionType === ActionTypes.UseToolToDamageEntity) {
        actionTitle = `${sourceInfo.nameOrYou} damage a ${targetInfo.name} using a ${actionInfo.name}`
      } else if (actionType === ActionTypes.PlaceGrowableInGrower) {
        actionTitle = `${sourceInfo.nameOrYou} planted ${actionInfo.name} in a ${targetInfo.name}`
      } else if (actionType === ActionTypes.DropItem) {
        actionTitle = `${sourceInfo.nameOrYou} dropped a ${targetInfo.name}`
      } else if (actionType === ActionTypes.PickUpItem) {
        actionTitle = `${sourceInfo.nameOrYou} picked up a ${targetInfo.name}`
      } else if (actionType === ActionTypes.UseEquippedTool) {
        actionTitle = `${sourceInfo.nameOrYou} used a ${targetInfo.name}`

        if (spawnedItemInfos.length > 0) {
          const spawnedItemInfo = spawnedItemInfos[0]
          actionTitle += ` to create a ${spawnedItemInfo.name}`
        }
      } else if (actionType === ActionTypes.EquipItem) {
        actionTitle = `${sourceInfo.nameOrYou} equipped a ${targetInfo.name}`
      } else if (actionType === ActionTypes.UnequipItem) {
        actionTitle = `${sourceInfo.nameOrYou} unequipped a ${targetInfo.name}`
      } else if (actionType === ActionTypes.Speak) {
        actionTitle = `${sourceInfo.nameOrYou} said...`
      } else if (actionType === ActionTypes.Thought) {
        actionTitle = `${sourceInfo.nameOrYou} thought...`
      } else if (actionType === ActionTypes.Craft) {
        actionTitle = `${sourceInfo.nameOrYou} crafted a ${targetInfo.name}`
      } else {
        actionTitle = `Unhandled action type: ${actionType}`
      }

      if (title !== "") {
        title += "\n" + actionTitle
      } else {
        title = actionTitle
      }
    }

    const timeDiv = <Text style={{
      fontSize: 11,
      color: "rgba(0, 0, 0, 0.5)",
    }}>{formatSeconds(entry.time)}</Text>

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
      <div
        key={"source-column"}
        style={{
          display: "flex",
          flexDirection: "column",
          gap: 5
        }}
      >
        {sourceInfo.iconDiv}

        {sourceLocation != null ?
          <ObservedCharacterButton
            onClick={(event) => {
              event.stopPropagation()
              props.dynamicState.scene?.centerCameraOnLocation(sourceLocation)
            }}
            style={{
              gap: 2
            }}
          >
            <IconMapPin color={"rgba(0, 0, 0, 0.25)"} size={17}/>

            {timeDiv}
          </ObservedCharacterButton> : timeDiv}
      </div>

      <div
        key={"main-column"}
        style={{
          display: "flex",
          flexDirection: "column",
          gap: 1,
          padding: 5,
          flexBasis: 0,
          flexGrow: 1
        }}
      >
        {buildTitleDiv(title)}

        <div
          key={"main-row"}
          style={{
            display: "flex",
            flexDirection: "row",
            gap: 5,
            alignItems: "center"
            //backgroundColor: "red"
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

          {buildMessageBubbleDiv(entry.message)}
          {buildMessageBubbleDiv(actionMessage)}

          {actionInfo.iconDiv}
          {targetInfo.iconDiv}
          {resultInfo.iconDiv}

          {entry.spawnedItems != null ? <div
            key={"spawn-items-list"}
            style={{
              display: "flex",
              flexDirection: "row",
              gap: 5
            }}
          >
            {entry.spawnedItems.map((spawnedItem, spawnedItemIndex) => {
              return buildIconDiv(
                spawnedItem.entityId,
                {
                  profileIconSize: 25,
                  fallbackItemConfigKey: spawnedItem.itemConfigKey,
                  keySuffix: "spawned-item:" + spawnedItemIndex
                }
              )
            })}
          </div> : null}

          <div style={{
            flexGrow: 1.0
          }}/>
        </div>

        {entry.observedByEntityIds != null || sourceLocation != null ? <div
          key={"observed-by-entities-list"}
          style={{
            display: "flex",
            flexDirection: "row",
            gap: 5,
            justifyContent: "right"
            //backgroundColor: "green"
          }}
        >
          {(entry.observedByEntityIds || [])
            .filter(entityId => entityId !== entry.sourceEntityId && entityId !== props.perspectiveEntity?.entityId)
            .map(entityId => {
              return buildIconDiv(
                entityId,
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
