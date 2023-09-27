package botfarm.agentserver

import botfarm.agentserver.utils.extractJsonFromPromptResponse
import botfarm.apidata.PromptUsage
import botfarm.misc.JsonSchema
import botfarm.misc.buildShortRandomString
import com.aallam.openai.api.LegacyOpenAI
import com.aallam.openai.api.chat.*
import com.aallam.openai.api.completion.CompletionRequest
import com.aallam.openai.api.core.FinishReason
import com.aallam.openai.api.core.Usage
import com.aallam.openai.api.exception.RateLimitException
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement


sealed class RunPromptResult {
   class Success(
      val responseText: String,
      val usage: PromptUsage
   ) : RunPromptResult()

   class LengthLimit(val errorId: String, val usage: PromptUsage) : RunPromptResult()
   class RateLimitError(val errorId: String) : RunPromptResult()
   class UnknownApiError(val errorId: String, val exception: Exception) : RunPromptResult()
}

@OptIn(LegacyOpenAI::class)
suspend fun runPrompt(
   debugInfo: String,
   openAI: OpenAI,
   modelInfo: ModelInfo,
   promptBuilder: PromptBuilder,
   useFunctionCalling: Boolean = false,
   functionName: String? = null,
   functionSchema: JsonSchema? = null,
   functionDescription: String? = null,
   completionPrefix: String? = null,
   completionMaxTokens: Int? = null,
   chatMaxTokens: Int? = null,
   temperature: Double = 0.0,
   shouldPrintResponse: Boolean = true
): RunPromptResult {
   val prompt = promptBuilder.buildText()
   println("Running prompt using model ${modelInfo.modelId}, ${debugInfo}:\n$prompt")
   println("Prompt token usage summary ${modelInfo.modelId}, ${debugInfo}:\n${promptBuilder.buildTokenUsageSummary()}")

   val usage: Usage?
   val promptUsage: PromptUsage
   val responseText: String

   if (modelInfo.isCompletionModel) {
      val maxTokens = completionMaxTokens ?: (modelInfo.maxTokenCount - promptBuilder.approximateTotalTokens)

      val completionRequest = CompletionRequest(
         model = ModelId(modelInfo.modelId),
         temperature = temperature,
         prompt = prompt,
         maxTokens = maxTokens
      )

      val completion = try {
         openAI.completion(completionRequest)
      } catch (exception: RateLimitException) {
         val errorId = buildShortRandomString()
         println("Got rate limit exception running prompt (errorId = $errorId, $debugInfo):\n" + exception.stackTraceToString())
         return RunPromptResult.RateLimitError(errorId = errorId)
      } catch (exception: Exception) {
         val errorId = buildShortRandomString()
         println("Got unknown error running prompt (errorId = $errorId, $debugInfo):\n" + exception.stackTraceToString())
         return RunPromptResult.UnknownApiError(errorId = errorId, exception = exception)
      }

      usage = completion.usage
      promptUsage = PromptUsage(
         totalTokens = usage?.totalTokens ?: 0,
         promptTokens = usage?.promptTokens ?: 0,
         completionTokens = usage?.completionTokens ?: 0
      )

      if (completion.choices.first().finishReason == FinishReason.Length) {
         val errorId = buildShortRandomString()

         println("Completion ended from max length (errorId = $errorId, $debugInfo, approximate input tokens = ${promptBuilder.approximateTotalTokens}, modelInfo.maxTokenCount = ${modelInfo.maxTokenCount}, promptTokens = ${promptUsage.promptTokens}, completionTokens = ${promptUsage.completionTokens}):\n" + completion.choices.first().text)

         return RunPromptResult.LengthLimit(errorId = errorId, usage = promptUsage)
      }

      responseText = (completionPrefix ?: "") + completion.choices.first().text
   } else {
      val chatCompletionRequest = ChatCompletionRequest(
         model = ModelId(modelInfo.modelId),
         temperature = temperature,
         maxTokens = chatMaxTokens,
         messages = listOf(
            ChatMessage(
               role = ChatRole.System,
               content = prompt
            )
         ),
         functionCall = if (useFunctionCalling) {
            FunctionMode.Named(
               functionName ?: throw Exception("functionName not provided when useFunctionCalling is true")
            )
         } else {
            null
         },
         functions = if (useFunctionCalling) {
            listOf(
               ChatCompletionFunction(
                  name = functionName ?: throw Exception("functionName not provided when useFunctionCalling is true"),
                  description = functionDescription,
                  parameters = Parameters(Json.encodeToJsonElement(functionSchema))
               )
            )
         } else {
            null
         }
      )

      val completion = try {
         openAI.chatCompletion(chatCompletionRequest)
      } catch (exception: RateLimitException) {
         val errorId = buildShortRandomString()
         println("Got rate limit exception running prompt (errorId = $errorId, $debugInfo, ${modelInfo.modelId}):\n" + exception.stackTraceToString())
         return RunPromptResult.RateLimitError(errorId = errorId)
      } catch (exception: Exception) {
         val errorId = buildShortRandomString()
         println("Got unknown error running prompt (errorId = $errorId, $debugInfo, ${modelInfo.modelId}):\n" + exception.stackTraceToString())
         return RunPromptResult.UnknownApiError(errorId = errorId, exception = exception)
      }

      usage = completion.usage
      promptUsage = PromptUsage(
         totalTokens = usage?.totalTokens ?: 0,
         promptTokens = usage?.promptTokens ?: 0,
         completionTokens = usage?.completionTokens ?: 0
      )

      if (completion.choices.first().finishReason == FinishReason.Length) {
         val errorId = buildShortRandomString()

         println("Chat completion ended from max length (errorId = $errorId, $debugInfo, approximate input tokens = ${promptBuilder.approximateTotalTokens}, modelInfo.maxTokenCount = ${modelInfo.maxTokenCount}, promptTokens = ${promptUsage.promptTokens}, completionTokens = ${promptUsage.completionTokens}):\n${completion.choices.first().message}")
         return RunPromptResult.LengthLimit(errorId = errorId, usage = promptUsage)
      }

      val message = completion.choices.first().message

      responseText = if (useFunctionCalling) {
         message.functionCall?.arguments ?: throw Exception("functionCall is null")
      } else {
         message.content ?: ""
      }
   }

   if (shouldPrintResponse) {
      println("Prompt response ($debugInfo, promptTokens = ${usage?.promptTokens}, completionTokens = ${usage?.completionTokens}, input chars = ${prompt.length}, ${modelInfo.modelId}):\n${responseText}")
   }

   return RunPromptResult.Success(
      usage = promptUsage,
      responseText = responseText
   )
}


sealed class RunJsonPromptResult {
   class Success(
      val responseData: JsonObject,
      val textBeforeJson: String,
      val textAfterJson: String,
      val usage: PromptUsage
   ) : RunJsonPromptResult()

   class JsonParseFailed(
      val errorId: String,
      val usage: PromptUsage
   ) : RunJsonPromptResult()

   class LengthLimit(val errorId: String) : RunJsonPromptResult()
   class RateLimitError(val errorId: String) : RunJsonPromptResult()
   class UnknownApiError(val errorId: String, val exception: Exception) : RunJsonPromptResult()
}

suspend fun runPromptWithJsonOutput(
   debugInfo: String,
   openAI: OpenAI,
   modelInfo: ModelInfo,
   promptBuilder: PromptBuilder,
   useFunctionCalling: Boolean = false,
   functionName: String? = null,
   functionSchema: JsonSchema? = null,
   functionDescription: String? = null,
   completionPrefix: String? = null,
   completionMaxTokens: Int,
   chatMaxTokens: Int? = null,
   temperature: Double = 0.0
): RunJsonPromptResult {
   val result = runPrompt(
      debugInfo = debugInfo,
      openAI = openAI,
      modelInfo = modelInfo,
      promptBuilder = promptBuilder,
      useFunctionCalling = useFunctionCalling,
      functionName = functionName,
      functionSchema = functionSchema,
      functionDescription = functionDescription,
      completionPrefix = completionPrefix,
      completionMaxTokens = completionMaxTokens,
      chatMaxTokens = chatMaxTokens,
      temperature = temperature,
      shouldPrintResponse = false // because we'll print the parsed JSON below (or the full text if a parsing error occurs)
   )

   when (result) {
      is RunPromptResult.Success -> {
         val responseText = result.responseText

         val extractJsonResult = try {
            extractJsonFromPromptResponse(
               responseText,
               debugInfo = "($debugInfo, ${modelInfo.modelId})"
            )
         } catch (exception: Exception) {
            val errorId = buildShortRandomString()
            println("Exception while extracting/parsing JSON from prompt response: (errorId = $errorId, $debugInfo, ${modelInfo.modelId})\n${exception.stackTraceToString()}\nresponseText = $responseText")

            return RunJsonPromptResult.JsonParseFailed(
               usage = result.usage,
               errorId = errorId
            )
         }

         val json = Json {
            prettyPrint = true
         }

         println("Before text: " + extractJsonResult.textBeforeJson)
         println("Prompt response data ($debugInfo):\n${json.encodeToString(extractJsonResult.jsonData)}")
         println("After text: " + extractJsonResult.textAfterJson)

         return RunJsonPromptResult.Success(
            usage = result.usage,
            responseData = extractJsonResult.jsonData,
            textBeforeJson = extractJsonResult.textBeforeJson,
            textAfterJson = extractJsonResult.textAfterJson
         )
      }

      is RunPromptResult.LengthLimit -> {
         return RunJsonPromptResult.LengthLimit(errorId = result.errorId)
      }

      is RunPromptResult.RateLimitError -> {
         return RunJsonPromptResult.RateLimitError(errorId = result.errorId)
      }

      is RunPromptResult.UnknownApiError -> {
         return RunJsonPromptResult.UnknownApiError(errorId = result.errorId, exception = result.exception)
      }
   }
}
