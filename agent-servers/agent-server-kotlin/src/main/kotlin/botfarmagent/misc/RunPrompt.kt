package botfarmagent.misc

import botfarmshared.engine.apidata.PromptUsage
import botfarmshared.misc.JsonSchema
import botfarmshared.misc.buildShortRandomIdentifier
import com.aallam.openai.api.LegacyOpenAI
import com.aallam.openai.api.chat.*
import com.aallam.openai.api.completion.CompletionRequest
import com.aallam.openai.api.core.FinishReason
import com.aallam.openai.api.core.Usage
import com.aallam.openai.api.exception.GenericIOException
import com.aallam.openai.api.exception.RateLimitException
import com.aallam.openai.api.model.ModelId
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import java.text.DecimalFormat
import kotlin.math.roundToInt


sealed class RunPromptResult {
   class Success(
      val responseText: String,
      val usage: PromptUsage
   ) : RunPromptResult()

   class LengthLimit(val errorId: String, val usage: PromptUsage) : RunPromptResult()
   class RateLimitError(val errorId: String) : RunPromptResult()
   class ConnectionError(val errorId: String) : RunPromptResult()
   class UnknownApiError(val errorId: String, val exception: Exception) : RunPromptResult()
}

@OptIn(LegacyOpenAI::class)
suspend fun runPrompt(
   debugInfo: String,
   languageModelService: LanguageModelService,
   modelInfo: ModelInfo,
   prompt: String,
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
   println("Running prompt using model ${modelInfo.modelId}, ${debugInfo}:\n$prompt")
   println("Prompt token usage summary ${modelInfo.modelId}, ${debugInfo}:\n${promptBuilder.buildTokenUsageSummary()}")

   val totalTokensInPrompt = promptBuilder.totalTokens
   val totalTokensInPromptCost = modelInfo.costPer1kInput * (totalTokensInPrompt / 1000.0)
   val decimalFormat = DecimalFormat("#,###.##")

   println("Prompt input token cost cents: ${decimalFormat.format(totalTokensInPromptCost * 100)}")

   println("All-time total token count: $totalTokenCount")
   println("All-time total token count duration ms: ${totalTokenCountDurationMs.roundToInt()}")
   println("All-time average ms per 1k tokens: ${totalTokenCountDurationMs.roundToInt() / (totalTokenCount / 1000.0).roundToInt()}")

   val usage: Usage?
   val promptUsage: PromptUsage
   val responseText: String

   if (modelInfo.isCompletionModel) {
      val maxTokens = completionMaxTokens ?: (modelInfo.maxTokenCount - promptBuilder.totalTokens)

      val completionRequest = CompletionRequest(
         model = ModelId(modelInfo.modelId),
         temperature = temperature,
         prompt = prompt,
         maxTokens = maxTokens
      )

      val completion = try {
         languageModelService.completion(completionRequest)
      } catch (exception: RateLimitException) {
         val errorId = buildShortRandomIdentifier()
         println("Got rate limit exception running prompt (errorId = $errorId, $debugInfo):\n" + exception.stackTraceToString())
         return RunPromptResult.RateLimitError(errorId = errorId)
      } catch (exception: GenericIOException) {
         val errorId = buildShortRandomIdentifier()
         println("Got connection error running prompt (errorId = $errorId, $debugInfo):\n" + exception.stackTraceToString())
         return RunPromptResult.ConnectionError(errorId = errorId)
      } catch (exception: Exception) {
         val errorId = buildShortRandomIdentifier()
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
         val errorId = buildShortRandomIdentifier()

         println("Completion ended from max length (errorId = $errorId, $debugInfo, approximate input tokens = ${promptBuilder.totalTokens}, modelInfo.maxTokenCount = ${modelInfo.maxTokenCount}, promptTokens = ${promptUsage.promptTokens}, completionTokens = ${promptUsage.completionTokens}):\n" + completion.choices.first().text)

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
         languageModelService.chatCompletion(chatCompletionRequest)
      } catch (exception: RateLimitException) {
         val errorId = buildShortRandomIdentifier()
         println("Got rate limit exception running prompt (errorId = $errorId, $debugInfo, ${modelInfo.modelId}):\n" + exception.stackTraceToString())
         return RunPromptResult.RateLimitError(errorId = errorId)
      } catch (exception: GenericIOException) {
         val errorId = buildShortRandomIdentifier()
         println("Got connection error running prompt (errorId = $errorId, $debugInfo):\n" + exception.stackTraceToString())
         return RunPromptResult.ConnectionError(errorId = errorId)
      } catch (exception: Exception) {
         val errorId = buildShortRandomIdentifier()
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
         val errorId = buildShortRandomIdentifier()

         println("Chat completion ended from max length (errorId = $errorId, $debugInfo, approximate input tokens = ${promptBuilder.totalTokens}, modelInfo.maxTokenCount = ${modelInfo.maxTokenCount}, promptTokens = ${promptUsage.promptTokens}, completionTokens = ${promptUsage.completionTokens}):\n${completion.choices.first().message}")
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
      val responseText: String,
      val textBeforeJson: String,
      val textAfterJson: String,
      val usage: PromptUsage
   ) : RunJsonPromptResult()

   class JsonParseFailed(
      val errorId: String,
      val usage: PromptUsage
   ) : RunJsonPromptResult()

   class LengthLimit(val errorId: String, val usage: PromptUsage) : RunJsonPromptResult()
   class RateLimitError(val errorId: String) : RunJsonPromptResult()
   class ConnectionError(val errorId: String) : RunJsonPromptResult()
   class UnknownApiError(val errorId: String, val exception: Exception) : RunJsonPromptResult()
}

suspend fun runPromptWithJsonOutput(
   debugInfo: String,
   languageModelService: LanguageModelService,
   modelInfo: ModelInfo,
   prompt: String,
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
      languageModelService = languageModelService,
      modelInfo = modelInfo,
      prompt = prompt,
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
            val errorId = buildShortRandomIdentifier()
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
            responseText = responseText,
            textBeforeJson = extractJsonResult.textBeforeJson,
            textAfterJson = extractJsonResult.textAfterJson
         )
      }

      is RunPromptResult.LengthLimit -> {
         return RunJsonPromptResult.LengthLimit(errorId = result.errorId, usage = result.usage)
      }

      is RunPromptResult.RateLimitError -> {
         return RunJsonPromptResult.RateLimitError(errorId = result.errorId)
      }

      is RunPromptResult.ConnectionError -> {
         return RunJsonPromptResult.ConnectionError(errorId = result.errorId)
      }

      is RunPromptResult.UnknownApiError -> {
         return RunJsonPromptResult.UnknownApiError(errorId = result.errorId, exception = result.exception)
      }
   }
}


