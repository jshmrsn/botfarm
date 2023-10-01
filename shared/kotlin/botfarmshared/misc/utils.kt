package botfarmshared.misc

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import java.security.MessageDigest
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

// https://gist.github.com/lovubuntu/164b6b9021f5ba54cefc67f60f7a1a25
fun sha256(text: String) = MessageDigest
   .getInstance("SHA-256")
   .digest(text.toByteArray())
   .fold("") { str, it -> str + "%02x".format(it) }
