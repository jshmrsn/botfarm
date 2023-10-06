package botfarm.engine.ktorplugins

import botfarm.engine.simulation.*
import botfarmshared.engine.apidata.SimulationId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.http.content.staticFiles
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.resources.Resources
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.routing
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import java.io.File
import kotlin.concurrent.thread
import kotlin.io.path.absolutePathString
import io.ktor.server.routing.post as routingPost


fun Application.configureRouting(
   simulationContainer: SimulationContainer
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

      staticFiles("/replay-data", File(Simulation.replayDirectory.absolutePathString()))
      staticFiles("/", File("public"))

      routingPost("${apiPrefix}list-simulations") {
         val request = call.receive<ListSimulationsRequest>()

         val isAdmin = AdminRequest.shouldGiveRequestAdminCapabilities(request.adminRequest)

         val result = simulationContainer.listSimulations(
            userSecret = request.userSecret,
            isAdmin = isAdmin
         )

         call.respond(result)
      }

      routingPost("${apiPrefix}terminate-simulation") {
         val request = call.receive<TerminateSerializationRequest>()
         val isAdmin = AdminRequest.shouldGiveRequestAdminCapabilities(request.adminRequest)

         val simulation = simulationContainer.getSimulation(request.simulationId)

         if (simulation != null) {
            if (simulation.context.createdByUserSecret != request.userSecret) {
               if (!isAdmin) {
                  throw Exception("Only admin can terminate other user's simulations")
               }
            }
         }

         val result = simulationContainer.terminateSimulation(request.simulationId)

         call.respond(result)
      }

      routingPost("${apiPrefix}get-simulation-info") {
         val request = call.receive<GetSimulationInfoRequest>()

         val simulation = simulationContainer.getSimulation(request.simulationId)

         val simulationInfo = simulation?.buildInfo(checkBelongsToUserSecret = request.userSecret)

         call.respond(
            GetSimulationInfoResponse(
               simulationInfo = simulationInfo
            )
         )
      }

      routingPost("${apiPrefix}create-simulation") {
         val request = call.receive<CreateSimulationRequest>()
         val isAdmin = AdminRequest.shouldGiveRequestAdminCapabilities(request.adminRequest)

         val scenario = simulationContainer.scenarioRegistration.registeredScenarios.find {
            it.identifier == request.scenarioIdentifier &&
                    it.gameIdentifier == request.scenarioGameIdentifier
         }

         if (scenario == null) {
            throw Exception("Scenario not found for identifier: " + request.scenarioIdentifier)
         } else if (scenario.requiresAdmin && !isAdmin) {
            throw Exception("Scenario requires admin: " + request.scenarioIdentifier)
         } else {
            var createdSimulationVar: Simulation? = null
            var backgroundExceptionVar: Exception? = null

            thread {
               runBlocking {
                  val coroutineScope = this
                  try {
                     val createdSimulation = simulationContainer.createSimulation(
                        wasCreatedByAdmin = isAdmin,
                        createdByUserSecret = request.userSecret,
                        scenario = scenario,
                        shouldMinimizeSleep = false,
                        coroutineScope = coroutineScope
                     )

                     createdSimulationVar = createdSimulation

                     createdSimulation.startTickingInBackground()
                  } catch (exception: Exception) {
                     println("Exception in simulation background thread: ${exception.stackTraceToString()}")
                     backgroundExceptionVar = exception
                  }
               }
            }

            while (true) {
               val createdSimulation = createdSimulationVar
               val backgroundException = backgroundExceptionVar

               if (backgroundException != null) {
                  throw Exception("Exception in background while creating simulation", backgroundException)
               } else if (createdSimulation != null) {
                  val simulationInfo = createdSimulation.buildInfo(
                     checkBelongsToUserSecret = request.userSecret
                  )

                  call.respond(
                     CreateSimulationResponse(
                        simulationInfo = simulationInfo
                     )
                  )
                  break
               } else {
                  delay(50)
               }
            }
         }
      }
   }
}


@Serializable
class CreateSimulationResponse(
   val simulationInfo: SimulationInfo
)

@Serializable
class ListSimulationsRequest(
   val userSecret: UserSecret,
   val adminRequest: AdminRequest? = null
)

@Serializable
class TerminateSerializationRequest(
   val simulationId: SimulationId,
   val userSecret: UserSecret,
   val adminRequest: AdminRequest? = null
)

@Serializable
class CreateSimulationRequest(
   val scenarioIdentifier: String,
   val scenarioGameIdentifier: String,
   val userSecret: UserSecret,
   val adminRequest: AdminRequest? = null
)

@Serializable
class GetSimulationInfoRequest(
   val simulationId: SimulationId,
   val userSecret: UserSecret
)

@Serializable
class GetSimulationInfoResponse(
   val simulationInfo: SimulationInfo?
)