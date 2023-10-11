import {LongMessage} from "./GameSimulationComponent";
import React, {ReactElement} from "react";
import {PanelCloseButton} from "./PanelCloseButton";
import {ActionIcon, Text} from "@mantine/core";
import {IconCopy} from "@tabler/icons-react";

export function LongMessagePanel(
  props: {
    windowHeight: number
    windowWidth: number
    useMobileLayout: boolean
    viewingLongMessage: LongMessage,
    close: () => void
  }
): ReactElement | null {
  return <div
    style={{
      width: props.useMobileLayout ? "100%" : props.windowWidth - 800,
      backgroundColor: "rgba(255, 255, 255, 0.5)",
      backdropFilter: "blur(5px)",
      WebkitBackdropFilter: "blur(5px)",
      padding: 10,
      borderRadius: 10,
      right: 10,
      display: "flex",
      flexDirection: "column",
      height: 0,
      flexGrow: 1.0,
      flexBasis: 0,
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
      <ActionIcon
        onClick={() => {
          navigator.clipboard.writeText(props.viewingLongMessage.message)
        }}
      >
        <IconCopy size={15}/>
      </ActionIcon>
      <Text>{props.viewingLongMessage.title}</Text>
    </div>

    <div
      key={"scroll-content"}
      style={{
        flexGrow: 1.0,
        width: "100%",
        overflowY: "auto",
        display: "flex",
        flexDirection: "column",
        backgroundColor: "white",
        paddingLeft: 10,
        paddingRight: 10,
        paddingTop: 5,
        paddingBottom: 10,
        borderRadius: 3
      }}
    >
      <Text>
        <pre>{props.viewingLongMessage.message}</pre>
      </Text>
    </div>
  </div>
}