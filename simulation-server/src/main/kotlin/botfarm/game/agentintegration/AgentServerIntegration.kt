package botfarm.game.agentintegration

import botfarmshared.game.apidata.AgentSyncOutput
import botfarmshared.game.apidata.AgentSyncInput
import botfarmshared.game.apidata.AgentSyncRequest
import botfarmshared.game.apidata.AgentSyncResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


class AgentServerIntegration(
   val agentServerEndpoint: String,
   val buildMockAgent: ((context: MockAgentContext) -> MockAgent)? = null
) {
   private val mockAgentsByKey = mutableMapOf<String, MockAgent>()

   suspend fun sendSyncRequest(inputs: AgentSyncInput): List<AgentSyncOutput> {
      val request = AgentSyncRequest(
         input = inputs
      )

      val buildMockAgent = this.buildMockAgent
      if (buildMockAgent != null) {
         val agentKey = inputs.simulationId.value + ":" + inputs.agentId

         val mockAgent = this.mockAgentsByKey.getOrPut(agentKey) {
            buildMockAgent(
               MockAgentContext(
               agentId = inputs.agentId,
               simulationId = inputs.simulationId,
               agentType = inputs.agentType
            )
            )
         }

         return mockAgent.handleSyncRequest(request).outputs
      } else {
         val agentServerEndpoint = this.agentServerEndpoint

         val httpResponse = HttpClient {
            install(HttpTimeout) {
               requestTimeoutMillis = 10000
            }
            install(ContentNegotiation) {
               json(Json {
                  prettyPrint = true
                  isLenient = true
               })
            }
         }.use {
            it.post(agentServerEndpoint + "/api/sync") {
               contentType(ContentType.Application.Json)
               val bodyString = Json.encodeToString(request)
               setBody(bodyString)
            }
         }

         val response = try {
            httpResponse.body<AgentSyncResponse>()
         } catch (exception: Exception) {
            throw Exception("Exception getting parsed HTTP body:${httpResponse.bodyAsText()}\n", exception)
         }

         return response.outputs
      }
   }
}


