import React, {useEffect} from "react";
import {ActionIcon, Text} from "@mantine/core";
import {IconX} from "@tabler/icons-react";
import ReactMarkdown from "react-markdown";
import {attributionsMarkdown, howToPlayMarkdown} from "./HowToPlayMarkdown";
import {DynamicState} from "./DynamicState";
import {useOnKeyDown} from "./useOnKeyDown";
import {PanelCloseButton} from "./PanelCloseButton";

interface HelpPanelProps {
  windowHeight: number
  windowWidth: number
  dynamicState: DynamicState
  close: () => void
}

export function HelpPanel(props: HelpPanelProps) {
  const simulation = props.dynamicState.simulation

  useOnKeyDown('Escape', props.close)
  useOnKeyDown('/', props.close)

  if (simulation == null) {
    return null
  }

  const panelWidth = Math.min(props.windowWidth - 20, 900)
  const panelHeight = Math.min(props.windowHeight - 30, 500)

  return <div
    key={"help-panel-background"}
    style={{
      width: "100%",
      height: "100%",
      backgroundColor: "rgba(0, 0, 0, 0.5)",
      position: "absolute",
      backdropFilter: "blur(5px)",
      WebkitBackdropFilter: "blur(5px)"
    }}
  >
    <div
      key={"help-panel-background"}
      style={{
        width: panelWidth,
        backgroundColor: "rgba(255, 255, 255, 0.9)",
        padding: 10,
        borderRadius: 10,
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        height: panelHeight,
        marginTop: Math.max(10, (props.windowHeight - panelHeight) * 0.3),
        marginLeft: (props.windowWidth - panelWidth) / 2,
        position: "absolute"
      }}
    >
      <PanelCloseButton close={props.close} />

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
        {/*<IconQuestionMark size={20}/>*/}
        <Text size={20} weight={"bold"}>Help</Text>
      </div>

      <div
        key={"content"}
        style={{
          display: "flex",
          flexDirection: "column",
          flexGrow: 1,
          overflowY: "auto"
        }}
      >
        <ReactMarkdown>
          {howToPlayMarkdown + "\n" + attributionsMarkdown}
        </ReactMarkdown>
      </div>
    </div>
  </div>
}



