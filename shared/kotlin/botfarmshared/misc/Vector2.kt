package botfarmshared.misc

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlin.math.*

fun randomSigned() = (Math.random() - 0.5) * 2.0

fun Double.signedPow(exponent: Double) = Math.abs(this).pow(exponent).withSign(this.sign)

@Serializable
class Vector2(
   val x: Double,
   val y: Double
) {
   companion object {
      val zero = Vector2(0.0, 0.0)
      val one = Vector2(1.0, 1.0)

      fun uniform(value: Double) = Vector2(value, value)

      fun randomSignedXY(scale: Double): Vector2 {
         return Vector2(
            randomSigned() * scale,
            randomSigned() * scale
         )
      }

      fun randomSignedXY(scaleX: Double, scaleY: Double): Vector2 {
         return Vector2(
            randomSigned() * scaleX,
            randomSigned() * scaleY
         )
      }

      fun randomSignedXY(scaleX: Double, scaleY: Double, exponent: Double): Vector2 {
         return Vector2(
            randomSigned().signedPow(exponent) * scaleX,
            randomSigned().signedPow(exponent) * scaleY
         )
      }
   }

   fun lerp(target: Vector2, percent: Double): Vector2 {
      val newX = this.x + percent * (target.x - this.x)
      val newY = this.y + percent * (target.y - this.y)
      return Vector2(newX, newY)
   }

   fun moveTowards(target: Vector2, maxDistance: Double): Vector2 {
      val delta = target - this
      val distance = delta.magnitude()

      if (distance <= maxDistance) {
         return target
      }

      val direction = delta / Math.max(distance, 0.00001)

      return this + direction * maxDistance
   }

   operator fun times(scalar: Double) = Vector2(x * scalar, y * scalar)
   operator fun times(vector: Vector2) = Vector2(x * vector.x, y * vector.y)
   operator fun div(scalar: Double) = Vector2(x / scalar, y / scalar)
   operator fun plus(vector: Vector2) = Vector2(x + vector.x, y + vector.y)
   operator fun minus(vector: Vector2) = Vector2(x - vector.x, y - vector.y)

   infix fun dot(other: Vector2): Double = x * other.x + y * other.y

   fun magnitude(): Double = sqrt(x * x + y * y)

   fun normalized(): Vector2 {
      val mag = Math.max(magnitude(), 0.00001)
      return Vector2(x / mag, y / mag)
   }

   infix fun distance(other: Vector2): Double {
      val dx = x - other.x
      val dy = y - other.y
      return sqrt(dx * dx + dy * dy)
   }

   infix fun angleBetween(other: Vector2): Double {
      val dot = this dot other
      val mag1 = this.magnitude()
      val mag2 = other.magnitude()
      return acos(dot / (mag1 * mag2)) * (180.0 / PI)
   }

   override fun toString(): String {
      return "Vector2(x=$x, y=$y)"
   }

   val asJsonArray: JsonArray
      get() = buildJsonArray {
         add(this@Vector2.x)
         add(this@Vector2.y)
      }

   val asJsonArrayRounded: JsonArray
      get() = buildJsonArray {
         add(this@Vector2.x.roundToInt())
         add(this@Vector2.y.roundToInt())
      }

   val rounded: Vector2
      get() = Vector2(this.x.roundToInt().toDouble(), this.y.roundToInt().toDouble())
}

@Serializable
class Vector2KeyFrame(
   val value: Vector2,
   val time: Double
)

@Serializable
class Vector2Animation(
   val keyFrames: List<Vector2KeyFrame> = listOf()
) {
   companion object {
      fun static(value: Vector2): Vector2Animation {
         return Vector2Animation(
            keyFrames = listOf(
               Vector2KeyFrame(
               time = 0.0,
               value = value
            )
            )
         )
      }
   }

   fun resolve(time: Double): Vector2 {
      val keyFrames = this.keyFrames

      if (keyFrames.isEmpty()) {
         return Vector2.zero
      }

      if (keyFrames.size == 1) {
         return keyFrames.first().value
      }

      val last = this.keyFrames.last()

      if (time >= last.time) {
         return last.value
      }

      val first = this.keyFrames.first()

      if (time <= first.time) {
         return first.value
      }

      for (i in 0 until keyFrames.size - 1) {
         val currentElement = keyFrames[i]
         val nextElement = keyFrames[i + 1]

         if (time >= currentElement.time && time <= nextElement.time) {
            val span = nextElement.time - currentElement.time
            val timeSinceCurrent = time - currentElement.time

            if (span <= 0.0) {
               return nextElement.value
            }

            val percent = timeSinceCurrent / span
            return currentElement.value.lerp(nextElement.value, percent)
         }
      }

      return last.value
   }
}

