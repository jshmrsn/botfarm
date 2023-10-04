package botfarmagent.game

import botfarmagent.game.ktorplugins.configureRouting
import botfarmagent.game.ktorplugins.configureSerialization
import botfarmshared.game.apidata.*
import com.aallam.openai.api.chat.ChatChoice
import com.aallam.openai.api.chat.ChatCompletion
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.core.FinishReason
import com.aallam.openai.api.core.Usage
import com.aallam.openai.api.model.ModelId
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

suspend fun ApplicationTestBuilder.sendSyncRequest(
   syncInput: AgentSyncInput
): AgentSyncResponse {
   val syncRequest = AgentSyncRequest(
      input = syncInput
   )

   val response = client.post("/api/sync") {
      contentType(ContentType.Application.Json)
      setBody(Json.encodeToString(syncRequest))
   }

   assertEquals(HttpStatusCode.OK, response.status)

   val responseText = response.bodyAsText()
   val syncResponse = Json.decodeFromString<AgentSyncResponse>(responseText)
   val pretty = Json {
      prettyPrint = true
   }

   println("syncResponse: " + pretty.encodeToString(syncResponse))

   return syncResponse
}


class AgentServerApplicationTest {
   @Test
   fun testRoot() = testApplication {
      val testCodeResponse = """
         I'm a language model and this is what I'd like to do!
         ```js
         speak("Hello from agent code!")  
         ```
         More text!
      """.trimIndent()

      val languageModelService = MockLanguageModelService(
         handleChatCompletionRequest = { request ->
            ChatCompletion(
               id = "foo",
               created = 1,
               model = ModelId("mock"),
               choices = listOf(
                  ChatChoice(
                     index = 0,
                     message = ChatMessage(
                        role = ChatRole.User,
                        content = testCodeResponse,
                        name = null,
                        functionCall = null
                     ),
                     finishReason = FinishReason.Stop
                  )
               ),
               usage = Usage(
                  promptTokens = 100,
                  completionTokens = 100,
                  totalTokens = 200
               )
            )
         }
      )

      val agentContainer = AgentContainer(
         languageModelService = languageModelService
      )

      application {
         configureSerialization()
         configureRouting(agentContainer)
      }

      client.get("/").apply {
         assertEquals(HttpStatusCode.OK, status)
         assertEquals("Botfarm Agent Server", bodyAsText())
      }

      var foundSpeakMessage = false
      var counter = 0

      while (!foundSpeakMessage) {
         val response = sendSyncRequest(buildSyncInputs())

         response.outputs.forEach { output ->
            output.actions?.forEach { action ->
               if (action.speak != null) {
                  if (action.speak == "Hello from agent code!") {
                     println("Found expected speech message")
                     foundSpeakMessage = true
                  } else {
                     throw Exception("Unexpected speak message: " + action.speak)
                  }
               }
            }
         }

         ++counter

         if (counter >= 15) {
            throw Exception("Agent test timed out")
         } else {
            delay(1000)
         }
      }
   }
}

