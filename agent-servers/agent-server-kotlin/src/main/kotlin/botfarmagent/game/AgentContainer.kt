package botfarmagent.game

import botfarmshared.game.apidata.AgentId
import botfarmshared.game.apidata.AgentSyncInput
import botfarmshared.game.apidata.AgentSyncOutput
import botfarmshared.engine.apidata.SimulationId
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.client.LoggingConfig
import com.aallam.openai.client.OpenAI
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.seconds

class AgentContainer {
   val openAI = OpenAI(
      token = System.getenv("BOTFARM_OPENAI_API_KEY"),
      timeout = Timeout(socket = 120.seconds),
      logging = LoggingConfig(
         logLevel = LogLevel.None
      )
   )

   private val threadPool = Executors.newCachedThreadPool()
   private val coroutineDispatcher: ExecutorCoroutineDispatcher = this.threadPool.asCoroutineDispatcher()

   private val agentRunnersByKey = mutableMapOf<String, AgentRunner>()

   fun addPendingInput(inputs: AgentSyncInput) {
      synchronized(this) {
         val agentId = inputs.selfInfo.agentId
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
            agentId = inputs.selfInfo.agentId,
            openAI = this.openAI,
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
