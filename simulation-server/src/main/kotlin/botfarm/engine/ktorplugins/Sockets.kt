package botfarm.engine.ktorplugins

import botfarm.engine.simulation.ClientId
import botfarm.engine.simulation.SimulationContainer
import botfarm.engine.simulation.UserId
import botfarm.engine.simulation.UserSecret
import botfarmshared.engine.apidata.SimulationId
import botfarmshared.misc.jsonNullToNull
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.websocket.*
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.time.Duration

@Serializable
class ConnectionRequest(
   val simulationId: SimulationId,
   val clientId: ClientId,
   val userId: UserId,
   val userSecret: UserSecret
)

fun Application.configureSockets(simulationContainer: SimulationContainer) {
   install(WebSockets) {
      pingPeriod = Duration.ofSeconds(15)
      timeout = Duration.ofSeconds(15)
      maxFrameSize = Long.MAX_VALUE
      masking = false
   }
   routing {
      webSocket("/ws") {
         val session: DefaultWebSocketServerSession = this
         var requestedConnectionToSimulationIdVar: SimulationId? = null

         for (frame in session.incoming) {
            val requestedConnectionToSimulationId = requestedConnectionToSimulationIdVar

            if (frame is Frame.Close) {
               if (requestedConnectionToSimulationId != null) {
                  val simulation = synchronized(simulationContainer) {
                     simulationContainer.getSimulation(requestedConnectionToSimulationId)
                  }

                  if (simulation != null) {
                     simulation.handleWebSocketClose(session)
                  }
               }
            } else if (frame is Frame.Text) {
               try {
                  val text = frame.readText()

                  try {
                     val parsedJson = try {
                        Json.parseToJsonElement(text)
                     } catch (error: Exception) {
                        throw Exception("Failed to parse websocket JSON: $error")
                     }

                     val adminRequest = parsedJson.jsonObject.get("adminRequest")?.jsonNullToNull?.jsonObject?.let {
                        Json.decodeFromJsonElement<AdminRequest>(it)
                     }

                     val isAdminRequest = AdminRequest.shouldGiveRequestAdminCapabilities(adminRequest)

                     val messageType = parsedJson.jsonObject.get("type")?.jsonPrimitive?.contentOrNull
                        ?: throw Exception("Websocket json message has no type string")

                     val messageData = parsedJson.jsonObject.get("data")?.jsonObject
                        ?: throw Exception("Websocket json message has no data object")

                     if (messageType == "ConnectionRequest") {
                        val connectionRequest = Json.decodeFromJsonElement<ConnectionRequest>(messageData)

                        if (requestedConnectionToSimulationIdVar != null) {
                           throw Exception("Already requested connection")
                        }

                        val simulationId = connectionRequest.simulationId
                        requestedConnectionToSimulationIdVar = simulationId

                        val simulation = synchronized(simulationContainer) {
                           simulationContainer.getSimulation(simulationId)
                        }

                        if (simulation != null) {
                           simulation.runOnSimulationThreadAndWait(
                              debugInfo = "Handle ConnectionRequest"
                           ) {
                              simulation.handleNewWebSocketClient(
                                 clientId = connectionRequest.clientId,
                                 userId = connectionRequest.userId,
                                 userSecret = connectionRequest.userSecret,
                                 webSocketSession = session
                              )
                           }
                        } else {
                           throw Exception("Simulation not found")
                        }
                     } else if (requestedConnectionToSimulationId == null) {
                        throw Exception("Already requested connection")
                     } else {
                        val simulation = synchronized(simulationContainer) {
                           simulationContainer.getSimulation(requestedConnectionToSimulationId)
                        }

                        if (simulation != null) {
                           simulation.runOnSimulationThreadAndWait {
                              simulation.handleWebSocketMessage(
                                 session = session,
                                 messageType = messageType,
                                 messageData = messageData,
                                 isAdminRequest = isAdminRequest
                              )
                           }
                        } else {
                           throw Exception("Simulation not found")
                        }
                     }
                  } catch (exception: Exception) {
                     throw Exception("Exception while handling websocket frame text ($text)", exception)
                  }
               } catch (exception: Exception) {
                  println("Exception while handling websocket frame: $exception, ${exception.stackTraceToString()}")
                  session.close(
                     CloseReason(
                        CloseReason.Codes.INTERNAL_ERROR,
                        "Exception while handling websocket frame"
                     )
                  )
               }
            }
         }
      }
   }
}
