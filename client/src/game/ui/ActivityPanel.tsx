import {ActivityStreamComponentData} from "../simulation/activityStreamComponentData";
import {Text} from "@mantine/core";
import {IconMessages} from "@tabler/icons-react";
import {ActivityStreamList} from "./ActivityStreamList";
import React from "react";
import {Entity} from "../../engine/simulation/Entity";
import {DynamicState} from "./DynamicState";
import {PanelCloseButton} from "./PanelCloseButton";
import {LongMessage} from "./GameSimulationComponent";


export function ActivityPanel(
  props: {
    windowHeight: number
    windowWidth: number
    dynamicState: DynamicState
    useMobileLayout: boolean
    perspectiveEntity: Entity | null
    userControlledEntity: Entity | null
    setViewingLongMessage: (longMessage: LongMessage) => void
    close: () => void
  }
) {
  const windowWidth = props.windowWidth
  const dynamicState = props.dynamicState
  const sideBarWidth = props.useMobileLayout
    ? windowWidth - 20
    : Math.min(350, windowWidth * 0.4)
  //Math.max(400, Math.min(windowWidth * 0.3, 350))
  const simulation = props.dynamicState.simulation

  if (simulation == null) {
    return null
  }

  const activityStreamEntity = simulation.getEntity("activity-stream")
  const activityStreamComponent = activityStreamEntity.getComponent<ActivityStreamComponentData>("ActivityStreamComponentData")
  const activityStream = activityStreamComponent.data.activityStream

  return <div
    style={{
      width: props.useMobileLayout ? "100%" : sideBarWidth,
      backgroundColor: "rgba(255, 255, 255, 0.5)",
      backdropFilter: "blur(5px)",
      WebkitBackdropFilter: "blur(5px)",
      padding: 10,
      borderRadius: 10,
      right: 10,
      display: "flex",
      flexDirection: "column",
      height: props.useMobileLayout ? props.windowHeight * 0.4 : undefined,
      pointerEvents: "auto"
    }}
  >
    <PanelCloseButton close={props.close} size={30} right={-5} top={-5}/>

    <div
      key="header"
      style={{
        display: "flex",
        flexDirection: "row",
        alignItems: "center",
        gap: 5,
        marginBottom: 5
      }}
    >
      <IconMessages size={15}/>
      <Text>Activity</Text>
    </div>

    <ActivityStreamList
      activityStream={activityStream}
      dynamicState={dynamicState}
      setViewingLongMessage={props.setViewingLongMessage}
      perspectiveEntity={props.perspectiveEntity}
    />
  </div>
}