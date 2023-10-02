package botfarmagent.game.ktorplugins

import botfarmagent.game.AgentContainer
import botfarmshared.game.apidata.AgentSyncRequest
import botfarmshared.game.apidata.AgentSyncResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receiveText
import io.ktor.server.resources.Resources
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import java.io.File
import io.ktor.server.routing.post as routingPost


fun Application.configureRouting(remoteAgentContainer: AgentContainer) {
//   install(StatusPages) {
//      exception<Throwable> { call, cause ->
//         call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
//      }
//   }

   install(Resources)
   val apiPrefix = "/api/"

   routing {
      println("Files: " + File("files").absolutePath)

      get {
         call.respondText("Botfarm Agent Server")
      }

      routingPost("${apiPrefix}sync") {
         val requestJsonString = call.receiveText()

         val request = Json.decodeFromString<AgentSyncRequest>(requestJsonString)

         val results = synchronized(remoteAgentContainer) {
            remoteAgentContainer.addPendingInputs(request.inputs)
            remoteAgentContainer.consumePendingResults(
               agentType = request.inputs.agentType,
               simulationId = request.inputs.simulationId,
               agentId = request.inputs.selfInfo.agentId
            )
         }

         val response = AgentSyncResponse(
            stepResults = results
         )

         call.respond(response)
      }
   }
}


