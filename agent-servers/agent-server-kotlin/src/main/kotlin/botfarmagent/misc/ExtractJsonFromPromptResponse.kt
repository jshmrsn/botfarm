package botfarmagent.misc

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class ExtractJsonResult(
   val textBeforeJson: String,
   val textAfterJson: String,
   val jsonData: JsonObject
)

fun extractJsonFromPromptResponse(
   messageContent: String,
   debugInfo: String
): ExtractJsonResult {
   val firstOpeningBraceIndex = messageContent.indexOf('{')
   val lastClosingBraceIndex = messageContent.lastIndexOf('}')

   return if (firstOpeningBraceIndex >= 0 && lastClosingBraceIndex >= 0) {
      val jsonString = messageContent.substring(firstOpeningBraceIndex, lastClosingBraceIndex + 1)
      val textBeforeJson = messageContent.substring(0, firstOpeningBraceIndex)
      val textAfterJson = if (lastClosingBraceIndex < messageContent.length - 1) {
         messageContent.substring(lastClosingBraceIndex + 1, messageContent.length)
      } else {
         ""
      }

      ExtractJsonResult(
         textBeforeJson = textBeforeJson,
         jsonData = Json.parseToJsonElement(jsonString).jsonObject,
         textAfterJson = textAfterJson
      )
   } else {
      throw Exception("Can't find JSON braces in message output ($debugInfo)")
   }
}

