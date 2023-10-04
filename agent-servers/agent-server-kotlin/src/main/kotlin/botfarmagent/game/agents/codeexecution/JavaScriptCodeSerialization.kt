package botfarmagent.game.agents.codeexecution

import botfarmagent.game.agents.codeexecution.jsdata.JsArray
import botfarmshared.misc.DynamicSerialization
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.graalvm.polyglot.HostAccess
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaField

// jshmrsn: The purpose of this class is to generate JavaScript code that represents an
// instance of a class that was already designed to be used with the JavaScript runtime
// using Polyglot (specifically, it by default  excluded fields unless they have the HostAccess.Export annotation).
// The code representation will not include quotes around object keys.
object JavaScriptCodeSerialization {
   inline fun <reified T> serialize(
      value: T?,
      baseClassTypeArguments: List<KClass<*>> = emptyList(),
      json: Json = Json,
      requireExplicitHostAccess: Boolean = true
   ): String {
      val builder = StringBuilder()

      serialize(
         json = json,
         baseClass = T::class,
         baseClassTypeArguments = baseClassTypeArguments,
         value = value,
         builder = builder,
         indent = "",
         requireExplicitHostAccess = requireExplicitHostAccess
      )

      return builder.toString()
   }

   inline fun <reified T> serialize(
      value: T?,
      baseClassTypeArguments: List<KClass<*>> = emptyList(),
      json: Json = Json,
      builder: StringBuilder,
      indent: String,
      requireExplicitHostAccess: Boolean
   ) {
      serialize(
         json = json,
         baseClass = T::class,
         baseClassTypeArguments = baseClassTypeArguments,
         value = value,
         indent = indent,
         builder = builder,
         requireExplicitHostAccess = requireExplicitHostAccess
      )
   }

   fun serialize(
      value: Any?,
      baseClass: KClass<*>? = null,
      baseClassTypeArguments: List<KClass<*>> = emptyList(),
      json: Json = Json,
      builder: StringBuilder,
      indent: String,
      requireExplicitHostAccess: Boolean
   ) {
      if (value == null) {
         builder.append("null")
         return
      }

      val newClass = value::class

      val resolvedValue = if (value is JsArray<*>) {
         value.values
      } else {
         DynamicSerialization.resolvePossibleInlineValueClassInstance(value = value, valueClass = newClass)
      }

      if (resolvedValue == null) {
         builder.append("null")
         return
      }

      if (resolvedValue is JsonElement) {
         builder.append(json.encodeToString(resolvedValue))
      } else if (resolvedValue is Enum<*>) {
         builder.append("\"${resolvedValue.name}\"")
      } else if (resolvedValue is Number) {
         builder.append(resolvedValue.toString())
      } else if (resolvedValue is String) {
         builder.append("\"${resolvedValue}\"")
      } else if (resolvedValue is Boolean) {
         if (resolvedValue) {
            builder.append("true")
         } else {
            builder.append("false")
         }
      } else if (resolvedValue is List<*>) {
         @Suppress("UNCHECKED_CAST")
         val list = resolvedValue as List<Any>

         if (list.isEmpty()) {
            builder.append("[]")
         } else {
            builder.append("[\n")
            list.forEachIndexed { index, containedValue ->
               builder.append(indent + "  ")

               serialize(
                  json = json,
                  baseClass = baseClassTypeArguments.getOrNull(0),
                  baseClassTypeArguments = emptyList(),
                  value = containedValue,
                  builder = builder,
                  indent = "$indent  ",
                  requireExplicitHostAccess = requireExplicitHostAccess
               )

               if (index == list.size - 1) {
                  builder.append("\n$indent]")
               } else {
                  builder.append(",\n")
               }
            }
         }
      } else if (resolvedValue is Map<*, *>) {
         @Suppress("UNCHECKED_CAST")
         val map = resolvedValue as Map<Any, Any>

         if (map.isEmpty()) {
            builder.append("{}")
         } else {
            builder.append("{\n")
            val entries = map.entries
            entries.forEachIndexed { index, entry ->
               val key = entry.key
               builder.append(indent + "  ")

               if (key is String) {
                  builder.append(key)
               } else {
                  serialize(
                     json = json,
                     baseClass = baseClassTypeArguments.getOrNull(0),
                     baseClassTypeArguments = emptyList(),
                     value = key,
                     builder = builder,
                     indent = "$indent  ",
                     requireExplicitHostAccess = requireExplicitHostAccess
                  )
               }

               builder.append(": ")

               serialize(
                  json = json,
                  baseClass = baseClassTypeArguments.getOrNull(0),
                  baseClassTypeArguments = emptyList(),
                  value = entry.value,
                  builder = builder,
                  indent = "$indent  ",
                  requireExplicitHostAccess = requireExplicitHostAccess
               )

               if (index == entries.size - 1) {
                  builder.append("\n$indent}")
               } else {
                  builder.append(",\n$indent")
               }
            }
         }
      } else {
         val includedMemberProperties = newClass.declaredMemberProperties.filter { property ->
            val javaField = property.javaField
            if (javaField == null) {
               false
            } else {
               val hasHostAccessExport = javaField.annotations.find { it is HostAccess.Export } != null
               hasHostAccessExport || !requireExplicitHostAccess
            }
         }

         if (includedMemberProperties.isEmpty()) {
            builder.append("{}")
         } else {
            builder.append("{\n")
            includedMemberProperties.forEachIndexed { index, property ->
               builder.append(indent + "  ")
               builder.append(property.name)
               builder.append(": ")

               @Suppress("UNCHECKED_CAST")
               val anyProperty = property as KProperty1<Any, *>
               val propertyValue: Any? = anyProperty.get(resolvedValue)

               val fieldClass = anyProperty.returnType.classifier as KClass<*>

               serialize(
                  json = json,
                  baseClass = fieldClass,
                  baseClassTypeArguments = anyProperty.returnType.arguments
                     .map {
                        (it.type?.classifier
                           ?: throw Exception("Expected classifier for type arguments")) as KClass<*>
                     },
                  value = propertyValue,
                  builder = builder,
                  indent = "$indent  ",
                  requireExplicitHostAccess = requireExplicitHostAccess
               )

               if (index == includedMemberProperties.size - 1) {
                  builder.append("\n$indent}")
               } else {
                  builder.append(",\n")
               }
            }
         }
      }
   }
}