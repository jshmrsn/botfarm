package botfarmagent.game.agents.scriptexecution

import botfarmagent.game.Agent
import botfarmagent.game.AgentContext
import botfarmagent.game.common.*
import botfarmagent.misc.*
import botfarmshared.engine.apidata.EntityId
import botfarmshared.engine.apidata.PromptUsage
import botfarmshared.engine.apidata.PromptUsageInfo
import botfarmshared.game.apidata.AgentSyncInput
import botfarmshared.game.apidata.AgentSyncOutput
import botfarmshared.game.apidata.EntityInfoWrapper
import botfarmshared.game.apidata.ScriptToRun
import botfarmshared.misc.buildShortRandomIdentifier
import botfarmshared.misc.getCurrentUnixTimeSeconds
import kotlin.math.roundToInt

class ScriptExecutionAgent(
   context: AgentContext,
   val shouldUseGpt4: Boolean,
   val shouldUseMockResponses: Boolean
) : Agent(context) {
   var nextMockResponseIndex = 1

   var previousPromptSendSimulationTime = -1000.0
   var previousPromptDoneUnixTime = -1000.0

   val memoryState = MemoryState()

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

      val newShortTermMemories = buildAutomaticShortTermMemoriesForNewObservations(
         newObservations = newObservations,
         selfEntityId = input.selfInfo.entityInfoWrapper.entityInfo.entityId
      )

      addNewAutomaticShortTermMemories(this.memoryState.automaticShortTermMemories, newShortTermMemories)
      addNewAutomaticShortTermMemories(this.memoryState.automaticShortTermMemoriesSinceLastPrompt, newShortTermMemories)

      val pendingNewActivityDebugString =
         this.memoryState.automaticShortTermMemoriesSinceLastPrompt.joinToString("\n") { it.summary }

      this.addPendingOutput(
         AgentSyncOutput(
            debugInfoByKey = mapOf(
               "Pending New Activity" to pendingNewActivityDebugString
            )
         )
      )
   }

   override suspend fun step(
      input: AgentSyncInput
   ) {
      val gameConstants = input.gameConstants
      val gameSimulationInfo = input.gameSimulationInfo
      val worldBounds = gameSimulationInfo.worldBounds

      val simulationTimeForStep = input.simulationTime

      val modelInfo = if (this.shouldUseGpt4) {
         ModelInfo.gpt_4
      } else {
         ModelInfo.gpt_3_5_turbo
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

      val automaticShortTermMemoriesSinceLastPrompt =
         this.memoryState.automaticShortTermMemoriesSinceLastPrompt.toList()

      val hasHighPriorityMemorySinceLastPrompt = automaticShortTermMemoriesSinceLastPrompt.find {
         it.isHighPriority
      } != null

      val timeSincePrompt = getCurrentUnixTimeSeconds() - this.previousPromptDoneUnixTime

      val hasCompletedScript = this.mostRecentSentScriptId == null ||
              this.mostRecentSentScriptId == this.mostRecentSyncInput.mostRecentCompletedScriptId

      val newPromptTimeLimit = if (this.shouldUseMockResponses) {
         10.0
      } else if (hasHighPriorityMemorySinceLastPrompt) {
         15.0
      } else if (hasCompletedScript) {
         20.0
      } else {
         60.0
      }

      val shouldPrompt = timeSincePrompt > newPromptTimeLimit

      println("hasCompletedScript: $hasCompletedScript")

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
      val observationRadius = selfInfo.observationRadius

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
            You will only be able observe entities within $observationRadius ${gameConstants.distanceUnit} from your current location.
            If an entity disappears, it may be because they moved outside your observation radius.
            Therefor, you should consider using the recordThought function to remember where important entities are.
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
         if (this.memoryState.shortTermMemory.isNotBlank()) {
            it.addLine("## YOUR MEMORY")
            it.addLine(this.memoryState.shortTermMemory)
            it.addLine("")
         }
      }

      // jshmrsn: Add sections in order first, so we can add critical tokens before adding less critical tokens if they fit
      val recentActivitySection = builder.addSection("recentActivity")
      val newActivitySection = builder.addSection("newActivity")

      builder.addSection("codeBlockStartSection").also {
         it.addLine("The following TypeScript defines the interfaces/API you can use to interact with the world.")
         it.addLine("```ts")
      }

      builder.addSection("interfaces").also {
         val interfacesSource = input.agentTypeScriptInterfaceString
         it.addLine(interfacesSource)
      }

      builder.addSection("worldAsCodeSection").also {
         it.addLine("")
         it.addLine("// The code below represents the known world state according to your observations.")
         it.addLine("// Note that some data is summarized in comments to reduce tokens, so you might need to use the API to query for the full state.")
      }

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
         reserveTokens = if (modelInfo == ModelInfo.gpt_3_5_turbo) {
            850
         } else {
            (modelInfo.maxTokenCount * 0.333).roundToInt()
         }
      )

//      val nonUniqueEntityListSection = builder.addSection(
//         "nonUniqueEntityListSection",
//         reserveTokens = 300
//      )

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

      val inventoryListSection =
         builder.addSection("inventoryListSection", reserveTokens = (modelInfo.maxTokenCount * 0.0875).roundToInt())
      val inventorySummarySection =
         builder.addSection("inventorySummarySection", reserveTokens = (modelInfo.maxTokenCount * 0.0375).roundToInt())

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

      finalInstructionsSection.addLine("First, write 1-3 sentences in English what you would like to do and achieve next, and how you would like to interact socially.")
      finalInstructionsSection.addLine("After your English description of your desires and intentions, then write a block of JavaScript using the provided TypeScript interfaces to best carry out your intentions.")
      finalInstructionsSection.addLine("Surround your code block with markdown tags.")
      finalInstructionsSection.addLine("Write your code as top-level statements.")
      finalInstructionsSection.addLine("Your output JavaScript should be about 1-15 lines long.")
      finalInstructionsSection.addLine("Your output can perform multiple steps to achieve a more complex compound action.")
      finalInstructionsSection.addLine("It is best to achieve as many useful actions per script as possible, so you might want to use loops to repeat repetitive tasks.")
      finalInstructionsSection.addLine("When you call functions to perform actions, those actions will complete before the function returns, so you can safely call multiple action functions without manually waiting for actions to complete.")
      finalInstructionsSection.addLine("Other people cannot see your code or comments in your code. If you want to express something to other people, you need to use the speak function.")
      finalInstructionsSection.addLine("You will not remember your code or your comments in your next prompt. If you want to remember something, use the recordThought function.")

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

      run {
         val newDebugInfoLines = mutableListOf<String>()
         newDebugInfoLines.add("### Prompt ID: $promptId")
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

         this.addPendingOutput(
            AgentSyncOutput(
               debugInfoByKey = mapOf(
                  "Prompt Run Info" to newDebugInfoLines.joinToString("\n"),
                  "Pending New Activity" to "<reset after prompt send>"
               )
            )
         )
      }

      this.memoryState.automaticShortTermMemoriesSinceLastPrompt.clear()

      this.previousPromptSendSimulationTime = simulationTimeForStep


      val promptResult = if (this.shouldUseMockResponses) {
         fun getResponseTextOrNull(): String? {
            val resourceName = "mock-responses/response-${this.nextMockResponseIndex}.md"
            return this::class.java.getResource("/$resourceName")?.readText()
         }

         val initialResourceResult = getResponseTextOrNull()

         val mockResponseText = if (initialResourceResult != null) {
            initialResourceResult
         } else {
            this.nextMockResponseIndex = 1
            getResponseTextOrNull() ?: ""
         }

         ++this.nextMockResponseIndex

         RunPromptResult.Success(
            responseText = mockResponseText,
            usage = PromptUsage(
               promptTokens = 0,
               completionTokens = 0,
               totalTokens = 0
            )
         )
      } else {
         runPrompt(
            languageModelService = this.context.languageModelService,
            modelInfo = modelInfo,
            promptBuilder = builder,
            debugInfo = "${input.agentType} (step) (simulationId = ${simulationId.value}, agentId = ${this.agentId.value}, syncId = $syncId, promptId = $promptId)",
            completionPrefix = completionPrefix,
            completionMaxTokens = completionMaxTokens,
            useFunctionCalling = false,
            temperature = 0.2
         )
      }

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


      val codeBlockStartIndex = responseText.indexOf("```")

      val beforeScriptText = if (codeBlockStartIndex >= 0) {
         responseText.substring(0, codeBlockStartIndex)
      } else {
         ""
      }

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

      val scriptId = buildShortRandomIdentifier()
      this.mostRecentSentScriptId = scriptId

      val newDebugInfoLines = mutableListOf<String>()

      newDebugInfoLines.add("### Before-Script Text")
      newDebugInfoLines.add(beforeScriptText)

      println("===== RESPONSE SCRIPT ($promptId) ====== \n$responseScript\n===============")

      this.addPendingOutput(
         AgentSyncOutput(
            scriptToRun = ScriptToRun(
               script = responseScript,
               scriptId = scriptId
            ),
            debugInfoByKey = mapOf(
               "Prompt Result Info" to newDebugInfoLines.joinToString("\n")
            ),
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
