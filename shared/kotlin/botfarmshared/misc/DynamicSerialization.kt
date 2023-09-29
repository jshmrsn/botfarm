package botfarmshared.misc

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

   private fun resolvePossibleInlineValueClassInstance(value: Any, valueClass: KClass<*>): Any? {
      if (valueClass.isValue) {
         // jshmrsn: I am confused why inline values class instances are represented using their custom classes
         // instead of their underlying value class. I would expect the runtime instances to just be the inline
         // values, in which this getter call would not be needed. Maybe this doesn't happen in unoptimized builds?
         @Suppress("UNCHECKED_CAST")
         val valueProperty = valueClass.declaredMemberProperties.first() as KProperty1<Any, *>
         val valueFromValueProperty = valueProperty.get(value)

         if (valueFromValueProperty == null) {
            // jshmrsn: Apparently null inline values are sometimes boxed as InlineValueClass(null) instead of just null
            return null
         }

         return valueFromValueProperty
      } else {
         return value
      }
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

      val resolvedValue = resolvePossibleInlineValueClassInstance(value = value, valueClass = newClass)
         ?: return JsonNull

      if (resolvedValue is JsonElement) {
         return resolvedValue
      } else if (resolvedValue is Enum<*>) {
         return JsonPrimitive(resolvedValue.name)
      } else if (resolvedValue is Number) {
         return JsonPrimitive(resolvedValue)
      } else if (resolvedValue is String) {
         return JsonPrimitive(resolvedValue)
      } else if (resolvedValue is Boolean) {
         return JsonPrimitive(resolvedValue)
      } else if (resolvedValue is List<*>) {
         return buildJsonArray {
            @Suppress("UNCHECKED_CAST")
            val list = resolvedValue as List<Any>

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
      } else if (resolvedValue is Map<*, *>) {
         return buildJsonObject {
            @Suppress("UNCHECKED_CAST")
            val map = resolvedValue as Map<Any, Any>

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
               val propertyValue: Any? = anyProperty.get(resolvedValue)

               if (property.name == "type") {
                  throw Exception("Dynamic-serialized value should not have reserved field name 'type' ('type' is reserved for polymorphic serialization)")
               }

               val fieldClass = anyProperty.returnType.classifier as KClass<*>

               val serializedValue = serialize(
                  json = json,
                  baseClass = fieldClass,
                  baseClassTypeArguments = anyProperty.returnType.arguments
                     .map {
                        (it.type?.classifier
                           ?: throw Exception("Expected classifier for type arguments")) as KClass<*>
                     },
                  value = propertyValue
               )

               put(
                  property.name,
                  serializedValue
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
      val resolvedNew = new?.let { resolvePossibleInlineValueClassInstance(value = it, valueClass = it::class) }
      val resolvedPrevious = previous?.let { resolvePossibleInlineValueClassInstance(value = it, valueClass = it::class) }

      if (resolvedNew == null) {
         if (resolvedPrevious == null) {
            return null
         }

         return Diff(
            value = null,
         )
      }

      if (resolvedPrevious == null) {
         return Diff(
            value = serialize(
               json = json,
               baseClass = baseClass,
               baseClassTypeArguments = baseClassTypeArguments,
               value = resolvedNew
            )
         )
      }

      val newClass = resolvedNew::class

      if (resolvedNew is String) {
         return if (resolvedPrevious != resolvedNew) {
            Diff(JsonPrimitive(resolvedNew))
         } else {
            null
         }
      } else if (resolvedNew is Enum<*>) {
         return if (resolvedPrevious != resolvedNew) {
            Diff(JsonPrimitive(resolvedNew.name))
         } else {
            null
         }
      } else if (resolvedNew is Number) {
         return if (resolvedPrevious != resolvedNew) {
            Diff(JsonPrimitive(resolvedNew))
         } else {
            null
         }
      } else if (resolvedNew is Boolean) {
         return if (resolvedPrevious != resolvedNew) {
            Diff(JsonPrimitive(resolvedNew))
         } else {
            null
         }
      } else if (newClass.isSubclassOf(List::class)) {
         @Suppress("UNCHECKED_CAST")
         val previousList = resolvedPrevious as List<Any>

         @Suppress("UNCHECKED_CAST")
         val newList = resolvedNew as List<Any>

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
         val previousMap = resolvedPrevious as Map<String, Any>

         @Suppress("UNCHECKED_CAST")
         val newMap = resolvedNew as Map<String, Any>

         if (newMap.isEmpty()) {
            return if (previousMap.isEmpty()) {
               null
            } else {
               Diff(
                  value = buildJsonObject { }
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
            val newPropertyValue: Any? = anyProperty.get(resolvedNew)
            val previousPropertyValue: Any? = anyProperty.get(resolvedPrevious)

            if (property.name == "type") {
               throw Exception("Dynamic-serialized value should not have reserved field name 'type' ('type'  is reserved for polymorphic serialization)")
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
