package botfarmagent.game.agents.codeexecution

import botfarmagent.game.Agent
import botfarmagent.game.AgentContext
import botfarmagent.game.common.*
import botfarmagent.misc.*
import botfarmshared.engine.apidata.EntityId
import botfarmshared.engine.apidata.PromptUsageInfo
import botfarmshared.game.apidata.*
import botfarmshared.misc.buildShortRandomIdentifier
import botfarmshared.misc.getCurrentUnixTimeSeconds
import kotlin.math.roundToInt

class CodeExecutionAgent(
   context: AgentContext,
   val useGpt4: Boolean
) : Agent(context) {
   var previousPromptSendSimulationTime = -1000.0
   var previousPromptDoneUnixTime = -1000.0

   val memoryState = MemoryState()

   val receivedActionStartedIds = mutableSetOf<String>()
   val receivedActionResultById = mutableMapOf<String, ActionResult>()

   init {
      val initialInputs = this.initialSyncInput
      val simulationTime = initialInputs.simulationTime
      val selfInfo = initialInputs.selfInfo


      val initialLongTermMemories = selfInfo.initialMemories.mapIndexed { initialMemoryIndex, initialMemory ->
         LongTermMemory(
            createdAtLocation = selfInfo.entityInfoWrapper.entityInfo.location,
            importance = 100,
            createdTime = simulationTime,
            content = initialMemory,
            id = initialMemoryIndex
         )
      }

      val initialAutomaticShortTermMemory = initialLongTermMemories.map { memory ->
         AutomaticShortTermMemory(
            time = simulationTime,
            summary = "I had the thought: \"" + memory.content + "\"",
            forcePreviousActivity = true
         )
      }

      this.memoryState.automaticShortTermMemories.addAll(initialAutomaticShortTermMemory)
      this.memoryState.automaticShortTermMemoriesSinceLastPrompt.addAll(initialAutomaticShortTermMemory)

      this.memoryState.longTermMemories.addAll(initialLongTermMemories)
   }

   override fun consumeInput(input: AgentSyncInput) {
      val newObservations = input.newObservations

      newObservations.startedActionUniqueIds.forEach {
         println("Got action started: $it")
         this.receivedActionStartedIds.add(it)
      }

      newObservations.actionResults.forEach {
         println("Got action completed result: ${it.actionUniqueId}")
         this.receivedActionResultById[it.actionUniqueId] = it
      }

      val newAutomaticShortTermMemories: List<AutomaticShortTermMemory> = mutableListOf<AutomaticShortTermMemory>()
         .also { newAutomaticShortTermMemories ->
            newObservations.activityStreamEntries.forEach { activityStreamEntry ->
               if (activityStreamEntry.sourceEntityId != input.selfInfo.entityInfoWrapper.entityInfo.entityId) {
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

            newObservations.selfSpokenMessages.forEach { selfSpokenMessage ->
               newAutomaticShortTermMemories.add(
                  AutomaticShortTermMemory(
                     time = selfSpokenMessage.time,
                     summary = "I said \"${selfSpokenMessage.message}\" (while standing at ${selfSpokenMessage.location.asJsonArrayRounded})",
                     forcePreviousActivity = true
                  )
               )
            }

            newObservations.selfThoughts.forEach { selfThought ->
               newAutomaticShortTermMemories.add(
                  AutomaticShortTermMemory(
                     time = selfThought.time,
                     summary = "I had the thought \"${selfThought.thought}\" (while standing at ${selfThought.location.asJsonArrayRounded})",
                     forcePreviousActivity = true
                  )
               )
            }

            newObservations.spokenMessages.forEach { observedMessage ->
               newAutomaticShortTermMemories.add(
                  AutomaticShortTermMemory(
                     time = observedMessage.time,
                     summary = "I heard ${observedMessage.characterName} say \"${observedMessage.message}\" (they were at ${observedMessage.speakerLocation.asJsonArrayRounded})",
                     isHighPriority = true
                  )
               )
            }

            fun buildReasonSuffix(reason: String?) = if (reason != null) {
               " (because ${reason})"
            } else {
               ""
            }

            newObservations.movementRecords.forEach { movement ->
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

            newObservations.actionOnEntityRecords.forEach { record ->
               newAutomaticShortTermMemories.add(
                  AutomaticShortTermMemory(
                     time = record.startedAtTime,
                     summary = "I took the action '${record.actionId}' on entity '${record.targetEntityId.value}'" + buildReasonSuffix(
                        record.reason
                     ),
                     forcePreviousActivity = true
                  )
               )
            }

            newObservations.actionOnInventoryItemActionRecords.forEach { record ->
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

            newObservations.craftItemActionRecords.forEach { record ->
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
      this.memoryState.automaticShortTermMemoriesSinceLastPrompt.addAll(newAutomaticShortTermMemories)

      this.memoryState.automaticShortTermMemories.sortBy { it.time }
      this.memoryState.automaticShortTermMemoriesSinceLastPrompt.sortBy { it.time }

      deDuplicateOldAutomaticMemories(this.memoryState.automaticShortTermMemories)
      deDuplicateOldAutomaticMemories(this.memoryState.automaticShortTermMemoriesSinceLastPrompt)
   }

   override suspend fun step(
      input: AgentSyncInput
   ) {
      val gameConstants = input.gameConstants
      val gameSimulationInfo = input.gameSimulationInfo
      val worldBounds = gameSimulationInfo.worldBounds

      val simulationTimeForStep = input.simulationTime

      val modelInfo = if (this.useGpt4) {
         ModelInfo.gpt_4
      } else {
         ModelInfo.gpt_3_5_turbo_instruct
      }

      val simulationId = input.simulationId
      val syncId = input.syncId

      var wasRateLimited = false
      val errors = mutableListOf<String>()

      val promptUsages = mutableListOf<PromptUsageInfo>()

      while (true) {
         val updateMemoryResult = updateMemory(
            inputs = input,
            languageModelService = this.context.languageModelService,
            memoryState = this.memoryState,
            modelInfo = modelInfo,
            simulationTime = input.simulationTime,
            selfInfo = input.selfInfo,
            provideResult = {
               this.addPendingOutput(it)
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

      val automaticShortTermMemoriesSinceLastPrompt = this.memoryState.automaticShortTermMemoriesSinceLastPrompt

      val hasHighPriorityMemorySinceLastPrompt = automaticShortTermMemoriesSinceLastPrompt.find {
         it.isHighPriority
      } != null

      val timeSincePrompt = getCurrentUnixTimeSeconds() - this.previousPromptDoneUnixTime

      val newPromptTimeLimit = if (hasHighPriorityMemorySinceLastPrompt) {
         8.0
         // TODO: Inform agent when script is complete
//      } else if (this.activeScriptPromptId == null) {
//         15.0
      } else {
         30.0
      }

      val shouldPrompt = timeSincePrompt > newPromptTimeLimit

      if (!shouldPrompt) {
         println("Not prompting yet (${timeSincePrompt.roundToInt()} / $newPromptTimeLimit seconds) ($agentId)")
         return
      }

      this.addPendingOutput(
         AgentSyncOutput(
            agentStatus = "running-prompt",
            statusStartUnixTime = getCurrentUnixTimeSeconds(),
            statusDuration = null
         )
      )

      println("Preparing to run prompt...")

      val selfInfo = input.selfInfo
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
            You will interact with the world by responding to this prompt.
            New iterations of this prompt will be called periodically so that you can interact with the world over time.
            However, new prompts are generated at long intervals, so try to make as much progress as you can with the output of each prompt.
            Your role is to be social, solve problems, and make progress.
            You will be given a representation of the world as a block of TypeScript code.
            You will respond with a block of JavaScript code that uses the interfaces and objects provided by the TypeScript representation of world, in order to interact with the world, carry out your intentions, and express yourself. 
            
            As you take actions, the simulation will automatically change values dynamically.
            Your code should not try to directly modify the values of entities or items.

            If you have a thought, observation, or reflection you'd like to remember for later, use the recordThought function to remember it.
            If you don't use the recordThought function, you will forget it.
            
            Other people you meet in this world may or may not be acting your interest.
            Act in accordance to your own values and experiences.
            Limit your knowledge to things that you've learned while living in this world, don't talk about the outside world that you've been trained on.
            Do not make things up to seem more believable. If someone asks you something you don't know, then just say you don't know.
         """.trimIndent()
         )
         it.addLine("")
      }

      val worldWidth = worldBounds.x
      val worldHeight = worldBounds.y

      builder.addSection("tips") {
         it.addLine("## TIPS")
         it.addText(
            """
            All time units will be in seconds, all locations will be [x,y] values in ${gameConstants.distanceUnit}.
            If other people have said something since the last time you spoke, or if you meet someone new, you will often want to say something.
            If you've recently asked someone a question and they haven't yet responded, don't ask them another question immediately. Give them ample time to respond. Don't say anything if there's nothing appropriate to say yet.
            People occupy about ${gameConstants.peopleSize} ${gameConstants.distanceUnit} of space, try to avoid walking to the exact same location of other people, instead walk to their side to politely chat.
            You will only be able observe entities within $observationDistance ${gameConstants.distanceUnit} from your current location. If an entity disappears, it may be because they moved outside your observation radius.
            Current date and time as Unix timestamp: ${simulationTimeForStep.roundToInt()}
            Seconds since your previous prompt: ${secondsSinceLastPrompt.roundToInt()}
            The available location to move to are between [0,0] and [$worldWidth,$worldHeight]
         """.trimIndent()
         )
         it.addLine("")
         it.addLine("")
      }

      builder.addSection("corePersonality") {
         it.addLine("## YOUR CORE PERSONALITY")
         it.addLine(selfInfo.corePersonality)
         it.addLine("")
      }

      builder.addSection("shortTermMemory") {
         it.addLine("## YOUR MEMORY")
         it.addLine(this.memoryState.shortTermMemory)
         it.addLine("")
      }

      // jshmrsn: Add sections in order first, so we can add critical tokens before adding less critical tokens if they fit
      val recentActivitySection = builder.addSection("recentActivity")
      val newActivitySection = builder.addSection("newActivity")

      builder.addSection("codeBlockStartSection").also {
         it.addLine("```ts")
      }

      val interfacesSection = builder.addSection("interfaces")
      val interfacesSource = CodeExecutionAgent::class.java.getResource("/scripted-agent-interfaces.ts")?.readText()
         ?: throw Exception("Scripted agent interfaces resource not found")

      interfacesSection.addLine(interfacesSource)

      val craftingRecipesSection = builder.addSection(
         "craftingRecipesSection"
      ).also {
         it.addLine("// Crafting recipes:")
      }

      builder.addSection(
         "after craftingRecipesSection"
      ).also {
         it.addLine("")
         it.addLine("")
      }

      val uniqueEntityListSection = builder.addSection(
         "uniqueEntityListSection",
         reserveTokens = (modelInfo.maxTokenCount * 0.333).roundToInt()
      )

      val nonUniqueEntityListSection = builder.addSection(
         "nonUniqueEntityListSection",
         reserveTokens = 300
      )

      val omittedEntityIntroSection = builder.addSection(
         "omittedEntityIntroSection",
         reserveTokens = 50
      )

      val omittedEntitySummarySection = builder.addSection(
         "omittedEntitySummarySection"
      )

      builder.addSection("afterEntityListSection").also {
         it.addLine("")
         it.addLine("")
      }

      val inventoryListSection = builder.addSection("inventoryListSection", reserveTokens = 700)
      val inventorySummarySection = builder.addSection("inventorySummarySection", reserveTokens = 300)

      builder.addSection("after inventoryListSection").also {
         it.addLine("")
         it.addLine("")
      }

      val selfSection = builder.addSection("selfSection").also {
         it.addLine("// Your own entity state:")
      }

      builder.addSection("after selfSection").also {
         it.addLine("")
      }

      builder.addSection("codeBlockEndSection").also {
         it.addLine("```")
      }

      val finalInstructionsSection = builder.addSection("finalInstructionsSection")

      finalInstructionsSection.addLine("Respond with a block of JavaScript code that uses the interfaces and objects provided by the TypeScript representation of world, in order to interact with the world, carry out your intentions, and express yourself socially.")
      finalInstructionsSection.addLine("Only respond with the block of JavaScript code, don't explain it.")
      finalInstructionsSection.addLine("Surround your code block with markdown tags.")
      finalInstructionsSection.addLine("Write your code as top-level statements.")
      finalInstructionsSection.addLine("Your output JavaScript should be about 1-20 lines long.")
      finalInstructionsSection.addLine("Your output can perform multiple steps to achieve a more complex compound action.")
      finalInstructionsSection.addLine("Your output can involve loops to repeat repetitive tasks.")
      finalInstructionsSection.addLine("When you call functions to perform actions, those actions will complete before the function returns, so you can safely call multiple action functions without manually waiting for actions to complete.")
      finalInstructionsSection.addLine("For example, if you want to chop down many trees, your code could loop through them and attack each one, instead of only attacking then nearest tree.")
      finalInstructionsSection.addLine("Other people cannot see your code or comments in your code. If you want to express something to other people, you need to use the speak() function.")
      finalInstructionsSection.addLine("You will not remember your code or your comments in your next prompt. If you want to remember something, use the recordThought() function.")

      val completionPrefix = ""

      newActivitySection.also {
         val headerDidFit =
            it.addLine("## NEW_OBSERVED_ACTIVITY (YOU SHOULD CONSIDER REACTING TO THIS)", optional = true).didFit

         if (headerDidFit) {
            if (newActivity.isEmpty()) {
               it.addLine("<none>")
            } else {
               for (entry in newActivity) {
                  val secondsAgo = (input.simulationTime - entry.time).roundToInt()
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
            val headerDidFit = it.addLine(
               "## PREVIOUS_OBSERVED_ACTIVITY (YOU MAY HAVE ALREADY REACTED TO THESE)",
               optional = true
            ).didFit

            if (headerDidFit) {
               for (entry in previousActivity) {
                  val secondsAgo = (input.simulationTime - entry.time).roundToInt()
                  it.addLine(entry.summary.replace("\n", " ") + " ($secondsAgo seconds ago)", optional = true)
               }

               it.addLine("", optional = true)
            }
         }
      }


      craftingRecipesSection.also {
         for (craftingRecipeInfoWrapper in gameSimulationInfo.craftingRecipeInfoWrappers) {
            val craftingRecipeInfo = craftingRecipeInfoWrapper.craftingRecipeInfo

            val variableName = craftingRecipeInfoWrapper.javaScriptVariableName

            val serializedAsCode = craftingRecipeInfoWrapper.serializedAsJavaScript

            val variableDeclarationAsCode =
               "const $variableName: CraftingRecipe = $serializedAsCode"

            val addLineResult = it.addLine(
               variableDeclarationAsCode,
               optional = true
            ).didFit

            if (!addLineResult) {
               println("Crafting item did not fit")
               break
            }
         }
      }

      inventoryListSection.also {
         val inventory = selfInfo.inventoryInfo

         if (inventory.itemStacks.isEmpty()) {
            it.addLine("// You have no inventory items")
         } else {
            it.addLine("// Your inventory item stacks:")

            val checkedItemTypes = mutableSetOf<String>()
            for ((stackIndex, itemStackInfoWrapper) in inventory.itemStacks.withIndex()) {
               val itemStackInfo = itemStackInfoWrapper.itemStackInfo
               if (!checkedItemTypes.contains(itemStackInfo.itemConfigKey)) {
                  checkedItemTypes.add(itemStackInfo.itemConfigKey)


                  val itemStackVariableName = itemStackInfoWrapper.javaScriptVariableName
                  val serializedAsJavaScript = itemStackInfoWrapper.serializedAsJavaScript

                  val inventoryItemStackAsCode =
                     "const $itemStackVariableName: InventoryItemStack = $serializedAsJavaScript"

                  val addLineResult = it.addLine(
                     inventoryItemStackAsCode,
                     optional = true
                  ).didFit

                  if (!addLineResult) {
                     println("Inventory item did not fit (${stackIndex + 1} / ${inventory.itemStacks.size})")
                     break
                  }
               }
            }

            inventorySummarySection.addLine("// NOTE: Only the first stack of each inventory item was shown above, use getCurrentInventoryItemStacks to analyze your full inventory.")
            inventorySummarySection.addLine("//   Summary of your fully inventory:")
            checkedItemTypes.forEach { checkedItemType ->
               val totalAmount = inventory.itemStacks
                  .filter { it.itemStackInfo.itemConfigKey == checkedItemType }
                  .sumOf { it.itemStackInfo.amount }
               inventorySummarySection.addLine("//     $checkedItemType: $totalAmount")
            }
         }
      }

      selfSection.addLine("const ${selfInfo.entityInfoWrapper.javaScriptVariableName}: Entity = ${selfInfo.entityInfoWrapper.serializedAsJavaScript}")


      val groupedSortedEntities = getGroupedSortedObservedEntities(input, selfInfo)

      uniqueEntityListSection.addLine("// Nearby entities in the world:")
      var nextEntityIndex = 0
      val addedEntityIds = mutableSetOf<EntityId>()

      fun addEntitiesFromList(
         section: PromptBuilder,
         entities: List<EntityInfoWrapper>
      ) {
         for (entityInfoWrapper in entities) {
            val entityInfo = entityInfoWrapper.entityInfo

            val entityVariableName = entityInfoWrapper.javaScriptVariableName
            val serializedAsJavaScript = entityInfoWrapper.serializedAsJavaScript

            val entityAsCode = "const $entityVariableName: Entity = $serializedAsJavaScript"

            val addLineResult = section.addLine(
               entityAsCode,
               optional = true
            ).didFit

            if (!addLineResult) {
               break
            }

            addedEntityIds.add(entityInfo.entityId)

            ++nextEntityIndex
         }
      }

      addEntitiesFromList(
         section = uniqueEntityListSection,
         entities = groupedSortedEntities.uniqueEntities
      )

      // jshmrsn: Not bothering to add non-unique entities to save on tokens
//      addEntitiesFromList(
//         section = nonUniqueEntityListSection,
//         entities = groupedSortedEntities.nonUniqueEntities
//      )

      val remainingEntities = (groupedSortedEntities.uniqueEntities + groupedSortedEntities.nonUniqueEntities).filter {
         it.entityInfo.entityId !in addedEntityIds
      }

      if (remainingEntities.isNotEmpty()) {
         omittedEntityIntroSection.addLine("// NOTE: ${remainingEntities.size} entities were omitted from the above list to reduce prompt size.")
         omittedEntityIntroSection.addLine("// You can use getCurrentNearbyEntities() in your response script to get the full list")

         omittedEntityIntroSection.addLine("//   Summary of omitted entities:")

         val characterEntities = remainingEntities.filter { it.entityInfo.characterInfo != null }

         if (characterEntities.isNotEmpty()) {
            omittedEntitySummarySection.addLine(
               "//     ${characterEntities.size} were other character entities",
               optional = true
            )
         }

         val itemInfos = remainingEntities.mapNotNull { it.entityInfo.itemInfo }

         val itemEntityCounts: Map<String, Int> = mutableMapOf<String, Int>().also {
            itemInfos.forEach { itemInfo ->
               it[itemInfo.itemConfigKey] = (it[itemInfo.itemConfigKey] ?: 0) + 1
            }
         }

         itemEntityCounts
            .entries
            .sortedBy { it.key }
            .forEach {
               omittedEntitySummarySection.addLine("//     ${it.value} were '${it.key}' item entities", optional = true)
            }
      }

      val text = builder.buildText()
      println("prompt: $text")

      val promptSendTime = getCurrentUnixTimeSeconds()
      val promptId = buildShortRandomIdentifier()


      val promptResult = runPrompt(
         languageModelService = this.context.languageModelService,
         modelInfo = modelInfo,
         promptBuilder = builder,
         debugInfo = "${input.agentType} (step) ($simulationId, $agentId, syncId = $syncId, promptId = $promptId)",
         completionPrefix = completionPrefix,
         completionMaxTokens = completionMaxTokens,
         useFunctionCalling = false,
         temperature = 0.2
      )

      when (promptResult) {
         is RunPromptResult.Success -> {
            promptUsages.add(buildPromptUsageInfo(promptResult.usage, modelInfo))
         }

         is RunPromptResult.RateLimitError -> {
            return this.addPendingOutput(
               AgentSyncOutput(
                  error = "Rate limit error running agent prompt (errorId = ${promptResult.errorId})",
                  wasRateLimited = true,
                  promptUsages = promptUsages
               )
            )
         }

         is RunPromptResult.ConnectionError -> {
            return this.addPendingOutput(
               AgentSyncOutput(
                  error = "Connection error running agent prompt (errorId = ${promptResult.errorId})",
                  wasRateLimited = true,
                  promptUsages = promptUsages
               )
            )
         }

         is RunPromptResult.UnknownApiError -> {
            return this.addPendingOutput(
               AgentSyncOutput(
                  error = "Unknown prompt API error (errorId = ${promptResult.errorId}): " + promptResult::class.simpleName,
                  promptUsages = promptUsages
               )
            )
         }

         is RunPromptResult.LengthLimit -> {
            promptUsages.add(buildPromptUsageInfo(promptResult.usage, modelInfo))

            return this.addPendingOutput(
               AgentSyncOutput(
                  error = "Prompt exceeded max length (errorId = ${promptResult.errorId})",
                  promptUsages = promptUsages
               )
            )
         }
      }

      val responseText = promptResult.responseText


      this.previousPromptDoneUnixTime = getCurrentUnixTimeSeconds()
      this.previousPromptSendSimulationTime = simulationTimeForStep

      this.memoryState.automaticShortTermMemoriesSinceLastPrompt.clear()

      val codeBlockStartIndex = responseText.indexOf("```")
      val responseScript = if (codeBlockStartIndex >= 0) {
         val textAfterBlockStart = responseText.substring(codeBlockStartIndex + 1)
         val newlineIndex = textAfterBlockStart.indexOf("\n")
         val textAfterBlockStartLine = textAfterBlockStart.substring(newlineIndex + 1)
         val blockEndIndex = textAfterBlockStartLine.indexOf("```")

         if (blockEndIndex < 0) {
            throw Exception("Script block end not found:\n$textAfterBlockStartLine")
         } else {
            textAfterBlockStartLine.substring(0, blockEndIndex)
         }
      } else {
         responseText
      }

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

      newDebugInfoLines.add("### Agent Script")
      newDebugInfoLines.add("```ts\n$responseScript\n```")

      println("===== RESPONSE SCRIPT ($promptId) ====== \n$responseScript\n===============")

      this.addPendingOutput(
         AgentSyncOutput(
            script = responseScript,
            debugInfo = newDebugInfoLines.joinToString("  \n"), // two spaces from https://github.com/remarkjs/react-markdown/issues/273
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
