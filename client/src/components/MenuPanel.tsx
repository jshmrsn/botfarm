import {useOnKeyDown} from "./useOnKeyDown";
import React, {useEffect} from "react";
import {ActionIcon, Button, Text} from "@mantine/core";
import {IconArrowDown, IconX} from "@tabler/icons-react";
import ReactMarkdown from "react-markdown";
import {attributionsMarkdown, howToPlayMarkdown} from "./HowToPlayMarkdown";
import {DynamicState} from "./DynamicState";
import {GetSimulationInfoResponse} from "./SimulationComponent";
import {useNavigate} from "react-router-dom";

interface MenuPanelProps {
  simulationId: string
  windowHeight: number
  windowWidth: number
  dynamicState: DynamicState
  getSimulationInfoResponse: GetSimulationInfoResponse | null
  loadReplayError: string | null
  wasDisconnected: boolean
  isViewingReplay: boolean
  viewReplay: () => void
  terminateSimulation: () => void
  exitSimulation: () => void
  close: () => void
}

export function MenuPanel(props: MenuPanelProps) {
  const panelWidth = Math.min(props.windowWidth - 20, 350)
  const panelHeight = Math.min(props.windowHeight - 30, 300)

  const simulationInfoResponse = props.getSimulationInfoResponse;
  const simulationInfo = simulationInfoResponse?.simulationInfo;

  const canClose = simulationInfoResponse != null &&
    ((simulationInfo != null && !simulationInfo.isTerminated) || props.isViewingReplay) &&
    !props.wasDisconnected

  useOnKeyDown('Escape', () => {
    if (canClose) {
      props.close()
    }
  })

  const content = <div
    key={"content"}
    style={{
      display: "flex",
      flexDirection: "column",
      flexGrow: 1,
      gap: 8,
      width: "100%"
    }}
  >
    {simulationInfo != null
      ? <div>
        <Text>Simulation ID: {props.simulationId}</Text>
      </div>
      : null}

    {simulationInfo != null
      ? <div>
        <Text>Scenario: {simulationInfo.scenarioInfo.name ?? simulationInfo.scenarioInfo.identifier}</Text>
      </div>
      : null}

    {(props.wasDisconnected && simulationInfo != null && !simulationInfo.isTerminated)
      ? <Text color={"red"}>Lost connection</Text>
      : null}

    {props.loadReplayError != null
      ? <Text color={"red"}>Failed to download replay</Text>
      : null}

    {simulationInfoResponse != null && simulationInfo == null
      ? <Text style={{
        textAlign: "center"
      }}>Simulation info not found, but a replay might still exist.</Text>
      : null}

    {simulationInfo != null &&
    simulationInfo.isTerminated
      ? <Text>This simulation has ended.</Text>
      : null}


    {props.isViewingReplay
      ? <Text>Viewing Replay</Text>
      : null}


    <div key={"spacer"} style={{flexGrow: 1.0}} />

    {simulationInfo != null &&
    !simulationInfo.isTerminated
      ?
      <Button
        color={"red"}
        variant={"filled"}
        onClick={() => {
          props.terminateSimulation()
        }}
      >
        Terminate Simulation
      </Button>
    : null}

    {(simulationInfo == null || simulationInfo.isTerminated) && !props.isViewingReplay
      ?
      <Button
        variant={"filled"}
        onClick={() => {
          props.viewReplay()
        }}
      >
        View Replay
      </Button>
      : null}

    <Button
      style={{}}
      // color={"red"}
      variant={"filled"}
      onClick={() => {
        props.exitSimulation()
      }}
    >
      Exit Simulation
    </Button>
  </div>

  return <div
    key={"menu-panel-background"}
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
      key={"menu-panel-background"}
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

      {canClose ? <div
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
      </div> : null}

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
        <Text size={20} weight={"bold"}>Simulation</Text>
      </div>

      {content}
    </div>
  </div>
}