package botfarmagent.game.agents.default

import botfarm.agentserver.*
import botfarm.agentserver.ModelInfo
import botfarm.agentserver.PromptBuilder
import botfarmagent.game.Agent
import botfarmagent.game.AgentContext
import botfarmagent.game.agents.common.*
import botfarmshared.engine.apidata.PromptUsageInfo
import botfarmshared.game.apidata.*
import botfarmshared.misc.buildShortRandomString
import botfarmshared.misc.getCurrentUnixTimeSeconds
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.math.roundToInt

private val constants = AgentResponseSchema_v1

class DefaultAgent(
   agentContext: AgentContext,
   val useGpt4: Boolean,
   val useFunctionCalling: Boolean
) : Agent(agentContext) {
   var previousPromptSendSimulationTime = -1000.0
   var previousPromptDoneUnixTime = -1000.0

   val memoryState = MemoryState()

   init {
      val initialInputs = this.initialInputs
      val simulationTime = initialInputs.simulationTime
      val selfInfo = initialInputs.selfInfo

      val initialLongTermMemories = selfInfo.initialMemories.mapIndexed { initialMemoryIndex, initialMemory ->
         LongTermMemory(
            createdAtLocation = selfInfo.entityInfo.location,
            importance = 100,
            createdTime = simulationTime,
            content = initialMemory,
            id = initialMemoryIndex
         )
      }

      initialLongTermMemories.forEach { memory ->
         this.memoryState.automaticShortTermMemories.add(
            AutomaticShortTermMemory(
               time = simulationTime,
               summary = "I had the thought: \"" + memory.content + "\"",
            )
         )
      }

      this.memoryState.longTermMemories.addAll(initialLongTermMemories)
   }

   override fun consumeInputs(inputs: AgentSyncInputs) {
      val pendingEvents = inputs.newObservations

      val newAutomaticShortTermMemories: List<AutomaticShortTermMemory> = mutableListOf<AutomaticShortTermMemory>()
         .also { newAutomaticShortTermMemories ->
            pendingEvents.activityStreamEntries.forEach { activityStreamEntry ->
               if (activityStreamEntry.sourceEntityId != inputs.selfInfo.entityInfo.entityId) {
                  newAutomaticShortTermMemories.add(
                     AutomaticShortTermMemory(
                        time = activityStreamEntry.time,
                        summary = activityStreamEntry.title + (activityStreamEntry.message?.let {
                           "\n" + activityStreamEntry.message
                        } ?: "")
                     )
                  )
               }
            }

            pendingEvents.selfSpokenMessages.forEach { selfSpokenMessage ->
               newAutomaticShortTermMemories.add(
                  AutomaticShortTermMemory(
                     time = selfSpokenMessage.time,
                     summary = "I said \"${selfSpokenMessage.message}\" (while standing at ${selfSpokenMessage.location.asJsonArrayRounded})",
                     forcePreviousActivity = true
                  )
               )
            }

            pendingEvents.spokenMessages.forEach { observedMessage ->
               newAutomaticShortTermMemories.add(
                  AutomaticShortTermMemory(
                     time = observedMessage.time,
                     summary = "I heard ${observedMessage.characterName} say \"${observedMessage.message}\" (they were at ${observedMessage.speakerLocation.asJsonArrayRounded})",
                  )
               )
            }

            fun buildReasonSuffix(reason: String?) = if (reason != null) {
               " (because ${reason})"
            } else {
               ""
            }

            pendingEvents.movementRecords.forEach { movement ->
               val movementSummary =
                  "I started walking from ${movement.startPoint.asJsonArrayRounded} to ${movement.endPoint.asJsonArrayRounded}" + buildReasonSuffix(
                     movement.reason
                  )

               newAutomaticShortTermMemories.add(
                  AutomaticShortTermMemory(
                     time = movement.startedAtTime,
                     summary = movementSummary,
                     deDuplicationCategory = "walkTo",
                     forcePreviousActivity = true
                  )
               )
            }

            pendingEvents.actionOnEntityRecords.forEach { record ->
               newAutomaticShortTermMemories.add(
                  AutomaticShortTermMemory(
                     time = record.startedAtTime,
                     summary = "I took the action '${record.actionId}' on entity '${record.targetEntityId}'" + buildReasonSuffix(
                        record.reason
                     ),
                     forcePreviousActivity = true
                  )
               )
            }

            pendingEvents.actionOnInventoryItemActionRecords.forEach { record ->
               newAutomaticShortTermMemories.add(
                  AutomaticShortTermMemory(
                     time = record.startedAtTime,
                     summary = "I performed action '${record.actionId}' on the item '${record.itemConfigKey}' from my inventory '${record.itemConfigKey}'" + buildReasonSuffix(
                        record.reason
                     ),
                     forcePreviousActivity = true
                  )
               )
            }

            pendingEvents.craftItemActionRecords.forEach { record ->
               newAutomaticShortTermMemories.add(
                  AutomaticShortTermMemory(
                     time = record.startedAtTime,
                     summary = "I crafted an '${record.itemConfigKey}' item" + buildReasonSuffix(
                        record.reason
                     ),
                     forcePreviousActivity = true
                  )
               )
            }
         }
         .sortedBy { it.time }

      this.memoryState.automaticShortTermMemories.addAll(newAutomaticShortTermMemories)

      this.memoryState.automaticShortTermMemories.sortBy { it.time }
      deDuplicateOldAutomaticMemories(this.memoryState.automaticShortTermMemories)
   }

   override suspend fun step(
      inputs: AgentSyncInputs
   ) {
      val openAI = this.context.openAI
      val simulationTimeForStep = inputs.simulationTime

      val modelInfo = if (this.useGpt4) {
         ModelInfo.gpt_4
      } else {
         if (this.useFunctionCalling) {
            ModelInfo.gpt_3_5_turbo
         } else {
            ModelInfo.gpt_3_5_turbo_instruct
         }
      }

      val simulationId = inputs.simulationId
      val syncId = inputs.syncId

      var wasRateLimited = false
      val errors = mutableListOf<String>()

      val promptUsages = mutableListOf<PromptUsageInfo>()

      while (true) {
         val updateMemoryResult = updateMemory(
            inputs = inputs,
            openAI = openAI,
            memoryState = this.memoryState,
            modelInfo = modelInfo,
            simulationTime = inputs.simulationTime,
            selfInfo = inputs.selfInfo,
            provideResult = {
               this.addPendingResult(it)
            }
         )

         when (updateMemoryResult) {
            is UpdateMemoryResult.Success -> {
               promptUsages.add(updateMemoryResult.promptUsageInfo)
            }

            is UpdateMemoryResult.RateLimit -> {
               wasRateLimited = true
               errors.add("Memory update prompt rate limited: " + updateMemoryResult.errorId)
            }

            is UpdateMemoryResult.ConnectionError -> {
               wasRateLimited = true
               errors.add("Memory update prompt connection error: " + updateMemoryResult.errorId)
            }

            is UpdateMemoryResult.LengthLimit -> {
               errors.add("Memory update prompt length limited: " + updateMemoryResult.errorId)
            }

            is UpdateMemoryResult.RunPromptError -> {
               errors.add("Prompt error for memory update: " + updateMemoryResult.errorId)
            }

            is UpdateMemoryResult.Skip -> {
               break
            }
         }
      }

      val previousActivity = this.memoryState.automaticShortTermMemories.filter { entry ->
         (entry.time <= this.previousPromptSendSimulationTime) || entry.forcePreviousActivity
      }

      val newActivity = this.memoryState.automaticShortTermMemories.filter { entry ->
         (entry.time > this.previousPromptSendSimulationTime) && !entry.forcePreviousActivity
      }

      val timeSincePrompt = getCurrentUnixTimeSeconds() - this.previousPromptDoneUnixTime
      val shouldPrompt = timeSincePrompt > 5.0

      if (!shouldPrompt) {
         return
      }

      this.addPendingResult(
         AgentStepResult(
            agentStatus = "running-prompt",
            statusStartUnixTime = getCurrentUnixTimeSeconds(),
            statusDuration = null
         )
      )

      println("Preparing to run prompt...")

      val selfInfo = inputs.selfInfo
      val currentLocation = selfInfo.entityInfo.location
      val observationDistance = selfInfo.observationDistance

      val completionMaxTokens = 500

      val builder = PromptBuilder(
         modelInfo = modelInfo,
         reservedOutputTokens = completionMaxTokens
      )

      val secondsSinceLastPrompt = if (previousPromptSendSimulationTime < 0) {
         0.0
      } else {
         simulationTimeForStep - this.previousPromptSendSimulationTime
      }

      builder.addSection("generalInfo") {
         it.addLine("## CORE INFO")
         it.addLine(
            """
            You are a human.
            You should try your best to behave like human.
            Other people you meet in this world may or may not be acting your interest. Act in accordance to your own values and experiences.
            Limit your knowledge to things that you've learned while living in this world, don't talk about the outside world that you've been trained on.
            Do not make things up to seem more believable. If someone asks you something you don't know, then just say you don't know.
            This prompt will be executed every few seconds, so you will have other opportunities to act and think in the future.
         """.trimIndent()
         )
         it.addLine("")
      }

//      Note: If people ask you about the date or time, format it as normal human readable text using the CST time zone

      val worldWidth = 3500
      val worldHeight = 3500

//      it.addLine("Check the activity stream to check if you've recently covered a topic to avoid repeating yourself.")
//      it.addLine("If you've recently asked someone a question and they haven't yet responded, don't ask them another question immediately. Give them ample time to respond. Don't say anything if there's nothing appropriate to say yet.")
//      it.addLine("If other people have said something since the last time you spoke, or if you meet someone new, you will often want to say something using the ${AgentResponseSchema_v1.iWantToSayKey} key.")

      builder.addSection("tips") {
         it.addLine("## TIPS")
         it.addText(
            """
            All time units will be in seconds, all locations will be [x,y] values in ${inputs.distanceUnit}.
            If other people have said something since the last time you spoke, or if you meet someone new, you will often want to say something using the iWantToSay key.
            If you've recently asked someone a question and they haven't yet responded, don't ask them another question immediately. Give them ample time to respond. Don't say anything if there's nothing appropriate to say yet.
            Avoid repeating yourself. You don't need to say something every prompt. If you've spoken recently, you can wait awhile. Especially if no one has said anything to you since the last time you talked. 
            People occupy about ${inputs.peopleSize} ${inputs.distanceUnit} of space, try to avoid walking to the exact same location of other people, instead walk to their side to politely chat.
            You will only be able observe entities within $observationDistance ${inputs.distanceUnit} from your current location. If an entity disappears, it may be because they moved outside your observation radius.
            Current date and time as Unix timestamp: ${simulationTimeForStep.roundToInt()}
            Seconds since your previous prompt: ${secondsSinceLastPrompt.roundToInt()}
            The available location to move to are between [0,0] and [$worldWidth,$worldHeight]
         """.trimIndent()
         )
         it.addLine("")
         it.addLine("")
      }


      builder.addSection("outputSchema") {
         it.addLine(
            """
            ## OUTPUT_SCHEMA
            Respond with a JSON object with the following top-level keys.
            Use the optional ${constants.locationToWalkToAndReasonKey} top-level key to walk to different locations (expects a JSON object with the keys location, reason)
            Use the optional ${constants.newThoughtsKey} top-level key to store important thoughts so you can remember them later (expects a JSON array of strings)
            Use the optional ${constants.iWantToSayKey} top-level key if you want to say something (expects a string value)
            Use the optional ${constants.actionOnEntityKey} top-level key when you want to use an action listed in availableActionIds of an OBSERVED_ENTITY (expects a JSON object with the keys targetEntityId, actionId, reason)
            Use the optional ${constants.actionOnInventoryItemKey} top-level key when you use an action listed in availableActionIds of an item in YOUR_ITEM_INVENTORY (expects a JSON object with the keys actionId, itemConfigKey, reason)
            Use the optional ${constants.craftItemKey} top-level key when you want to craft a new item (only if you have the needed cost items in your inventory) (expects a JSON object with the keys itemConfigKey, reason)
            Use the optional ${constants.useEquippedToolItemKey} top-level key if you want to use your currently equipped tool item (expects a JSON object with the key reason. This JSON object doesn't need an actionId or anything because it just assumes your equipped item)
            Use the required ${constants.facialExpressionEmojiKey} top-level key to show your current emotions (expects a string containing a single emoji)
            You can use multiple top-level key keys at once, for example if you want to talk and also move somewhere at the same time.
            Do not try to make up or guess an actionId, itemConfigKey, or targetEntityId. Only use the values that you see in this prompt. This is very important.
         """.trimIndent()
         )
         it.addLine("")
      }

      builder.addSection("corePersonality") {
         it.addLine("## YOUR CORE PERSONALITY")
         it.addLine(selfInfo.corePersonality)
         it.addLine("")
      }

      builder.addSection("craftingRecipes") {
         it.addLine("## ITEM_CRAFTING_RECIPES")
         inputs.craftingRecipes.forEach { craftingRecipe ->
            it.addJsonLine(buildJsonObject {
               put("itemConfigKey", craftingRecipe.itemConfigKey)
               put("itemName", craftingRecipe.itemName)
               putJsonArray("craftingCost") {
                  craftingRecipe.cost.entries.forEach {
                     addJsonObject {
                        put("costItemAmount", it.amount)
                        put("costItemConfigKey", it.itemConfigKey)
                     }
                  }
               }
            })
         }
         it.addLine("")
      }

      builder.addSection("yourInventory") {
         it.addLine("## YOUR_ITEM_INVENTORY")
         val inventory = selfInfo.inventoryInfo

         for (itemStack in inventory.itemStacks) {
            it.addJsonLine(buildJsonObject {
               put("itemConfigKey", itemStack.itemConfigKey)
               put("youHaveQuantity", itemStack.amount)

               putJsonArray("availableActionIds") {
                  if (itemStack.canBeEquipped) {
                     add(JsonPrimitive("equipItem"))
                  }

                  if (itemStack.canBeDropped) {
                     add(JsonPrimitive("dropItem"))
                  }
               }

               putJsonObject("itemInfo") {
                  put("itemName", itemStack.itemName)
                  put("itemDescription", itemStack.itemDescription)
               }
            })
         }

         it.addLine("")
      }

      builder.addSection("yourOwnState") {
         it.addLine("## YOUR_OWN_STATE")
         it.addJsonLine(
            buildStateForEntity(
               entityInfo = selfInfo.entityInfo
            )
         )

         it.addLine("")
      }

      builder.addSection("observedEntities", reserveTokens = 1000) {
         it.addLine("## OBSERVED_ENTITIES")

         val sortedEntities = getSortedObservedEntities(inputs, selfInfo)

         var entityIndex = 0
         for (entityInfo in sortedEntities) {
            val result = it.addJsonLine(
               buildStateForEntity(
                  entityInfo = entityInfo
               ),
               optional = true
            ).didFit

            if (!result) {
               println("Entity did not fit (${entityIndex + 1} / ${sortedEntities.size}): " + entityInfo.location.distance(selfInfo.entityInfo.location))
               break
            }

            ++entityIndex
         }
         it.addLine("", optional = true)
      }

      builder.addSection("shortTermMemory") {
         it.addLine("## YOUR_ACTIVE_MEMORY")
         it.addLine(this.memoryState.shortTermMemory)
         it.addLine("")
      }

      // jshmrsn: Add sections in order first, so we can add critical tokens before adding less critical tokens if they fit
      val recentActivitySection = builder.addSection("recentActivity")
      val newActivitySection = builder.addSection("newActivity")
      val instructionsSection = builder.addSection("instructions")

      val completionPrefix = "" // "{\n  "

      instructionsSection.also {
         it.addLine("## PROMPT")

         it.addLine(
            """
            First think through what you think about the activity you observed above and write down your thoughts and goals in plain English (not as part of the JSON). If these thoughts are important, you should include them in the ${constants.newThoughtsKey} top-level key.
            Think about what you could say with iWantToSay, what availableActionIds you could use on OBSERVED_ENTITIES, and what availableActionIds you could take on items in YOUR_ITEM_INVENTORY, etc. so you could do to make progress towards your goals.
            After that, you must write exactly one JSON object conforming to the OUTPUT_SCHEMA to express those goal-serving actions.
         """.trimIndent()
         )

         if (modelInfo.isCompletionModel) {
            it.addLine(
               """
            Your response:
            
"""
            )
         }

         it.addText(completionPrefix)
      }

      newActivitySection.also {
         val headerDidFit = it.addLine("## NEW_OBSERVED_ACTIVITY (YOU SHOULD CONSIDER REACTING TO THIS)", optional = true).didFit

         if (headerDidFit) {
            if (newActivity.isEmpty()) {
               it.addLine("<none>")
            } else {
               for (entry in newActivity) {
                  val secondsAgo = (inputs.simulationTime - entry.time).roundToInt()
                  val didFit =
                     it.addLine(entry.summary.replace("\n", " ") + " ($secondsAgo seconds ago)", optional = true).didFit

                  if (!didFit) {
                     break
                  }
               }
            }

            it.addLine("", optional = true)
         }
      }

      if (previousActivity.isNotEmpty()) {
         recentActivitySection.also {
            val headerDidFit = it.addLine("## PREVIOUS_OBSERVED_ACTIVITY (YOU MAY HAVE ALREADY REACTED TO THESE)", optional = true).didFit

            if (headerDidFit) {
               for (entry in previousActivity) {
                  val secondsAgo = (inputs.simulationTime - entry.time).roundToInt()
                  it.addLine(entry.summary.replace("\n", " ") + " ($secondsAgo seconds ago)", optional = true)
               }

               it.addLine("", optional = true)
            }
         }
      }


      val promptId = buildShortRandomString()

      val promptSendTime = getCurrentUnixTimeSeconds()

      val promptResult = runPromptWithJsonOutput(
         openAI = openAI,
         modelInfo = modelInfo,
         promptBuilder = builder,
         functionName = constants.functionName,
         functionSchema = constants.functionSchema,
         functionDescription = null,
         debugInfo = "${inputs.agentType} (step) ($simulationId, $agentId, syncId = $syncId, promptId = $promptId)",
         completionPrefix = completionPrefix,
         completionMaxTokens = completionMaxTokens,
         useFunctionCalling = this.useFunctionCalling
      )

      when (promptResult) {
         is RunJsonPromptResult.Success -> {
            promptUsages.add(buildPromptUsageInfo(promptResult.usage, modelInfo))
         }
         is RunJsonPromptResult.RateLimitError -> {
            return this.addPendingResult(
               AgentStepResult(
                  error = "Rate limit error running agent prompt (errorId = ${promptResult.errorId})",
                  wasRateLimited = true
               )
            )
         }

         is RunJsonPromptResult.ConnectionError -> {
            return this.addPendingResult(
               AgentStepResult(
                  error = "Connection error running agent prompt (errorId = ${promptResult.errorId})",
                  wasRateLimited = true
               )
            )
         }

         is RunJsonPromptResult.JsonParseFailed -> {
            promptUsages.add(buildPromptUsageInfo(promptResult.usage, modelInfo))

            return this.addPendingResult(
               AgentStepResult(
                  error = "Failed to parse json of prompt output (errorId = ${promptResult.errorId})",
                  wasRateLimited = true,
                  promptUsages = promptUsages
               )
            )
         }

         is RunJsonPromptResult.UnknownApiError -> {
            return this.addPendingResult(
               AgentStepResult(
                  error = "Unknown prompt API error (errorId = ${promptResult.errorId}): " + promptResult::class.simpleName
               )
            )
         }

         is RunJsonPromptResult.LengthLimit -> {
            promptUsages.add(buildPromptUsageInfo(promptResult.usage, modelInfo))

            return this.addPendingResult(
               AgentStepResult(
                  error = "Prompt exceeded max length (errorId = ${promptResult.errorId})",
                  promptUsages = promptUsages
               )
            )
         }
      }

      this.previousPromptDoneUnixTime = getCurrentUnixTimeSeconds()

      val responseData = promptResult.responseData
      val textBeforeJson = promptResult.textBeforeJson

      val response = try {
         Json.decodeFromJsonElement<AgentResponseSchema_v1.AgentResponseFunctionInputs>(responseData)
      } catch (exception: Exception) {
         val prettyJson = Json { prettyPrint = true }
         throw Exception(
            "Exception while decoding agent response JSON (promptId = $promptId, syncId = $syncId, agentId = $agentId):\n${
               prettyJson.encodeToString(
                  responseData
               )
            }", exception
         )
      }

      this.previousPromptSendSimulationTime = simulationTimeForStep

      val iWantToSay = response.iWantToSay

      val facialExpressionEmoji = response.facialExpressionEmoji

      val locationToWalkToAndReason = response.locationToWalkToAndReason

      val actionOnEntity = response.actionOnEntity

      val actionOnInventoryItem = response.actionOnInventoryItem
      val craftItemAction = response.craftItem
      val useEquippedToolItem = response.useEquippedToolItem

      val newThoughts = response.newThoughts ?: listOf()

      val newLongTermMemories = newThoughts.map { newLongTermMemory ->
         LongTermMemory(
            createdTime = simulationTimeForStep,
            content = newLongTermMemory,
            id = this.memoryState.longTermMemories.size + 1,
            createdAtLocation = currentLocation,
            importance = 0
         )
      }

      this.memoryState.longTermMemories.addAll(newLongTermMemories)

//      if (textBeforeJson.length > 5) {
//         this.memoryState.automaticShortTermMemories.add(
//            AutomaticShortTermMemory(
//               time = simulationTimeForStep,
//               summary = "I reasoned: " + textBeforeJson,
//            )
//         )
//      }

      newLongTermMemories.forEach { memory ->
         this.memoryState.automaticShortTermMemories.add(
            AutomaticShortTermMemory(
               time = simulationTimeForStep,
               summary = "I had the thought: " + memory.content,
            )
         )
      }

      val actions = Actions(
         walk = locationToWalkToAndReason,
         actionOnEntity = actionOnEntity,
         actionOnInventoryItem = actionOnInventoryItem,
         speak = iWantToSay,
         facialExpressionEmoji = facialExpressionEmoji,
         craftItemAction = craftItemAction,
         useEquippedToolItem = useEquippedToolItem
      )

      val newDebugInfoLines = mutableListOf<String>()
      newDebugInfoLines.add("### Sync ID: $syncId")
      newDebugInfoLines.add("")


      newDebugInfoLines.add("### Short-Term Memory")
      newDebugInfoLines.add("")

      newDebugInfoLines.add(this.memoryState.shortTermMemory)

      newDebugInfoLines.add("")

      newDebugInfoLines.add("### Previous Activity")
      previousActivity.forEach {
         newDebugInfoLines.add(it.summary)
      }

      newDebugInfoLines.add("### New Activity")
      newActivity.forEach {
         newDebugInfoLines.add(it.summary)
      }

      this.addPendingResult(
         AgentStepResult(
            actions = actions,
            newDebugInfo = newDebugInfoLines.joinToString("  \n"), // two spaces from https://github.com/remarkjs/react-markdown/issues/273
            statusDuration = getCurrentUnixTimeSeconds() - promptSendTime,
            agentStatus = "prompt-finished",
            promptUsages = promptUsages,
            wasRateLimited = wasRateLimited,
            error = if (errors.isNotEmpty()) {
               errors.joinToString("\n")
            } else {
               null
            }
         )
      )
   }
}

fun buildStateForEntity(
   entityInfo: EntityInfo
): JsonObject {
   val characterInfo = entityInfo.characterInfo

   return buildJsonObject {
      put("entityId", entityInfo.entityId.value)

      if (characterInfo != null) {
         put("description", characterInfo.description)
         put("name", characterInfo.name)
         put("age", characterInfo.age)
         put("gender", characterInfo.gender)
      }

      val itemInfo = entityInfo.itemInfo

      if (itemInfo != null) {
         put("itemName", itemInfo.itemName)
         put("description", itemInfo.description)
      }

      if (entityInfo.availableActionIds != null) {
         putJsonArray("availableActionIds") {
            entityInfo.availableActionIds.forEach {
               add(it)
            }
         }
      }

      put("location", entityInfo.location.asJsonArrayRounded)
   }
}
