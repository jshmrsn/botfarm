package botfarm.simulationserver.game.ai

import botfarm.apidata.AgentStepInputs
import botfarm.apidata.AgentStepResult
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

@Serializable
class RemoteStepRequest(
   val inputs: AgentStepInputs
)

@Serializable
class RemoteStepResponse(
   val results: List<AgentStepResult>
)

class AgentServerIntegration {
   val agentServerEndpoint = System.getenv()["BOTFARM_AGENT_SERVER_ENDPOINT"] ?: "http://localhost:5002"

   suspend fun remoteStep(inputs: AgentStepInputs): List<AgentStepResult> {
      val request = RemoteStepRequest(
         inputs = inputs
      )

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
//         println("Sending post request for remoteStep: " + inputs.selfInfo.agentId)
         it.post(agentServerEndpoint + "/api/step") {
            contentType(ContentType.Application.Json)
            val bodyString = Json.encodeToString(request)
            setBody(bodyString)
         }
      }

//      println("httpResponse: $httpResponse")

      val response = try {
         httpResponse.body<RemoteStepResponse>()
      } catch (exception: Exception) {
         throw Exception("Exception getting parsed HTTP body:${httpResponse.bodyAsText()}\n", exception)
      }

      return response.results
   }
}


