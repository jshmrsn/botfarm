import {useOnKeyDown} from "./useOnKeyDown";
import React, {useEffect} from "react";
import {ActionIcon, Button, Switch, Text} from "@mantine/core";
import {IconArrowDown, IconX} from "@tabler/icons-react";
import ReactMarkdown from "react-markdown";
import {attributionsMarkdown, howToPlayMarkdown} from "./HowToPlayMarkdown";
import {DynamicState} from "./DynamicState";
import {GetSimulationInfoResponse} from "./SimulationComponent";
import {useNavigate} from "react-router-dom";
import {Entity} from "../simulation/Entity";
import {DebugInfoComponentData} from "../game/DebugInfoComponentData";

interface MenuPanelProps {
  simulationId: string
  windowHeight: number
  windowWidth: number
  dynamicState: DynamicState
  getSimulationInfoResponse: GetSimulationInfoResponse | null
  loadReplayError: string | null
  rawUserControlledEntity: Entity | null
  wasDisconnected: boolean
  isViewingReplay: boolean
  viewReplay: () => void
  terminateSimulation: () => void
  exitSimulation: () => void
  close: () => void
  isInForceSpectateMode: boolean
  setIsInForceSpectateMode: (value: boolean) => void
}

export function MenuPanel(props: MenuPanelProps) {
  const panelWidth = Math.min(props.windowWidth - 20, 350)
  const panelHeight = Math.min(props.windowHeight - 30, 400)

  const simulationInfoResponse = props.getSimulationInfoResponse;
  const simulationInfo = simulationInfoResponse?.simulationInfo;


  const userControlledEntity = props.dynamicState.userControlledEntity

  const debugInfoEntity = props.dynamicState.simulation?.getEntityOrNull("debug-info")
  const debugInfo = debugInfoEntity?.getComponentData<DebugInfoComponentData>("DebugInfoComponentData")

  const canClose = simulationInfoResponse != null &&
    ((simulationInfo != null && !simulationInfo.isTerminated) || props.isViewingReplay) &&
    !props.wasDisconnected

  useOnKeyDown('Escape', () => {
    if (canClose) {
      props.close()
    }
  })

  const canSendMessages = !props.isViewingReplay &&
    simulationInfoResponse != null &&
    simulationInfo != null &&
    !simulationInfo.isTerminated &&
    !props.wasDisconnected

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
      ? <div
        style={{
          display: "flex",
          flexDirection: "column",
          gap: 3,
          width: "100%"
        }}
      >
        <Text color={"red"}>Lost connection</Text>
        <Button
          style={{}}
          variant={"filled"}
          onClick={() => {
            window.location.reload()
          }}
        >
          Reconnect
        </Button>
      </div>
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

    {canSendMessages &&
    props.rawUserControlledEntity != null
      ? <Switch checked={props.isInForceSpectateMode}
                onChange={(event) => props.setIsInForceSpectateMode(event.currentTarget.checked)}
                label={"Force Spectate"}
      />
      : null}

    {canSendMessages &&
    debugInfo != null
      ? debugInfo.aiPaused
        ? <Button
          variant={"filled"}
          onClick={() => {
            props.dynamicState.simulation?.sendMessage("ResumeAiRequest", {})
          }}
        >
          Resume AI
        </Button>
        : <Button
          variant={"filled"}
          onClick={() => {
            props.dynamicState.simulation?.sendMessage("PauseAiRequest", {})
          }}
        >
          Pause AI
        </Button>
      : null}

    {canSendMessages
      ? props.rawUserControlledEntity == null
        ? <Button
          variant={"filled"}
          onClick={() => {
            props.dynamicState.simulation?.sendSpawnRequest()
          }}
        >
          Spawn
        </Button>
        : <Button
          variant={"filled"}
          onClick={() => {
            props.dynamicState.simulation?.sendDespawnRequest()
          }}
        >
          De-spawn
        </Button>
      : null}

    <div key={"spacer"} style={{flexGrow: 1.0}}/>

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
      WebkitBackdropFilter: "blur(5px)",
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
      flexDirection: "column",
      paddingBottom: 100
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
        alignItems: "center"
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