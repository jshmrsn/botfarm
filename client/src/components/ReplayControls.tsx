import {Simulation} from "../simulation/Simulation";
import {ReplayData} from "../simulation/EntityData";
import {ActionIcon, Slider, Text} from "@mantine/core";
import React, {ReactElement, useState} from "react";
import {IconPlayerPauseFilled, IconPlayerPlayFilled} from "@tabler/icons-react";

interface ReplayControlsProps {
  simulation: Simulation
  replayData: ReplayData
}

export function ReplayControls(props: ReplayControlsProps): ReactElement {
  const simulation = props.simulation
  const replayData = props.replayData

  const [forceUpdateCounter, setForceUpdateCounter] = useState(0)
  const [dynamicState, setDynamicState] = useState({
    waitingToChange: 0,
    pendingValue: 0.0
  })

  setTimeout(() => {
    setForceUpdateCounter(forceUpdateCounter + 1)
  }, 0)

  return <div
    key={"replay-controls-row"}
    style={{
      borderRadius: 6,
      paddingLeft: 4,
      paddingRight: 15,
      width: "100%",
      flexBasis: 48,
      display: "flex",
      flexDirection: "row",
      backgroundColor: "rgba(0, 0, 0, 0.4)",
      gap: 8,
      alignItems: "center"
    }}
  >

    <ActionIcon color={"white"} size={40} variant={"subtle"} onClick={() => {
      simulation.isReplayPaused = !simulation.isReplayPaused
    }}>
      {simulation.isReplayPaused
        ? <IconPlayerPlayFilled size={20}/>
        : <IconPlayerPauseFilled size={20}/>}
    </ActionIcon>
    <Text
      color={"white"}
      style={{}}
    >
      {formatSeconds(simulation.getCurrentSimulationTime())}
    </Text>

    <Slider
      min={0}
      max={replayData.replayGeneratedAtSimulationTime}
      label={(value) => formatSeconds(value)}
      value={dynamicState.waitingToChange === 0 ? simulation?.getCurrentSimulationTime() : dynamicState.pendingValue}
      onChange={(value) => {
        dynamicState.waitingToChange += 1

        setTimeout(() => {
          --dynamicState.waitingToChange
          dynamicState.pendingValue = value
          if (dynamicState.waitingToChange === 0) {
            simulation.seekReplayToSimulationTime(value)
          }
        }, 50)
      }}
      color="rgba(0, 255, 128, 1)"
      style={{
        flexGrow: 1.0
      }}
    />

    <Text
      color={"white"}
      style={{}}
    >
      {formatSeconds(props.replayData.replayGeneratedAtSimulationTime)}
    </Text>
  </div>
}

export function formatSeconds(seconds: number): string {
  let minutes = Math.floor(seconds / 60);
  let extraSeconds = seconds % 60;
  let minutesString = minutes < 10 ? "0" + minutes : minutes;
  let extraSecondsString = (extraSeconds < 10 ? "0" + extraSeconds.toFixed(0) : extraSeconds.toFixed(0))

  return `${minutesString}:${extraSecondsString}`
}