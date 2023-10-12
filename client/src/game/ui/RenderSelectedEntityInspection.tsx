import {Vector2Animation} from "../../misc/Vector2Animation";
import {Text} from "@mantine/core";
import {RawEntityDataDebugComponent} from "./RenderInspectMode";
import {Entity} from "../../engine/simulation/Entity";
import {PositionComponent} from "../../common/PositionComponentData";
import {AgentControlledComponent} from "../simulation/AgentControlledComponentData";
import ReactMarkdown from "react-markdown";
import {getUnixTimeSeconds} from "../../misc/utils";
import {Fragment, ReactElement} from "react";

export function renderSelectedEntityInspection(
  entity: Entity,
  windowHeight: number
) {
  const simulation = entity.simulation

  const positionComponent = PositionComponent.getOrNull(entity)
  const agentControlledComponent = AgentControlledComponent.getOrNull(entity)

  const simulationTime = simulation.getCurrentSimulationTime()

  let positionDiv: ReactElement | null
  if (positionComponent != null) {
    const position = Vector2Animation.resolve(positionComponent.data.positionAnimation, simulationTime)
    positionDiv = <div>
      <Text>Location: x = {position.x.toFixed(0)}, y = {position.y.toFixed(0)}</Text>
    </div>
  } else {
    positionDiv = null
  }

  let agentDiv: ReactElement | null
  if (agentControlledComponent != null) {
    const agentControlledComponentData = agentControlledComponent.data
    agentDiv = <div>
      <Text>Core Personality: {agentControlledComponentData.corePersonality}</Text>
      <Text>Input tokens: {agentControlledComponentData.totalInputTokens}</Text>
      <Text>Output tokens: {agentControlledComponentData.totalOutputTokens}</Text>
      <Text>Prompt count: {agentControlledComponentData.totalPrompts}</Text>
      <Text>Agent requests: {agentControlledComponentData.totalRemoteAgentRequests}</Text>
      <Text>Agent Type: {agentControlledComponentData.agentType}</Text>
      <Text>Cost: ${agentControlledComponentData.costDollars.toFixed(2)}</Text>
      <Text>Agent Status: <b>{agentControlledComponentData.agentStatus}</b></Text>
      {agentControlledComponentData.lastAgentResponseUnixTime != null ? <Text>Status Start
        Age: <b>{(getUnixTimeSeconds() - agentControlledComponentData.lastAgentResponseUnixTime).toFixed(1)}</b></Text> : null}

      <Text>Agent Integration Status: <b>{agentControlledComponentData.agentIntegrationStatus}</b></Text>

      {agentControlledComponentData.executingScriptId != null ? <Fragment>
        <Text><b>Agent Script: ({agentControlledComponentData.executingScriptId})</b></Text>
        <Text>
          <pre>{agentControlledComponentData.executingScript ?? ""}</pre>
        </Text>
      </Fragment> : null}

      {agentControlledComponentData.currentActionTimeline != null ? <Fragment>
        <Text><b>Running Action:</b></Text>
        <Text>
          <pre>{agentControlledComponentData.currentActionTimeline}</pre>
        </Text>
      </Fragment> : null}

      <Text><b>Debug Info:</b></Text>

      <ReactMarkdown>
        {agentControlledComponentData.agentRemoteDebugInfo}
      </ReactMarkdown>


    </div>
  } else {
    agentDiv = null
  }

  return <div style={{
    width: "100%",
    // display: "flex",
    gap: 2,
    flexDirection: "column",
    height: windowHeight * 0.4
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