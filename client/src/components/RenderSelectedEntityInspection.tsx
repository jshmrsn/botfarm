import {Vector2Animation} from "../misc/Vector2Animation";
import {Text} from "@mantine/core";
import {RawEntityDataDebugComponent} from "./RenderInspectMode";
import {Entity} from "../simulation/Entity";
import {PositionComponentData} from "../common/PositionComponentData";
import {AgentComponentData} from "../game/agentComponentData";
import ReactMarkdown from "react-markdown";
import {getUnixTimeSeconds} from "../misc/utils";

export function renderSelectedEntityInspection(
  entity: Entity
) {
  const simulation = entity.simulation

  const positionComponent = entity.getComponentOrNull<PositionComponentData>("PositionComponentData")
  const agentComponent = entity.getComponentOrNull<AgentComponentData>("AgentComponentData")

  const simulationTime = simulation.getCurrentSimulationTime()

  let positionDiv: JSX.Element | null
  if (positionComponent != null) {
    const position = Vector2Animation.resolve(positionComponent.data.positionAnimation, simulationTime)
    positionDiv = <div>
      <Text>Location: x = {position.x.toFixed(0)}, y = {position.y.toFixed(0)}</Text>
    </div>
  } else {
    positionDiv = null
  }

  let agentDiv: JSX.Element | null
  if (agentComponent != null) {
    const agentComponentData = agentComponent.data
    agentDiv = <div>
      <Text>Core Personality: {agentComponentData.corePersonality}</Text>
      <Text>Input tokens: {agentComponentData.totalInputTokens}</Text>
      <Text>Output tokens: {agentComponentData.totalOutputTokens}</Text>
      <Text>Prompt count: {agentComponentData.totalPrompts}</Text>
      <Text>Agent requests: {agentComponentData.totalRemoteAgentRequests}</Text>
      <Text>Agent Type: {agentComponentData.agentType}</Text>
      <Text>Cost: ${agentComponentData.costDollars.toFixed(2)}</Text>
      <Text>Agent Status: <b>{agentComponentData.agentStatus}</b></Text>
      {agentComponentData.statusStartUnixTime != null ? <Text>Status Start Age: <b>{(getUnixTimeSeconds() - agentComponentData.statusStartUnixTime).toFixed(1)}</b></Text> : null}
      {agentComponentData.statusDuration != null ? <Text>Status Duration: <b>{agentComponentData.statusDuration.toFixed(1)}</b></Text> : null}
      <Text>Agent Integration Status: <b>{agentComponentData.agentIntegrationStatus}</b></Text>


      {agentComponentData.agentError != null ? <div
        style={{
          backgroundColor: "rgba(255, 0, 0, 0.2)",
          display: "flex",
          flexDirection: "row"
        }}
      >
        <Text><b>Agent Error:</b></Text>

        {agentComponentData.agentError.split("\n").map((line, index) => {
          return <Text key={"error-line-" + index}>{line}</Text>
        })}
      </div> : null}

      <Text><b>Debug Info:</b></Text>

      <ReactMarkdown>
        {agentComponentData.agentRemoteDebugInfo}
      </ReactMarkdown>


    </div>
  } else {
    agentDiv = null
  }

  return <div style={{
    width: "100%",
    // display: "flex",
    gap: 2,
    flexDirection: "column"
  }}>
    <Text><b>Entity</b> ({entity.entityId})</Text>

    <div style={{
      flexGrow: 1.0,
      width: "100%",
      display: "flex",
      gap: 2,
      flexDirection: "column",
      backgroundColor: "rgba(0, 0, 0, 0.1)",
      padding: 7,
      borderRadius: 5,
    }}>
      {positionDiv}
      {agentDiv}
      <RawEntityDataDebugComponent
        entity={entity}
      />
    </div>
  </div>
}