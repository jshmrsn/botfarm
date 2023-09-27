import {EntityComponentData} from "../simulation/EntityData";

export interface AgentComponentData extends EntityComponentData {
  corePersonality: string
  observationDistance: number

  wasRateLimited: Boolean,
  agentId: string
  agentStatus: string | null
  agentIntegrationStatus: string | null
  agentRemoteDebugInfo: string,
  agentError: string | null,
  totalInputTokens: number
  totalOutputTokens: number
  totalPrompts: number
  totalRemoteAgentRequests: number
  agentType: string
  costDollars: number
}