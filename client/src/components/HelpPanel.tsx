import React, {useCallback, useEffect} from "react";
import {DynamicState} from "./SimulationComponent";
import {ActionIcon, Text} from "@mantine/core";
import {IconX} from "@tabler/icons-react";
import ReactMarkdown from "react-markdown";
import {attributionsMarkdown, howToPlayMarkdown} from "./HowToPlayMarkdown";

interface HelpPanelProps {
  windowHeight: number
  windowWidth: number
  dynamicState: DynamicState
  close: () => void
}


function useOnKeyDown(key: String, handleClose: () => void) {
  const handleEscKey = useCallback((event: any) => {
    if (event.key === key) {
      handleClose();
    }
  }, [handleClose]);

  useEffect(() => {
    document.addEventListener('keydown', handleEscKey, false);

    return () => {
      document.removeEventListener('keydown', handleEscKey, false);
    };
  }, [handleEscKey]);
}

export function HelpPanel(props: HelpPanelProps) {
  const simulation = props.dynamicState.simulation


  let phaserScene = props.dynamicState.phaserScene

  useOnKeyDown('Escape', props.close)
  useOnKeyDown('/', props.close)

  useEffect(() => {
    if (phaserScene != null &&
      phaserScene.input !== undefined &&
      phaserScene.input.keyboard != null) {
      phaserScene.input.keyboard.enabled = false
      phaserScene.input.keyboard.disableGlobalCapture()
    }

    return () => {
      if (phaserScene != null &&
        phaserScene.input !== undefined &&
        phaserScene.input.keyboard != null) {
        phaserScene.input.keyboard.enabled = true
        phaserScene.input.keyboard.enableGlobalCapture()
      }
    }
  }, []);


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
        backdropFilter: "blur(5px)",
        WebkitBackdropFilter: "blur(5px)",
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
      <div
        key="close-button-container"
        style={{
          position: "absolute",
          top: 10,
          right: 10,
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
          props.close()
        }}>
          <IconX size={20}/>
        </ActionIcon>
      </div>

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
          overflowY: "scroll"
        }}
      >
        <ReactMarkdown>
          {howToPlayMarkdown + "\n" + attributionsMarkdown}
        </ReactMarkdown>
      </div>
    </div>
  </div>
}