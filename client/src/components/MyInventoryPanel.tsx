import {Text} from "@mantine/core";
import {IconGridDots} from "@tabler/icons-react";
import React from "react";
import {InventoryComponent} from "./InventoryComponent";
import {Entity} from "../simulation/Entity";
import {ActivityStreamComponentData} from "../game/activityStreamComponentData";
import {DynamicState} from "./DynamicState";

interface MyInventoryPanelProps {
  windowHeight: number
  windowWidth: number
  dynamicState: DynamicState
  userControlledEntity: Entity | null
  useMobileLayout: boolean
}

export function MyInventoryPanel(props: MyInventoryPanelProps): JSX.Element | null {
  const windowHeight = props.windowHeight
  const windowWidth = props.windowWidth
  const dynamicState = props.dynamicState
  const sideBarWidth = 250
  const simulation = props.dynamicState.simulation

  if (simulation == null) {
    return null
  }

  const userControlledEntity = props.userControlledEntity

  if (userControlledEntity == null) {
    return null
  }

  const simulationId = simulation.simulationId

  const activityStreamEntity = simulation.getEntity("activity-stream")
  const activityStreamComponent = activityStreamEntity.getComponent<ActivityStreamComponentData>("ActivityStreamComponentData")
  const activityStream = activityStreamComponent.data.activityStream

  const scrollAreaHeight = windowHeight - 100 - 60 - 20 - 40;

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
      height: props.useMobileLayout ? props.windowHeight * 0.25 : 0,
      flexGrow: 1
    }}
  >
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
      <IconGridDots size={15} />
      <Text>Inventory</Text>
    </div>

    <div
      key={"scroll-content"}
      style={{
        flexGrow: 1.0,
        overflowY: "auto"
      }}
    >
      {<InventoryComponent dynamicState={dynamicState} entity={userControlledEntity} viewOnly={false}/>}
    </div>
  </div>
}