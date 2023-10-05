package botfarmagent.misc

import com.aallam.openai.api.LegacyOpenAI
import com.aallam.openai.api.chat.ChatCompletion
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.completion.CompletionRequest
import com.aallam.openai.api.completion.TextCompletion
import com.aallam.openai.client.OpenAI

// jshmrsn: Currently using OpenAI types directly as this is
// the minimum effort required to mock OpenAI for testing
// Eventually, create abstractions over services-specific types
// so this interface can be used to swap out language model services
interface LanguageModelService {
   suspend fun completion(request: CompletionRequest): TextCompletion
   suspend fun chatCompletion(request: ChatCompletionRequest): ChatCompletion
}

class OpenAiLanguageModelService(
   val openAI: OpenAI
) : LanguageModelService {
   @OptIn(LegacyOpenAI::class)
   override suspend fun completion(request: CompletionRequest): TextCompletion =
      this.openAI.completion(request)

   override suspend fun chatCompletion(request: ChatCompletionRequest): ChatCompletion =
      this.openAI.chatCompletion(request)
}

class MockLanguageModelService(
   val handleCompletionRequest: (CompletionRequest) -> TextCompletion = { throw Exception("MockLanguageModelService: handleCompletionRequest not implemented") },
   val handleChatCompletionRequest: (ChatCompletionRequest) -> ChatCompletion = { throw Exception("MockLanguageModelService: handleChatCompletionRequest not implemented") }
) : LanguageModelService {
   override suspend fun completion(request: CompletionRequest): TextCompletion {
      return this.handleCompletionRequest(request)
   }

   override suspend fun chatCompletion(request: ChatCompletionRequest): ChatCompletion {
      return this.handleChatCompletionRequest(request)
   }
}
