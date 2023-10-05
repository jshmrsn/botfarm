package botfarm.game.agentintegration

import botfarmshared.engine.apidata.SimulationId
import botfarmshared.game.apidata.AgentId
import botfarmshared.game.apidata.AgentSyncRequest
import botfarmshared.game.apidata.AgentSyncResponse

class MockAgentContext(
   val agentId: AgentId,
   val agentType: String,
   val simulationId: SimulationId
)

class MockAgent(
   val handleSyncRequest: (AgentSyncRequest) -> AgentSyncResponse
)