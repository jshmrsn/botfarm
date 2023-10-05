import {EntityComponentData} from "../simulation/EntityData";
import {EntityComponentGetter} from "../simulation/EntityComponentGetter";

export interface AgentControlledComponentData extends EntityComponentData {
  corePersonality: string
  observationDistance: number

  wasRateLimited: Boolean,
  agentId: string
  agentStatus: string | null
  statusDuration: number | null
  statusStartUnixTime: number | null
  agentIntegrationStatus: string | null
  agentRemoteDebugInfo: string
  agentError: string | null
  currentActionTimeline: string | null

  executingScript: string | null
  executingScriptId: string | null
  scriptExecutionError: string | null


  totalInputTokens: number
  totalOutputTokens: number
  totalPrompts: number
  totalRemoteAgentRequests: number
  agentType: string
  costDollars: number
}

export const AgentControlledComponent = new EntityComponentGetter<AgentControlledComponentData>("AgentControlledComponentData")