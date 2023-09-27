package botfarm.simulationserver.plugins

import botfarm.simulationserver.game.ai.AgentServerIntegration
import botfarm.simulationserver.game.createGameSimulation
import botfarm.simulationserver.simulation.ClientSimulationData
import botfarm.simulationserver.simulation.SimulationContainer
import botfarm.apidata.SimulationId
import io.ktor.http.HttpStatusCode
import io.ktor.resources.Resource
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.http.content.staticFiles
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.resources.Resources
import io.ktor.server.resources.get
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.routing
import io.ktor.util.pipeline.PipelineContext
import kotlinx.serialization.Serializable
import java.io.File
import io.ktor.server.resources.post as resourcePost
import io.ktor.server.routing.post as routingPost

fun Application.configureRouting(
   simulationContainer: SimulationContainer,
   agentServerIntegration: AgentServerIntegration
) {
   install(StatusPages) {
      exception<Throwable> { call, cause ->
         call.respondText(text = "500", status = HttpStatusCode.InternalServerError)
      }
   }

   install(Resources)

   val apiPrefix = "/api/"

   routing {
      println("Files: " + File("files").absolutePath)

      staticFiles("/", File("public"))

      val listSimulationsBody: suspend PipelineContext<Unit, ApplicationCall>.(ListSimulationsRequest) -> Unit =
         { article ->
            val result = synchronized(simulationContainer) {
               simulationContainer.listSimulations()
            }

            call.respond(result)
         }

      get<ListSimulationsRequest>(listSimulationsBody)
      resourcePost<ListSimulationsRequest>(listSimulationsBody)

      routingPost("${apiPrefix}terminate-simulation") {
         val request = call.receive<TerminateSerializationRequestData>()

         val result = synchronized(simulationContainer) {
            simulationContainer.terminateSimulation(request.simulationId)
         }

         call.respond(result)
      }

      routingPost("${apiPrefix}create-simulation") {
         val result = synchronized(simulationContainer) {
            try {
               createGameSimulation(
                  simulationContainer = simulationContainer,
                  agentServerIntegration = agentServerIntegration
               )
            } catch (exception: Exception) {
               println("Exception calling createDemoSimulation:  ${exception.stackTraceToString()}")
               throw exception
            }
         }

         call.respond(result)
      }
   }
}


class GetSimulationDataResponse(
   val simulationData: ClientSimulationData?
)


@Serializable
@Resource("/api/list-simulations")
class ListSimulationsRequest()

@Serializable
class TerminateSerializationRequestData(
   val simulationId: SimulationId
)

