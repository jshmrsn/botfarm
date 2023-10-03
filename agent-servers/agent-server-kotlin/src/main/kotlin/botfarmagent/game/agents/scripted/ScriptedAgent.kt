package botfarmagent.game.agents.scripted

import botfarm.agentserver.*
import botfarmagent.game.Agent
import botfarmagent.game.AgentContext
import botfarmagent.game.agents.common.*
import botfarmagent.game.agents.default.UpdateMemoryResult
import botfarmagent.game.agents.default.updateMemory
import botfarmshared.engine.apidata.PromptUsageInfo
import botfarmshared.game.apidata.AgentStepResult
import botfarmshared.game.apidata.AgentSyncInputs
import botfarmshared.misc.buildShortRandomString
import botfarmshared.misc.getCurrentUnixTimeSeconds
import kotlinx.serialization.json.*
import org.graalvm.polyglot.Context
import kotlin.math.roundToInt
import org.graalvm.polyglot.*


class ScriptedAgent(
   context: AgentContext,
   val useGpt4: Boolean,
   val useFunctionCalling: Boolean
) : Agent(context) {
   var previousPromptSendSimulationTime = -1000.0
   var previousPromptDoneUnixTime = -1000.0

   val memoryState = MemoryState()

   val javaScriptContext: Context =
      Context.newBuilder("js")
         .option("js.strict", "true")
         .build()

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
         val helpersSource =
            ScriptedAgent::class.java.getResource("/scripted-agent-runtime.js")?.readText()
               ?: throw Exception("Scripted agent runtime JavaScript resource not found")

         val javaScriptSource = Source.newBuilder("js", helpersSource, sourceName).build()
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
      val simulationTimeForStep = inputs.simulationTime
      val bindings = this.javaScriptContext.getBindings("js")

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
            You are an AI agent in control of a character in a virtual world.
            Your role is to be a smart problem solver and make progress in the game.
            You will be given a representation of the world as a block of TypeScript code.
            You will respond with a block of JavaScript code that uses the interfaces and objects provided by the Typescript representation of world, in order to interact with the world, carry out your intentions, and express yourself. 
            
            As you take actions, the simulation will automatically change values dynamically. Your code should not try to directly modify the values of entities or items.
            
            Do not write any comments in your javascript code. Only respond with the block of JavaScript code, don't explain it. Write your code as top-level statements. Surround your code block with markdown tags.

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

      val typesSection = builder.addSection("types")
      val entityListSection = builder.addSection(
         "entityListSection",
         reserveTokens = 2500
      )

      val afterEntityListSection = builder.addSection("afterEntityListSection")

      val inventoryListSection = builder.addSection("inventoryListSection")
      val selfSection = builder.addSection("selfSection")

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
               val serializedAsJavaScript = JavaScriptSerialization.serialize(jsInventoryItemStackInfo)

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

               bindings.putMember(itemStackVariableName, jsInventoryItemStackInfo)
            }

            it.addLine("")
         }
      }

      val interfacesSource = ScriptedAgent::class.java.getResource("/scripted-agent-interfaces.ts")?.readText()
         ?: throw Exception("got null from scripted-agent-interfaces.ts")

      typesSection.addLine(interfacesSource)

      val selfJsEntity = this.agentJavaScriptApi.buildJsEntity(selfInfo.entityInfo)
      selfSection.addLine("// Your own entity state")
      selfSection.addLine("const self: Entity = ${JavaScriptSerialization.serialize(selfJsEntity)}")

      bindings.putMember("self", selfJsEntity)

      val sortedEntities = getSortedObservedEntities(inputs, selfInfo)

      entityListSection.addLine("// Entities in the world")
      var nextEntityIndex = 0
      for (entityInfo in sortedEntities) {
         val jsEntity = this.agentJavaScriptApi.buildJsEntity(entityInfo)

         val entityVariableName = "entity_${entityInfo.entityId.value}"
         val serializedAsJavaScript = JavaScriptSerialization.serialize(jsEntity)

         val entityAsCode = "const $entityVariableName: Entity = $serializedAsJavaScript"

         val addLineResult = entityListSection.addLine(
            entityAsCode,
            optional = true
         ).didFit

         if (!addLineResult) {
            println(
               "Entity did not fit (${nextEntityIndex + 1} / ${sortedEntities.size}): " + entityInfo.location.distance(
                  selfInfo.entityInfo.location
               )
            )
            break
         }

         bindings.putMember(entityVariableName, jsEntity)

         ++nextEntityIndex
      }

      entityListSection.addLine("", optional = true)

      val remainingEntities = sortedEntities.size - nextEntityIndex
      if (remainingEntities > 0) {
         afterEntityListSection.addLine("// NOTE: $remainingEntities entities were omitted from the above list to reduce prompt size, you can use getCurrentNearbyEntities() in your response to get the full list")
      }

      val text = builder.buildText()
      println("prompt: $text")

      val promptSendTime = getCurrentUnixTimeSeconds()

//      val responseScript = """
//         const result = getCurrentNearbyEntities()
//         const first = result[0]
//         console.log("first", result[0])
//         console.log("first location", result[0].location)
//         console.log("first location.x", result[0].location.x)
//         console.log("first location mag", result[0].location.getMagnitude())
//         console.log("first location min", result[0].location.plus(vector2(99999, 0)))
//         console.log("entity_D0D7FA4F print", entity_D0D7FA4F)
//
//         console.log("first json", JSON.stringify(first))
//      """.trimIndent()
//

      val promptId = buildShortRandomString()
      val promptResult = runPrompt(
         openAI = this.context.openAI,
         modelInfo = modelInfo,
         promptBuilder = builder,
         functionName = AgentResponseSchema_v1.functionName,
         functionSchema = AgentResponseSchema_v1.functionSchema,
         functionDescription = null,
         debugInfo = "${inputs.agentType} (step) (simulationId = $simulationId agentId = $agentId, syncId = $syncId, promptId = $promptId)",
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

      val responseText: String = promptResult.responseText

      this.previousPromptDoneUnixTime = getCurrentUnixTimeSeconds()
      this.previousPromptSendSimulationTime = simulationTimeForStep

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

      this.addPendingResult(
         AgentStepResult(
            actions = null,
            newDebugInfo = responseScript.lines()
               .joinToString("  \n"), // two spaces from https://github.com/remarkjs/react-markdown/issues/273
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

      val sourceName = "agent"
      val javaScriptSource = Source.newBuilder("js", wrappedResponseScript, sourceName).build()
      try {
         this.javaScriptContext.eval(javaScriptSource)
      } catch (exception: Exception) {
         println("Exception evaluating agent JavaScript: " + exception.stackTraceToString() + "\nAgent script was: $wrappedResponseScript")
      }
   }
}