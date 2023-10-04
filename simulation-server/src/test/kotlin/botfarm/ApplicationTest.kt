package botfarm

import botfarm.engine.ktorplugins.*
import botfarm.engine.simulation.SimulationContainer
import botfarm.engine.simulation.UserSecret
import botfarm.game.setup.registerGameScenarios
import botfarm.game.setup.registerGameSystems
import botfarmshared.engine.apidata.SimulationId
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

suspend fun ApplicationTestBuilder.sendCreateSimulationRequest(
   scenarioIdentifier: String
): CreateSimulationResponse {
   val request = CreateSimulationRequest(
      scenarioGameIdentifier = "game",
      userSecret = UserSecret("foo"),
      adminRequest = null,
      scenarioIdentifier = scenarioIdentifier
   )

   val response = client.post("/api/create-simulation") {
      contentType(ContentType.Application.Json)
      setBody(Json.encodeToString(request))
   }

   val responseText = response.bodyAsText()
   val createSimulationResponse = Json.decodeFromString<CreateSimulationResponse>(responseText)

   val pretty = Json {
      prettyPrint = true
   }

   println("createSimulationResponse: " + pretty.encodeToString(createSimulationResponse))

   return createSimulationResponse
}

suspend fun ApplicationTestBuilder.sendGetSimulationInfoRequest(
   simulationId: SimulationId
): GetSimulationInfoResponse {
   val request = GetSimulationInfoRequest(
      userSecret = UserSecret("foo"),
      simulationId = simulationId
   )

   val response = client.post("/api/get-simulation-info") {
      contentType(ContentType.Application.Json)
      setBody(Json.encodeToString(request))
   }

   val responseText = response.bodyAsText()
   val getSimulationInfoResponse = Json.decodeFromString<GetSimulationInfoResponse>(responseText)

   val pretty = Json {
      prettyPrint = true
   }

   println("getSimulationInfoResponse: " + pretty.encodeToString(getSimulationInfoResponse))

   return getSimulationInfoResponse
}


class ApplicationTest {
   @Test
   fun testRoot() = testApplication {
      val simulationContainer = SimulationContainer()

      application {
         registerGameSystems()
         registerGameScenarios()
         configureSerialization()
         configureRouting(simulationContainer)
      }

      val scenarioIdentifier = "default-no-agent"

      val createSimulationResponse = sendCreateSimulationRequest(
         scenarioIdentifier = scenarioIdentifier
      )

      val getSimulationInfoResponse = sendGetSimulationInfoRequest(
         simulationId = createSimulationResponse.simulationInfo.simulationId
      )

      val simulationInfo = getSimulationInfoResponse.simulationInfo

      assertNotNull(simulationInfo)
      assertEquals(simulationInfo.scenarioInfo.identifier, scenarioIdentifier)
   }
}
