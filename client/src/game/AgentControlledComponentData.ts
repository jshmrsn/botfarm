import {EntityComponentData} from "../simulation/EntityData";
import {EntityComponentGetter} from "../simulation/EntityComponentGetter";

export interface AgentControlledComponentData extends EntityComponentData {
  corePersonality: string
  observationDistance: number

  agentId: string
  agentStatus: string | null
  lastAgentResponseUnixTime: number | null
  agentIntegrationStatus: string | null
  agentRemoteDebugInfo: string
  currentActionTimeline: string | null

  executingScript: string | null
  executingScriptId: string | null


  totalInputTokens: number
  totalOutputTokens: number
  totalPrompts: number
  totalRemoteAgentRequests: number
  agentType: string
  costDollars: number
}

export const AgentControlledComponent = new EntityComponentGetter<AgentControlledComponentData>("AgentControlledComponentData")