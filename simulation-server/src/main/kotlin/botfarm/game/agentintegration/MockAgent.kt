package botfarm.game.agentintegration

import botfarmshared.engine.apidata.SimulationId
import botfarmshared.game.apidata.*

class MockAgentContext(
   val agentId: AgentId,
   val agentType: String,
   val simulationId: SimulationId
)

class MockAgent(
   val handleSyncRequest: (AgentSyncRequest) -> AgentSyncResponse
)

fun scriptSequenceMockAgentBuilder(
   scripts: List<ScriptToRun>
): (context: MockAgentContext) -> MockAgent {
   return {
      var nextScriptIndex = 0

      MockAgent { agentSyncRequest ->
         val outputs = mutableListOf<AgentSyncOutput>()

         val nextScript = scripts.getOrNull(nextScriptIndex)
         val previousScript = scripts.getOrNull(nextScriptIndex - 1)

         if (nextScript != null) {
            if (nextScriptIndex == 0 ||
               (previousScript != null && agentSyncRequest.input.mostRecentCompletedScriptId == previousScript.scriptId)) {
               ++nextScriptIndex

               outputs.add(
                  AgentSyncOutput(
                     scriptToRun = nextScript
                  )
               )
            }
         }

         AgentSyncResponse(
            outputs = outputs
         )
      }
   }
}
