package botfarm.misc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.javaField

object DynamicSerialization {
   @Serializable
   data class Diff(
      val value: JsonElement? = null,
      val index: Int? = null,
      val key: String? = null,
      val size: Int? = null,
      val items: List<Diff>? = null,
      val fields: List<Diff>? = null,
      val remove: List<String>? = null
   )

   fun getSerializationNameForClass(aClass: KClass<*>): String {
      val serialName = aClass.annotations.firstNotNullOfOrNull { it as? SerialName }?.value
      return serialName ?: aClass.simpleName ?: throw Exception("Class has no qualified name")
   }

   inline fun <reified T> serializeToString(
      value: T?,
      baseClassTypeArguments: List<KClass<*>> = emptyList(),
      json: Json = Json
   ): String {
      return json.encodeToString(
         serialize(
            value = value,
            baseClassTypeArguments = baseClassTypeArguments,
            json = json
         )
      )
   }

   inline fun <reified T> serialize(
      value: T?,
      baseClassTypeArguments: List<KClass<*>> = emptyList(),
      json: Json = Json
   ): JsonElement {
      return serialize(
         json = json,
         baseClass = T::class,
         baseClassTypeArguments = baseClassTypeArguments,
         value = value
      )
   }

   fun serialize(
      value: Any?,
      baseClass: KClass<*>? = null,
      baseClassTypeArguments: List<KClass<*>> = emptyList(),
      json: Json = Json
   ): JsonElement {
      if (value == null) {
         return JsonNull
      }

      val newClass = value::class

      if (value is JsonElement) {
         return value
      } else if (value is Number) {
         return JsonPrimitive(value)
      } else if (value is String) {
         return JsonPrimitive(value)
      } else if (value is Boolean) {
         return JsonPrimitive(value)
      } else if (value is List<*>) {
         return buildJsonArray {
            @Suppress("UNCHECKED_CAST")
            val list = value as List<Any>

            list.forEach { containedValue ->
               val serializedValue = serialize(
                  json = json,
                  baseClass = baseClassTypeArguments.getOrNull(0),
                  baseClassTypeArguments = emptyList(),
                  value = containedValue
               )

               add(serializedValue)
            }
         }
      } else if (value is  Map<*, *>) {
         return buildJsonObject {
            @Suppress("UNCHECKED_CAST")
            val map = value as Map<Any, Any>

            map.forEach { containedKey, containedValue ->
               val serializedKey = containedKey as? String
                  ?: throw Exception("All dynamically serialized map keys must be strings: $containedKey")

               val serializedValue = serialize(
                  json = json,
                  baseClass = baseClassTypeArguments.getOrNull(1),
                  baseClassTypeArguments = emptyList(),
                  value = containedValue
               )

               put(serializedKey, serializedValue)
            }
         }
      } else {
         val jsonObject = buildJsonObject {
            for (property: KProperty1<out Any, *> in newClass.declaredMemberProperties) {
               if (property.javaField == null) {
                  // jshmrsn: Skip properties which don't have backing fields
                  continue
               }

               @Suppress("UNCHECKED_CAST")
               val anyProperty = property as KProperty1<Any, *>
               val propertyValue: Any? = anyProperty.get(value)

               if (property.name == "type") {
                  throw Exception("Dynamic-serialized value should not have reserved field name 'type'")
               }

               put(
                  property.name, serialize(
                     json = json,
                     baseClass = anyProperty.returnType.classifier as KClass<*>,
                     baseClassTypeArguments = anyProperty.returnType.arguments
                        .map {
                           (it.type?.classifier
                              ?: throw Exception("Expected classifier for type arguments")) as KClass<*>
                        },
                     value = propertyValue
                  )
               )
            }

            if (baseClass != newClass) {
               put("type", getSerializationNameForClass(newClass))
            }
         }

         return jsonObject
      }
   }

   fun serializeDiff(
      previous: Any?,
      new: Any?,
      baseClass: KClass<*>? = null,
      baseClassTypeArguments: List<KClass<*>> = emptyList(),
      json: Json = Json
   ): Diff? {
      if (new == null) {
         if (previous == null) {
            return null
         }

         return Diff(
            value = null,
         )
      }

      if (previous == null) {
         return Diff(
            value = serialize(
               json = json,
               baseClass = baseClass,
               baseClassTypeArguments = baseClassTypeArguments,
               value = new
            )
         )
      }

      val newClass = new::class

      if (new is String) {
         return if (previous != new) {
            Diff(JsonPrimitive(new))
         } else {
            null
         }
      } else if (new is Number) {
         return if (previous != new) {
            Diff(JsonPrimitive(new))
         } else {
            null
         }
      } else if (new is Boolean) {
         return if (previous != new) {
            Diff(JsonPrimitive(new))
         } else {
            null
         }
      } else if (newClass.isSubclassOf(List::class)) {
         @Suppress("UNCHECKED_CAST")
         val previousList = previous as List<Any>

         @Suppress("UNCHECKED_CAST")
         val newList = new as List<Any>

         if (previousList.isEmpty() && newList.isNotEmpty()) {
            return Diff(
               value = serialize(
                  value = newList,
                  baseClass = baseClass,
                  baseClassTypeArguments = baseClassTypeArguments,
                  json = json
               )
            )
         }

         val arrayEntries = newList.mapIndexedNotNull { index, newEntry ->
            val previousEntry = previousList.getOrNull(index)

            val diff = serializeDiff(
               json = json,
               previous = previousEntry,
               new = newEntry,
               baseClass = baseClassTypeArguments.getOrNull(0),
               baseClassTypeArguments = emptyList()
            )

            diff?.copy(
               index = index
            )
         }

         if (previousList.size == newList.size && arrayEntries.isEmpty()) {
            return null
         }

         return Diff(
            value = null,
            size = if (newList.size < previousList.size) {
               newList.size
            } else {
               null
            },
            items = arrayEntries
         )
      } else if (newClass.isSubclassOf(Map::class)) {
         @Suppress("UNCHECKED_CAST")
         val previousMap = previous as Map<String, Any>

         @Suppress("UNCHECKED_CAST")
         val newMap = new as Map<String, Any>

         if (newMap.isEmpty()) {
            return if (previousMap.isEmpty()) {
               null
            } else {
               Diff(
                  value = buildJsonObject {  }
               )
            }
         }

         if (previousMap.isEmpty()) {
            return Diff(
               value = serialize(
                  value = newMap,
                  baseClass = baseClass,
                  baseClassTypeArguments = baseClassTypeArguments,
                  json = json
               )
            )
         }

         val fields = mutableListOf<Diff>()

         for (newEntry in newMap) {
            val previousValue = previousMap[newEntry.key]

            val diff = serializeDiff(
               json = json,
               previous = previousValue,
               new = newEntry.value,
               baseClass = baseClassTypeArguments.getOrNull(1),
               baseClassTypeArguments = emptyList()
            )

            if (diff != null) {
               fields.add(
                  diff.copy(
                     key = newEntry.key
                  )
               )
            }
         }

         val removeObjectKeys = mutableListOf<String>()

         for (previousKey in previousMap.keys) {
            if (!newMap.containsKey(previousKey)) {
               removeObjectKeys.add(previousKey)
            }
         }

         if (fields.isEmpty() && removeObjectKeys.isEmpty()) {
            return null
         }

         return Diff(
            value = null,
            fields = fields,
            remove = removeObjectKeys.ifEmpty {
               null
            }
         )
      } else {
         val fields = mutableListOf<Diff>()

         for (property: KProperty1<out Any, *> in newClass.declaredMemberProperties) {
            if (property.javaField == null) {
               // jshmrsn: Skip properties which don't have backing fields
               continue
            }

            @Suppress("UNCHECKED_CAST")
            val anyProperty = property as KProperty1<Any, *>
            val newPropertyValue: Any? = anyProperty.get(new)
            val previousPropertyValue: Any? = anyProperty.get(previous)

            if (property.name == "type") {
               throw Exception("Dynamic-serialized value should not have reserved field name 'type'")
            }

            val diff = serializeDiff(
               json = json,
               baseClass = anyProperty.returnType.classifier as KClass<*>,
               baseClassTypeArguments = anyProperty.returnType.arguments
                  .map {
                     (it.type?.classifier
                        ?: throw Exception("Expected classifier for type arguments")) as KClass<*>
                  },
               new = newPropertyValue,
               previous = previousPropertyValue
            )

            if (diff != null) {
               fields.add(
                  diff.copy(
                     key = property.name
                  )
               )
            }
         }

         if (fields.isEmpty()) {
            return null
         }

         return Diff(
            value = null,
            fields = fields
         )
      }
   }
}
