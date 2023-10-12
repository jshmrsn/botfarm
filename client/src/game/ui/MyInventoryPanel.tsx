import {Text} from "@mantine/core";
import {IconGridDots} from "@tabler/icons-react";
import React, {ReactElement} from "react";
import {InventoryListComponent} from "./InventoryListComponent";
import {Entity} from "../../engine/simulation/Entity";
import {DynamicState} from "./DynamicState";
import {useWindowSize} from "@react-hook/window-size";
import {PanelCloseButton} from "./PanelCloseButton";

interface MyInventoryPanelProps {
  dynamicState: DynamicState
  userControlledEntity: Entity | null
  perspectiveEntity: Entity | null
  useMobileLayout: boolean
  close: () => void
}

export function MyInventoryPanel(props: MyInventoryPanelProps): ReactElement | null {
  const dynamicState = props.dynamicState
  const sideBarWidth = 250
  const simulation = props.dynamicState.simulation
  const [windowWidth, windowHeight] = useWindowSize()

  if (simulation == null) {
    return null
  }

  const perspectiveEntity = props.perspectiveEntity

  if (perspectiveEntity == null) {
    return null
  }


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
      height: props.useMobileLayout ? windowHeight * 0.25 : 0,
      flexGrow: 1,
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
      {<InventoryListComponent dynamicState={dynamicState}
                               perspectiveEntity={perspectiveEntity}
                               userControlledEntity={props.userControlledEntity}
                               viewOnly={perspectiveEntity !== props.userControlledEntity}/>}
    </div>
  </div>
}

