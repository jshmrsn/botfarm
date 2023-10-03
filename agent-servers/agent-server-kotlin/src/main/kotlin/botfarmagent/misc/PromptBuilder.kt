package botfarm.agentserver

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.EncodingRegistry
import com.knuddels.jtokkit.api.ModelType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

sealed class SectionOrEntry {
   class Entry(
      val tokenCount: Int,
      val text: String
   ) : SectionOrEntry()

   class Section(
      val promptBuilder: PromptBuilder
   ) : SectionOrEntry()
}

// jshmrsn: Re-using the same registry significantly sped up token counting over many inputs
val registry: EncodingRegistry = Encodings.newDefaultEncodingRegistry()

var totalTokenCountDurationMs = 0.0
var totalTokenCount = 0L

fun getApproximateTokenCountForText(
   modelType: ModelType,
   text: String
): Int {
   val startNanoTime = System.nanoTime()
   val encoding = registry.getEncodingForModel(modelType)

   val count = encoding.countTokens(text)

   totalTokenCount += count
   totalTokenCountDurationMs += (System.nanoTime() - startNanoTime) / 1000000.0

   return count
}

class PromptBuilder(
   val debugName: String = "default",
   val modelInfo: ModelInfo,
   val reservedOutputTokens: Int,
   val parentBuilder: PromptBuilder? = null,
   rootBuilder: PromptBuilder? = null,
   val reservedTokens: Int? = null
) {
   val rootBuilder = rootBuilder ?: this

   private val mutableEntries = mutableListOf<SectionOrEntry>()
   val entries = this.mutableEntries

   private var selfAndRecursiveChildrenTokenCount = 0
   val totalTokens: Int
      get() = this.selfAndRecursiveChildrenTokenCount

   fun getRecursiveReservedOrAllocatedTokens(): Int {
      if (this.reservedTokens != null) {
         return this.reservedTokens
      }

      return this.getRecursiveAllocatedTokens()
   }

   fun getRecursiveAllocatedTokens(): Int {
      return this.entries.sumOf { entry ->
         when (entry) {
            is SectionOrEntry.Section -> {
               entry.promptBuilder.getRecursiveReservedOrAllocatedTokens()
            }

            is SectionOrEntry.Entry -> {
               entry.tokenCount
            }
         }
      }
   }

   fun getRecursiveAvailableTokens(): Int {
      if (this.reservedTokens != null) {
         return this.reservedTokens - this.getRecursiveAllocatedTokens()
      }

      return if (this.parentBuilder != null) {
         this.parentBuilder.getRecursiveAvailableTokens()
      } else {
         this.modelInfo.maxTokenCount - this.reservedOutputTokens - this.getRecursiveAllocatedTokens()
      }
   }

   fun addSection(
      debugName: String,
      reserveTokens: Int? = null,
      useBuilder: (PromptBuilder) -> Unit = {}
   ): PromptBuilder {
      val availableTokens = this.getRecursiveAvailableTokens()

      if (reserveTokens != null) {
         if (reserveTokens > availableTokens) {
            throw Exception("Attempted to add section, but requested reserved tokens exceeds available tokens (requestedReservedTokens = $reserveTokens, availableTokens = $availableTokens)")
         }
      }

      val newSectionBuilder = PromptBuilder(
         debugName = debugName,
         modelInfo = this.modelInfo,
         reservedOutputTokens = 0,
         reservedTokens = reserveTokens,
         parentBuilder = this,
         rootBuilder = this.rootBuilder
      )

      this.entries.add(
         SectionOrEntry.Section(
            promptBuilder = newSectionBuilder
         )
      )

      useBuilder(newSectionBuilder)

      return newSectionBuilder
   }

   private fun buildTokenUsageSummary(indent: String, stringBuilder: StringBuilder): String {
      var selfTokenCount = 0
      this.entries.forEach {
         if (it is SectionOrEntry.Entry) {
            selfTokenCount += it.tokenCount
         }
      }

      val reservedSuffix = this.reservedTokens?.let {
         " (reserved $it)"
      } ?: ""

      stringBuilder.appendLine("${this.debugName}: $selfTokenCount$reservedSuffix")

      this.entries.forEach {
         if (it is SectionOrEntry.Section) {
            it.promptBuilder.buildTokenUsageSummary(
               indent = indent + "  ",
               stringBuilder = stringBuilder
            )
         }
      }

      return stringBuilder.toString()
   }

   fun buildTokenUsageSummary(): String {
      val stringBuilder = StringBuilder()

      stringBuilder.appendLine("TOTAL: ${this.selfAndRecursiveChildrenTokenCount}")
      this.buildTokenUsageSummary(
         indent = "  ",
         stringBuilder = stringBuilder
      )

      return stringBuilder.toString()
   }

   fun buildText(stringBuilder: StringBuilder) {
      for (entry in this.entries) {
         when (entry) {
            is SectionOrEntry.Section -> {
               entry.promptBuilder.buildText(stringBuilder)
            }

            is SectionOrEntry.Entry -> {
               stringBuilder.append(entry.text)
            }

            else -> throw Exception("Not exhaustive?")
         }
      }
   }

   fun buildText(): String {
      val stringBuilder = StringBuilder()

      this.buildText(stringBuilder)

      return stringBuilder.toString()
   }

   fun getApproximateTokenCountForText(
      text: String
   ) = getApproximateTokenCountForText(text = text, modelType = modelInfo.closestTikTokenModelType)

   class AddTextIfFitsResult(
      val didFit: Boolean,
      val previousTokenCount: Int,
      val addingTokenCount: Int,
      val reservedOutputTokens: Int,
      val previousAvailableTokens: Int,
      val newAvailableTokens: Int
   )

   private fun notifyRecursiveAddedTokens(addedTokenCount: Int) {
      this.selfAndRecursiveChildrenTokenCount += addedTokenCount
      this.parentBuilder?.notifyRecursiveAddedTokens(addedTokenCount)
   }

   fun addText(
      text: String,
      optional: Boolean = false
   ): AddTextIfFitsResult {
      val currentRootRecursiveTokenCount = this.rootBuilder.selfAndRecursiveChildrenTokenCount

      val previousAvailableTokens = this.getRecursiveAvailableTokens()
      val addingTokenCount = this.getApproximateTokenCountForText(text)

      val newAvailableTokens = previousAvailableTokens - addingTokenCount

      val fits = newAvailableTokens >= 0

      if (fits) {
         this.notifyRecursiveAddedTokens(addingTokenCount)

         this.mutableEntries.add(
            SectionOrEntry.Entry(
               text = text,
               tokenCount = addingTokenCount
            )
         )
      } else if (!optional) {
         val reservedOutputTokens = this.rootBuilder.reservedOutputTokens

         throw Exception(
            """Added text does not fit (${this.debugName}):
               currentRootRecursiveTokenCount = $currentRootRecursiveTokenCount
               currentSelfRecursiveTokenCount = ${this.selfAndRecursiveChildrenTokenCount}
               addingTokenCount = $addingTokenCount
               previousRemainingTokens = $previousAvailableTokens,
               newAvailableTokens = $newAvailableTokens
               reservedOutputTokens = $reservedOutputTokens
               modelInfo.maxTokenCount = ${this.modelInfo.maxTokenCount}
               Token usage summary:
               ${this.rootBuilder.buildTokenUsageSummary()}
               Text to add:
               $text
               Previous text:
               ${this.buildText()}
            """.trimIndent()
         )
      }

      return AddTextIfFitsResult(
         didFit = fits,
         previousTokenCount = currentRootRecursiveTokenCount,
         addingTokenCount = addingTokenCount,
         reservedOutputTokens = reservedOutputTokens,
         previousAvailableTokens = previousAvailableTokens,
         newAvailableTokens = newAvailableTokens
      )
   }

   fun addLine(
      text: String,
      optional: Boolean = false
   ): AddTextIfFitsResult {
      return this.addText(
         text = text + "\n",
         optional = optional
      )
   }

   fun addValue(
      label: String,
      value: Any,
      optional: Boolean = false
   ) {
      this.addLine(
         text = "$label: $value",
         optional = optional
      )
   }

   fun addJsonLine(
      value: JsonElement,
      useBlockMarkdown: Boolean = false,
      optional: Boolean = false
   ): AddTextIfFitsResult {
      val jsonString = Json.encodeToString(value)
      return if (useBlockMarkdown) {
         this.addLine("```json\n$jsonString\n```", optional = optional)
      } else {
         this.addLine(jsonString, optional = optional)
      }
   }

   fun addJsonLine(
      prefix: String,
      value: JsonElement,
      useBlockMarkdown: Boolean = false,
      optional: Boolean = false
   ): AddTextIfFitsResult {
      val jsonString = Json.encodeToString(value)
      return if (useBlockMarkdown) {
         this.addLine("$prefix\n```json$jsonString\n```", optional = optional)
      } else {
         this.addLine("$prefix\n$jsonString", optional = optional)
      }
   }
}
