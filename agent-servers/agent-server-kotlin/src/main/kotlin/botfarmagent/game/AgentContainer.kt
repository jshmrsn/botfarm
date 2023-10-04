package botfarmagent.game

import botfarmshared.game.apidata.AgentId
import botfarmshared.game.apidata.AgentSyncInput
import botfarmshared.game.apidata.AgentSyncOutput
import botfarmshared.engine.apidata.SimulationId
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

class AgentContainer(
   val languageModelService: LanguageModelService
) {
   private val threadPool = Executors.newCachedThreadPool()
   private val coroutineDispatcher: ExecutorCoroutineDispatcher = this.threadPool.asCoroutineDispatcher()

   private val agentRunnersByKey = mutableMapOf<String, AgentRunner>()

   fun addPendingInput(inputs: AgentSyncInput) {
      synchronized(this) {
         val agentId = inputs.agentId
         val simulationId = inputs.simulationId
         val agentType = inputs.agentType

         val agentKey = this.buildAgentKey(
            simulationId = simulationId,
            agentId = agentId,
            agentType = agentType
         )

         val agentContext = AgentContext(
            agentType = agentType,
            agentContainer = this,
            agentId = inputs.agentId,
            languageModelService = this.languageModelService,
            initialSyncInput = inputs
         )

         val agentRunner = this.agentRunnersByKey.getOrPut(agentKey) {
            val agent = buildAgentForType(
               agentContext = agentContext
            )

            AgentRunner(
               agent = agent,
               agentContainer = this,
               coroutineDispatcher = this.coroutineDispatcher
            ).also {
               it.startRunningInBackground()
            }
         }

         agentRunner.addPendingInput(inputs)
      }
   }

   private fun buildAgentKey(
      simulationId: SimulationId,
      agentId: AgentId,
      agentType: String
   ) = "$simulationId:$agentId:$agentType"

   fun consumePendingOutputs(
      simulationId: SimulationId,
      agentId: AgentId,
      agentType: String
   ): List<AgentSyncOutput> {
      val agentKey = this.buildAgentKey(
         simulationId = simulationId,
         agentId = agentId,
         agentType = agentType
      )

      return synchronized(this) {
         val agentRunner = this.agentRunnersByKey[agentKey]
         agentRunner?.consumePendingResults() ?: listOf()
      }
   }
}
