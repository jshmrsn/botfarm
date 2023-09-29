package botfarmshared.misc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

@Serializable
sealed class JsonSchema {
   abstract val description: String?

   fun toJsonElement(): JsonElement {
      return Json.encodeToJsonElement(this)
   }
}

@Serializable
@SerialName("boolean")
class JsonBooleanSchema(
   override val description: String? = null,
) : JsonSchema()

@Serializable
@SerialName("number")
class JsonNumberSchema(
   override val description: String? = null,
) : JsonSchema()

@Serializable
@SerialName("string")
class JsonStringSchema(
   override val description: String? = null,
) : JsonSchema()

@Serializable
@SerialName("array")
class JsonArraySchema(
   override val description: String? = null,
   val items: JsonSchema
) : JsonSchema()

@Serializable
@SerialName("object")
class JsonObjectSchema(
   override val description: String? = null,
   val properties: Map<String, JsonSchema>,
   val required: List<String>? = null
) : JsonSchema()



