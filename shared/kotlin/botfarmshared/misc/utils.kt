package botfarmshared.misc

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlin.random.Random


fun getCurrentUnixTimeSeconds(): Double {
   return System.currentTimeMillis() / 1000.0
}

val JsonElement?.jsonNullToNull: JsonElement?
   get() = if (this == null || this is JsonNull) {
      null
   } else {
      this
   }


fun <T> Collection<T>.randomWithNullChance(nullChance: Double): T? {
   if (Math.random() < nullChance) {
      return null
   }

   return random(Random)
}


fun <T> List<T>.replaced(index: Int, value: T): List<T> {
   val copy = this.toMutableList()
   copy[index] = value
   return copy
}

fun <T> List<T>.removed(index: Int): List<T> {
   val copy = this.toMutableList()
   copy.removeAt(index)
   return copy
}