package botfarmagent.game.agents.scripted

import botfarm.agentserver.*
import botfarmagent.game.Agent
import botfarmagent.game.AgentContext
import botfarmagent.game.agents.common.*
import botfarmagent.game.agents.common.UpdateMemoryResult
import botfarmagent.game.agents.common.updateMemory
import botfarmshared.engine.apidata.EntityId
import botfarmshared.engine.apidata.PromptUsageInfo
import botfarmshared.game.apidata.ActionResult
import botfarmshared.game.apidata.AgentStepResult
import botfarmshared.game.apidata.AgentSyncInputs
import botfarmshared.game.apidata.EntityInfo
import botfarmshared.misc.buildShortRandomString
import botfarmshared.misc.getCurrentUnixTimeSeconds
import kotlinx.coroutines.delay
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value
import kotlin.concurrent.thread
import kotlin.math.roundToInt


class ScriptedAgent(
   context: AgentContext,
   val useGpt4: Boolean,
   val useFunctionCalling: Boolean
) : Agent(context) {
   var previousPromptSendSimulationTime = -1000.0
   var previousPromptDoneUnixTime = -1000.0

   val memoryState = MemoryState()

   val receivedActionStartedIds = mutableSetOf<String>()
   val receivedActionResultById = mutableMapOf<String, ActionResult>()

   val javaScriptContext: Context =
      Context.newBuilder("js")
         .option("js.strict", "true")
         .build()

   private var activeScriptPromptId: String? = null
   private var activeScriptThread: Thread? = null
   private var mostRecentScript: String? = null

   val agentJavaScriptApi: AgentJavaScriptApi

   init {
      val initialInputs = this.initialInputs
      val simulationTime = initialInputs.simulationTime
      val selfInfo = initialInputs.selfInfo

      val javaScriptBindings = this.javaScriptContext.getBindings("js")
      val agentJavaScriptApi = AgentJavaScriptApi(this)
      this.agentJavaScriptApi = agentJavaScriptApi
      javaScriptBindings.putMember("api", agentJavaScriptApi)

      val sourceName = "helpers"

      run {
         val runtimeSource =
            ScriptedAgent::class.java.getResource("/scripted-agent-runtime.js")?.readText()
               ?: throw Exception("Scripted agent runtime JavaScript resource not found")

         val javaScriptSource = Source.newBuilder("js", runtimeSource, sourceName).build()
         this.javaScriptContext.eval(javaScriptSource)
      }

      val initialLongTermMemories = selfInfo.initialMemories.mapIndexed { initialMemoryIndex, initialMemory ->
         LongTermMemory(
            createdAtLocation = selfInfo.entityInfo.location,
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
         )
      }

      this.memoryState.automaticShortTermMemories.addAll(initialAutomaticShortTermMemory)
      this.memoryState.automaticShortTermMemoriesSinceLastPrompt.addAll(initialAutomaticShortTermMemory)

      this.memoryState.longTermMemories.addAll(initialLongTermMemories)
   }

   override fun consumeInputs(inputs: AgentSyncInputs) {
      val newObservations = inputs.newObservations

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

            newObservations.selfSpokenMessages.forEach { selfSpokenMessage ->
               newAutomaticShortTermMemories.add(
                  AutomaticShortTermMemory(
                     time = selfSpokenMessage.time,
                     summary = "I said \"${selfSpokenMessage.message}\" (while standing at ${selfSpokenMessage.location.asJsonArrayRounded})",
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
                     summary = "I took the action '${record.actionId}' on entity '${record.targetEntityId}'" + buildReasonSuffix(
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
      inputs: AgentSyncInputs
   ) {
      val simulationTimeForStep = inputs.simulationTime
      val bindingsToAdd = mutableListOf<Pair<String, (conversionContext: JsConversionContext) -> Any>>()

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
            openAI = this.context.openAI,
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

      val automaticShortTermMemoriesSinceLastPrompt = this.memoryState.automaticShortTermMemoriesSinceLastPrompt

      val hasHighPriorityMemorySinceLastPrompt = automaticShortTermMemoriesSinceLastPrompt.find {
         it.isHighPriority
      } != null

      val timeSincePrompt = getCurrentUnixTimeSeconds() - this.previousPromptDoneUnixTime

      val newPromptTimeLimit = if (hasHighPriorityMemorySinceLastPrompt) {
         8.0
      } else if (this.activeScriptPromptId == null) {
         15.0
      } else {
         60.0
      }

      val shouldPrompt = timeSincePrompt > newPromptTimeLimit

      if (!shouldPrompt) {
         println("Not prompting yet (${timeSincePrompt.roundToInt()} / $newPromptTimeLimit seconds) ($agentId)")
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
            You are an AI agent in control of a character in a virtual world.
            Your role is to be a smart problem solver and make progress in the game.
            You will be given a representation of the world as a block of TypeScript code.
            You will respond with a block of JavaScript code that uses the interfaces and objects provided by the Typescript representation of world, in order to interact with the world, carry out your intentions, and express yourself. 
            
            As you take actions, the simulation will automatically change values dynamically. Your code should not try to directly modify the values of entities or items.
            
            Do not write any comments in your javascript code. Only respond with the block of JavaScript code, don't explain it. Write your code as top-level statements. Surround your code block with markdown tags.

            If you have a thought, observation, or reflection you'd like to remember for later, use the recordThought function to remember it.
            If you don't use the recordThought function, you will forget it.
            
            Other people you meet in this world may or may not be acting your interest. Act in accordance to your own values and experiences.
            Limit your knowledge to things that you've learned while living in this world, don't talk about the outside world that you've been trained on.
            Do not make things up to seem more believable. If someone asks you something you don't know, then just say you don't know.
            This prompt will be executed every few seconds, so you will have other opportunities to act and think in the future.
         """.trimIndent()
         )
         it.addLine("")
      }

      val worldWidth = 3500
      val worldHeight = 3500

      builder.addSection("tips") {
         it.addLine("## TIPS")
         it.addText(
            """
            All time units will be in seconds, all locations will be [x,y] values in ${inputs.distanceUnit}.
            If other people have said something since the last time you spoke, or if you meet someone new, you will often want to say something.
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

      builder.addSection("corePersonality") {
         it.addLine("## YOUR CORE PERSONALITY")
         it.addLine(selfInfo.corePersonality)
         it.addLine("")
      }


      builder.addSection("shortTermMemory") {
         it.addLine("## YOUR_ACTIVE_MEMORY")
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
      val interfacesSource = ScriptedAgent::class.java.getResource("/scripted-agent-interfaces.ts")?.readText()
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

      val completionPrefix = ""

      newActivitySection.also {
         val headerDidFit =
            it.addLine("## NEW_OBSERVED_ACTIVITY (YOU SHOULD CONSIDER REACTING TO THIS)", optional = true).didFit

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
            val headerDidFit = it.addLine(
               "## PREVIOUS_OBSERVED_ACTIVITY (YOU MAY HAVE ALREADY REACTED TO THESE)",
               optional = true
            ).didFit

            if (headerDidFit) {
               for (entry in previousActivity) {
                  val secondsAgo = (inputs.simulationTime - entry.time).roundToInt()
                  it.addLine(entry.summary.replace("\n", " ") + " ($secondsAgo seconds ago)", optional = true)
               }

               it.addLine("", optional = true)
            }
         }
      }


      craftingRecipesSection.also {
         for (craftingRecipe in inputs.craftingRecipes) {
            val jsCraftingRecipe = this.agentJavaScriptApi.buildJsCraftingRecipe(
               craftingRecipe = craftingRecipe
            )

            val variableName = "crafting_recipe_${craftingRecipe.itemConfigKey.replace("-", "_")}"

            val serializedAsCode = SerializationAsJavaScriptCode.serialize(jsCraftingRecipe)

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

            bindingsToAdd.add(variableName to {
               this.agentJavaScriptApi.buildJsCraftingRecipe(
                  craftingRecipe = craftingRecipe,
                  jsConversionContext = it
               )
            })
         }
      }

      inventoryListSection.also {
         val inventory = selfInfo.inventoryInfo

         if (inventory.itemStacks.isEmpty()) {
            it.addLine("// You have no inventory items")
         } else {
            it.addLine("// Your inventory item stacks")

            for ((stackIndex, itemStack) in inventory.itemStacks.withIndex()) {
               val jsInventoryItemStackInfo = this.agentJavaScriptApi.buildJsInventoryItemStackInfo(
                  itemStackInfo = itemStack,
                  stackIndex = stackIndex
               )

               val itemStackVariableName = "inventory_item_${stackIndex}"
               val serializedAsJavaScript = SerializationAsJavaScriptCode.serialize(jsInventoryItemStackInfo)

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

               bindingsToAdd.add(itemStackVariableName to {
                  jsInventoryItemStackInfo
               })
            }
         }
      }


      val selfJsEntity = this.agentJavaScriptApi.buildJsEntity(selfInfo.entityInfo)

      selfSection.addLine("const self: Entity = ${SerializationAsJavaScriptCode.serialize(selfJsEntity)}")

      bindingsToAdd.add("self" to {
         selfJsEntity
      })

      val groupedSortedEntities = getGroupedSortedObservedEntities(inputs, selfInfo)

      uniqueEntityListSection.addLine("// Nearby entities in the world:")
      var nextEntityIndex = 0
      val addedEntityIds = mutableSetOf<EntityId>()

      fun addEntitiesFromList(
         section: PromptBuilder,
         entities: List<EntityInfo>
      ) {
         for (entityInfo in entities) {
            val jsEntity = this.agentJavaScriptApi.buildJsEntity(entityInfo)

            val variableTypeName = if (entityInfo.characterInfo != null) {
               "character"
            } else if (entityInfo.itemInfo != null) {
               entityInfo.itemInfo.itemConfigKey.replace("-", "_")
            } else {
               ""
            }

            val entityVariableName = "${variableTypeName}_entity_${nextEntityIndex}"
            val serializedAsJavaScript = SerializationAsJavaScriptCode.serialize(jsEntity)

            val entityAsCode = "const $entityVariableName: Entity = $serializedAsJavaScript"

            val addLineResult = section.addLine(
               entityAsCode,
               optional = true
            ).didFit

            if (!addLineResult) {
               break
            }

            addedEntityIds.add(entityInfo.entityId)

            bindingsToAdd.add(entityVariableName to {
               jsEntity
            })

            ++nextEntityIndex
         }
      }

      addEntitiesFromList(
         section = uniqueEntityListSection,
         entities = groupedSortedEntities.uniqueEntities
      )

      addEntitiesFromList(
         section = nonUniqueEntityListSection,
         entities = groupedSortedEntities.nonUniqueEntities
      )

      val remainingEntities = (groupedSortedEntities.uniqueEntities + groupedSortedEntities.nonUniqueEntities).filter {
         it.entityId !in addedEntityIds
      }

      if (remainingEntities.isNotEmpty()) {
         omittedEntityIntroSection.addLine("// NOTE: ${remainingEntities.size} entities were omitted from the above list to reduce prompt size.")
         omittedEntityIntroSection.addLine("// You can use getCurrentNearbyEntities() in your response script to get the full list")

         omittedEntityIntroSection.addLine("//   Summary of omitted entities:")

         val characterEntities = remainingEntities.filter { it.characterInfo != null }

         if (characterEntities.isNotEmpty()) {
            omittedEntitySummarySection.addLine(
               "//     ${characterEntities.size} were other character entities",
               optional = true
            )
         }

         val itemInfos = remainingEntities.mapNotNull { it.itemInfo }

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
      val promptId = buildShortRandomString()


      val responseText = if (responseOverride != null) {
         responseOverride
      } else {
         val promptResult = runPrompt(
            openAI = this.context.openAI,
            modelInfo = modelInfo,
            promptBuilder = builder,
            functionName = AgentResponseSchema_v1.functionName,
            functionSchema = AgentResponseSchema_v1.functionSchema,
            functionDescription = null,
            debugInfo = "${inputs.agentType} (step) ($simulationId, $agentId, syncId = $syncId, promptId = $promptId)",
            completionPrefix = completionPrefix,
            completionMaxTokens = completionMaxTokens,
            useFunctionCalling = this.useFunctionCalling
         )

         when (promptResult) {
            is RunPromptResult.Success -> {
               promptUsages.add(buildPromptUsageInfo(promptResult.usage, modelInfo))
            }

            is RunPromptResult.RateLimitError -> {
               return this.addPendingResult(
                  AgentStepResult(
                     error = "Rate limit error running agent prompt (errorId = ${promptResult.errorId})",
                     wasRateLimited = true,
                     promptUsages = promptUsages
                  )
               )
            }

            is RunPromptResult.ConnectionError -> {
               return this.addPendingResult(
                  AgentStepResult(
                     error = "Connection error running agent prompt (errorId = ${promptResult.errorId})",
                     wasRateLimited = true,
                     promptUsages = promptUsages
                  )
               )
            }

            is RunPromptResult.UnknownApiError -> {
               return this.addPendingResult(
                  AgentStepResult(
                     error = "Unknown prompt API error (errorId = ${promptResult.errorId}): " + promptResult::class.simpleName,
                     promptUsages = promptUsages
                  )
               )
            }

            is RunPromptResult.LengthLimit -> {
               promptUsages.add(buildPromptUsageInfo(promptResult.usage, modelInfo))

               return this.addPendingResult(
                  AgentStepResult(
                     error = "Prompt exceeded max length (errorId = ${promptResult.errorId})",
                     promptUsages = promptUsages
                  )
               )
            }
         }

         promptResult.responseText
      }


      this.previousPromptDoneUnixTime = getCurrentUnixTimeSeconds()
      this.previousPromptSendSimulationTime = simulationTimeForStep

      this.memoryState.automaticShortTermMemoriesSinceLastPrompt.clear()

      val codeBlockStartIndex = responseText.indexOf("```")
      val responseScript = if (codeBlockStartIndex >= 0) {
         val newlineIndex = responseText.indexOf("\n")
         val textAfterBlockStart = responseText.substring(newlineIndex + 1)
         val blockEndIndex = textAfterBlockStart.indexOf("```")

         if (blockEndIndex < 0) {
            throw Exception("Script block end not found:\n$textAfterBlockStart")
         } else {
            textAfterBlockStart.substring(0, blockEndIndex)
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

      this.addPendingResult(
         AgentStepResult(
            actions = null,
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

      println("responseScript: $responseScript")

      val wrappedResponseScript = """
         (function() {
         $responseScript
         })()
      """.trimIndent()

      val previousThread = this.activeScriptThread

      this.agentJavaScriptApi.shouldEndScript = true

      if (previousThread != null) {
         if (previousThread.isAlive) {
            println("Agent script thread exists and is active, so attempting to end now ($promptId)")

            val waitStartTime = getCurrentUnixTimeSeconds()
            val timeout = 5.0

            while (true) {
               val waitedTime = getCurrentUnixTimeSeconds() - waitStartTime
               if (!previousThread.isAlive) {
                  println("Agent script thread has now ended after waiting $waitedTime seconds ($promptId)")
                  break
               } else if (waitedTime > timeout) {
                  println("Agent script thread did not end after $timeout seconds, so interrupting ($promptId)")
                  previousThread.interrupt()
                  break
               } else {
                  delay(200)
               }
            }

            delay(200)
         } else {
            println("Agent script thread exists, but is not active, so not interrupting ($promptId)")
         }
      }

      synchronized(this) {
         this.activeScriptPromptId = promptId
         this.mostRecentScript = wrappedResponseScript
         this.agentJavaScriptApi.shouldEndScript = false


         this.addPendingResult(
            AgentStepResult(
               agentStatus = "running-script",
               statusStartUnixTime = getCurrentUnixTimeSeconds(),
               statusDuration = null
            )
         )

         this.activeScriptThread = thread {
            val bindings = this.javaScriptContext.getBindings("js")

            val jsConversionContext = JsConversionContext(bindings)

            bindingsToAdd.forEach {
               bindings.putMember(it.first, it.second(jsConversionContext))
            }

            val sourceName = "agent"
            val javaScriptSource = Source.newBuilder("js", wrappedResponseScript, sourceName).build()

            try {
               this.javaScriptContext.eval(javaScriptSource)
               println("Agent script thread complete: $promptId")
               synchronized(this) {
                  this.addPendingResult(
                     AgentStepResult(
                        agentStatus = "script-done",
                        statusStartUnixTime = getCurrentUnixTimeSeconds(),
                        statusDuration = null
                     )
                  )

                  if (this.activeScriptPromptId == promptId) {
                     this.activeScriptPromptId = null
                  }
               }
            } catch (unwindScriptThreadThrowable: UnwindScriptThreadThrowable) {
               println("Got unwind agent script thread throwable ($promptId): ${unwindScriptThreadThrowable.reason}")
               synchronized(this) {
                  if (this.activeScriptPromptId == promptId) {
                     this.activeScriptPromptId = null
                  }
               }
            } catch (exception: Exception) {
               println("Exception evaluating agent JavaScript ($promptId): " + exception.stackTraceToString() + "\nAgent script was: $wrappedResponseScript")
               synchronized(this) {
                  this.addPendingResult(
                     AgentStepResult(
                        error = "Exception evaluating JavaScript ($promptId)",
                        agentStatus = "script-exception"
                     )
                  )
               }
            }
         }
      }
   }

   fun recordThought(thought: String) {
      val newLongTermMemory = LongTermMemory(
         createdTime = this.mostRecentInputs.simulationTime,
         content = thought,
         id = this.memoryState.longTermMemories.size + 1,
         createdAtLocation = this.mostRecentInputs.selfInfo.entityInfo.location,
         importance = 0
      )

      this.memoryState.longTermMemories.add(newLongTermMemory)

      this.memoryState.automaticShortTermMemories.add(
         AutomaticShortTermMemory(
            time = this.mostRecentInputs.simulationTime,
            summary = "I had the thought: " + thought
         )
      )
   }
}

val responseOverride: String? = """
   const woodNeeded = crafting_recipe_house.costEntries.find(entry => entry.itemTypeId === "wood").amount;
   const stoneNeeded = crafting_recipe_house.costEntries.find(entry => entry.itemTypeId === "stone").amount;
   
   const currentWood = getTotalInventoryAmountForItemTypeId("wood");
   const currentStone = getTotalInventoryAmountForItemTypeId("stone");
   if (currentWood < woodNeeded || currentStone < stoneNeeded) {
     // We still need more resources. Let's continue gathering.
     // First, let's check if there are any items on the ground that we can pick up.
     const nearbyEntities = getCurrentNearbyEntities();
     const nearbyWood = nearbyEntities.find(entity => entity.itemOnGround && entity.itemOnGround.itemTypeId === "wood");
     const nearbyStone = nearbyEntities.find(entity => entity.itemOnGround && entity.itemOnGround.itemTypeId === "stone");
     if (nearbyWood) {
       // There's wood on the ground. Let's pick it up.
       walkTo(nearbyWood.location);
       nearbyWood.itemOnGround.pickup();
     } else if (nearbyStone) {
       // There's stone on the ground. Let's pick it up.
       walkTo(nearbyStone.location);
       nearbyStone.itemOnGround.pickup();
     } else {
       // There's no wood or stone on the ground. Let's find a tree or boulder to harvest.
       const nearbyTree = nearbyEntities.find(entity => entity.itemOnGround && entity.itemOnGround.itemTypeId === "tree");
       const nearbyBoulder = nearbyEntities.find(entity => entity.itemOnGround && entity.itemOnGround.itemTypeId === "boulder");
       if (nearbyTree) {
         // There's a tree nearby. Let's equip our axe and cut it down.
         const axe = getCurrentInventoryItemStacks().find(item => item.itemTypeId === "axe");
         axe.equip();
         nearbyTree.damageable.attackWithEquippedItem();
       } else if (nearbyBoulder) {
         // There's a boulder nearby. Let's equip our pickaxe and break it apart.
         const pickaxe = getCurrentInventoryItemStacks().find(item => item.itemTypeId === "pickaxe");
         pickaxe.equip();
         nearbyBoulder.damageable.attackWithEquippedItem();
       }
     }
   } else {
     // We have enough resources to craft the house. Let's do it.
     crafting_recipe_house.craft();
     speak("Player, we have gathered enough resources. I'm going to start crafting the house now.");
   }

""".trimIndent()
//   """
//   const nearbyEntities = getCurrentNearbyEntities();
//   const nearbyTrees = nearbyEntities.filter(entity => entity.itemOnGround && entity.itemOnGround.itemTypeId === 'tree');
//
//   if (nearbyTrees.length > 0) {
//     // If there are trees nearby, walk to the closest one to chop it down
//     const closestTree = nearbyTrees.reduce((prev, curr) =>
//       self.location.distanceTo(prev.location) < self.location.distanceTo(curr.location) ? prev : curr
//     );
//     walkTo(closestTree.location, "foo");
//   } else {
//     // If there are no trees nearby, walk to a random location
//     const randomX = Math.random() * 3500;
//     const randomY = Math.random() * 3500;
//     walkTo(vector2(randomX, randomY), "foo");
//   }
//""".trimIndent()
//"""
//   recordThought("Player wants to build a house. I should check if I have enough resources for that.");
//   const houseRecipe = crafting_recipe_house;
//   const canAffordHouse = houseRecipe.canCurrentlyAfford;
//
//   if (canAffordHouse) {
//     houseRecipe.craft("Building a house as suggested by Player.");
//   } else {
//     speak("I'm sorry, Player. I don't have enough resources to build a house right now. I'll continue gathering more.");
//   }
//""".trimIndent()
//"""
//         // Check if I can afford to craft a house
//         const craftingRecipes = getAllCraftingRecipes();
//         const houseRecipe = craftingRecipes.find(recipe => recipe.itemTypeId === "house");
//
//         if (houseRecipe && houseRecipe.canCurrentlyAfford) {
//           // Craft a house
//           houseRecipe.craft("I have enough resources to build a house.");
//         } else {
//           // If I can't afford a house, I'll continue gathering resources
//           const nearbyEntities = getCurrentNearbyEntities();
//           const boulder = nearbyEntities.find(entity => entity.itemOnGround && entity.itemOnGround.itemTypeId === "boulder");
//           const tree = nearbyEntities.find(entity => entity.itemOnGround && entity.itemOnGround.itemTypeId === "tree");
//
//           // Check if I have a pickaxe and an axe in my inventory
//           const inventoryItems = getCurrentInventoryItemStacks();
//           const pickaxe = inventoryItems.find(item => item.itemTypeId === "pickaxe");
//           const axe = inventoryItems.find(item => item.itemTypeId === "axe");
//
//           if (boulder && pickaxe) {
//             // Equip the pickaxe and interact with the boulder
//             pickaxe.equip("I need to equip this pickaxe to break the boulder.");
//             boulder.damageable.attackWithEquippedItem("I'm breaking this boulder with my pickaxe.");
//           } else if (tree && axe) {
//             // Equip the axe and interact with the tree
//             axe.equip("I need to equip this axe to cut down the tree.");
//             tree.damageable.attackWithEquippedItem("I'm cutting down this tree with my axe.");
//           } else {
//             // If there are no boulders or trees nearby, I'll walk to a random location
//             const randomX = Math.floor(Math.random() * 3500);
//             const randomY = Math.floor(Math.random() * 3500);
//             walkTo(vector2(randomX, randomY), "I'm walking to a new location to find more resources.");
//           }
//         }
//      """.trimIndent()