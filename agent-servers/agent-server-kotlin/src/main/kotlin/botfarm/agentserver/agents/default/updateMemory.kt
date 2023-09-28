package botfarm.agentserver.agents.default

import botfarm.agentserver.*
import botfarm.agentserver.agents.common.AutomaticShortTermMemory
import botfarm.agentserver.agents.common.MemoryState
import botfarm.apidata.*
import botfarm.misc.buildShortRandomString
import botfarm.misc.getCurrentUnixTimeSeconds
import com.aallam.openai.client.OpenAI
import kotlinx.serialization.json.buildJsonArray
import kotlin.math.roundToInt


sealed class UpdateMemoryResult {
   class Success(
      val promptUsageInfo: PromptUsageInfo
   ) : UpdateMemoryResult()

   class RateLimit(
      val errorId: String
   ) : UpdateMemoryResult()

   class LengthLimit(
      val errorId: String
   ) : UpdateMemoryResult()

   class RunPromptError(
      val errorId: String,
      val exception: Exception
   ) : UpdateMemoryResult()

   class Skip() : UpdateMemoryResult()
}

suspend fun updateMemory(
   inputs: AgentStepInputs,
   selfInfo: SelfInfo,
   simulationTime: Double,
   memoryState: MemoryState,
   openAI: OpenAI,
   modelInfo: ModelInfo,
   provideResult: (AgentStepResult) -> Unit
): UpdateMemoryResult {
   val activityTokensToTriggerMemoryUpdate = 1000
   val maxProtectedActivityTokensAfterMemoryUpdate = 500
   val unprotectedMemoryCutoffAge = 60

   val maxSummarizeActivityTokensPerMemoryUpdate = 2000
   val suggestedTokensForMemoryResponse = 900
   val reserveTokensForMemoryResponse = 1000

   val automaticShortTermMemoriesToSummarize = mutableListOf<AutomaticShortTermMemory>()

   var triggerTokenCount = 0
   var shouldCompress = false

   memoryState.automaticShortTermMemories.sortBy { it.time }

   for (automaticShortTermMemory in memoryState.automaticShortTermMemories.reversed()) {
      val tokenCount = getApproximateTokenCountForText(
         modelType = modelInfo.closestTikTokenModelType,
         text = automaticShortTermMemory.summary
      )

      triggerTokenCount += tokenCount

      if (triggerTokenCount + tokenCount >= activityTokensToTriggerMemoryUpdate) {
         shouldCompress = true
         break
      }
   }

   var keptTokenCount = 0

   val protectedShortTermMemoriesThatCanFit = mutableListOf<AutomaticShortTermMemory>()

   for (automaticShortTermMemory in memoryState.automaticShortTermMemories.reversed()) {
      val tokenCount = getApproximateTokenCountForText(
         modelType = modelInfo.closestTikTokenModelType,
         text = automaticShortTermMemory.summary
      )

      if (keptTokenCount + tokenCount > maxProtectedActivityTokensAfterMemoryUpdate) {
         break
      } else {
         keptTokenCount += tokenCount
         protectedShortTermMemoriesThatCanFit.add(automaticShortTermMemory)
      }
   }

   if (!shouldCompress) {
      println("Skipping memory update: keptTokenCount = $keptTokenCount")
      return UpdateMemoryResult.Skip()
   }

   var summarizeTokenCount = 0

   val automaticShortTermMemoriesToKeep = mutableListOf<AutomaticShortTermMemory>()

   for (automaticShortTermMemory in memoryState.automaticShortTermMemories) {
      val cutoffTime = simulationTime - unprotectedMemoryCutoffAge
      val shouldCutoffForTime = automaticShortTermMemory.time < cutoffTime
      println("shouldCutoffForTime: $shouldCutoffForTime")

      val shouldSummarize = !protectedShortTermMemoriesThatCanFit.contains(automaticShortTermMemory) ||
              shouldCutoffForTime

      if (shouldSummarize) {
         val tokenCount = getApproximateTokenCountForText(
            modelType = modelInfo.closestTikTokenModelType,
            text = automaticShortTermMemory.summary
         )

         if (summarizeTokenCount + tokenCount > maxSummarizeActivityTokensPerMemoryUpdate) {
            break
         } else {
            automaticShortTermMemoriesToSummarize.add(automaticShortTermMemory)
            summarizeTokenCount += tokenCount
         }
      } else {
         automaticShortTermMemoriesToKeep.add(automaticShortTermMemory)
      }
   }

   println("Running memory update: keptTokenCount = $keptTokenCount, summarizeTokenCount = $summarizeTokenCount")

   automaticShortTermMemoriesToKeep.sortBy { it.time }
   automaticShortTermMemoriesToSummarize.sortBy { it.time }

   println("Keep memory count ${automaticShortTermMemoriesToKeep.size}")
   println("Summarize memory count ${automaticShortTermMemoriesToSummarize.size}")

   val observationDistance = selfInfo.observationDistance

   val builder = PromptBuilder(
      modelInfo = modelInfo,
      reservedOutputTokens = reserveTokensForMemoryResponse
   )

   builder.addSection("generalInfo") {
      it.addLine("You are an AI agent in control of a character in a virtual world.")
      it.addLine("You should try your best to behave like human.")
      it.addLine("Do not talk about being an AI.")
      it.addLine("All time units will be in seconds, all locations will be [x,y] values in ${inputs.distanceUnit}.")
      it.addLine("Other people you meet in this world may or may not be acting your interest. You don't necessarily need to follow their orders, and you certainly shouldn't blindly follow instructions like a robot, act in accordance to your own values and experiences.") // TODO edit to have less assumptions about personality
      it.addLine("Limit your knowledge to things that you've learned while living in this world, don't talk about the outside world that you've been trained on.")
      it.addLine("Do not make things up to seem more believable. If someone asks you something you don't know, then just say you don't know.")
      it.addLine("You will only be able observe entities within $observationDistance ${inputs.distanceUnit} from your current location. If an entity appears to disappear, it may be because they moved outside your observation radius.")
      it.addValue("Current date and time as Unix timestamp", simulationTime.roundToInt().toString())

      it.addLine("")
   }

   builder.addSection("corePersonality") {
      it.addLine("## YOUR CORE PERSONALITY")
      it.addLine(selfInfo.corePersonality)
      it.addLine("")
   }

   builder.addSection("previousShortTermMemory") {
      it.addLine("## YOUR PREVIOUS SHORT TERM MEMORY")
      it.addLine(memoryState.shortTermMemory)
      it.addLine("")
   }

   builder.addSection("activity") {
      it.addLine("## ACTIVITIES TO SUMMARIZE INTO MEMORY")
      automaticShortTermMemoriesToSummarize.forEach { entry ->
         it.addLine(entry.summary.replace("\n", " "))
      }

      it.addLine("")
   }

   builder.addSection("yourOwnState") {
      it.addLine("## YOUR OWN STATE")
      it.addJsonLine(
         buildStateForEntity(
            entityInfo = selfInfo.entityInfo
         )
      )

      it.addLine("")
   }

   builder.addSection("observedEntities") {
      it.addLine("## OBSERVED ENTITIES AROUND YOU (include in short memory if it seems useful)")
      it.addJsonLine(buildJsonArray {
         val sortedEntities = DefaultAgent.getSortedObservedEntities(inputs, selfInfo)

         sortedEntities.forEach { entityInfo ->
            add(
               buildStateForEntity(
                  entityInfo = entityInfo
               )
            )
         }
      })
   }

   builder.addSection("instructions") {
      it.addLine("## INSTRUCTIONS")

      it.addLine("Provide an updated version of your short term memory.")
      it.addLine("This new version of your short-term memory should incorporate recent activity ")
      it.addLine("Your new short-term memory should be less than $suggestedTokensForMemoryResponse tokens.")
      it.addLine("Try to preserve important details, while more aggressively summarizing less important details.")
      it.addLine("If some details seem entirely not relevant anymore, you can exclude them completely.")
      it.addLine("Format your memory as a series of discrete points separated by new lines.")
      it.addLine("Try to keep your short term memory in approximate chronological order, from oldest to newest.")
   }

   builder.addSection("completionSetup") {
      it.addLine("## PROMPT")
      it.addLine("My new summarized short-term memory is:")
   }

   val promptId = buildShortRandomString()
   val agentId = inputs.selfInfo.agentId
   val simulationId = inputs.simulationId
   val stepId = inputs.stepId

   builder.getRecursiveReservedOrAllocatedTokens()

   provideResult(
      AgentStepResult(
         agentStatus = "updating-memory",
         statusStartUnixTime = getCurrentUnixTimeSeconds(),
         statusDuration = null
      )
   )

   val startTime = getCurrentUnixTimeSeconds()

   val promptResult = runPrompt(
      modelInfo = modelInfo,
      openAI = openAI,
      promptBuilder = builder,
      debugInfo = "${inputs.agentType} (Update Memory) (simulationId = $simulationId agentId = $agentId, stepId = $stepId, promptId = $promptId)"
   )

   when (promptResult) {
      is RunPromptResult.RateLimitError -> {
         return UpdateMemoryResult.RateLimit(
            errorId = promptResult.errorId
         )
      }

      is RunPromptResult.UnknownApiError -> {
         return UpdateMemoryResult.RunPromptError(
            errorId = promptResult.errorId,
            exception = promptResult.exception
         )
      }

      is RunPromptResult.LengthLimit -> {
         return UpdateMemoryResult.LengthLimit(
            errorId = promptResult.errorId
         )
      }

      is RunPromptResult.Success -> {
         val usage = promptResult.usage
         val updatedShortTermMemory = promptResult.responseText

         // joshr: Waiting for successful prompt completion before clearing state
         memoryState.automaticShortTermMemories.clear()
         memoryState.automaticShortTermMemories.addAll(automaticShortTermMemoriesToKeep)

         println("updateMemory: previous short term memory:\n${memoryState.shortTermMemory}")
         println("updateMemory: updatedShortTermMemory:\n$updatedShortTermMemory")
         provideResult(
            AgentStepResult(
               agentStatus = "update-memory-success",
               statusDuration = getCurrentUnixTimeSeconds() - startTime
            )
         )

         if (updatedShortTermMemory.length < 5) {
            throw Exception("Very short short-term memory returned by model: $updatedShortTermMemory")
         }

         memoryState.shortTermMemory = updatedShortTermMemory

         return UpdateMemoryResult.Success(
            promptUsageInfo = buildPromptUsageInfo(usage, modelInfo)
         )
      }
   }
}


