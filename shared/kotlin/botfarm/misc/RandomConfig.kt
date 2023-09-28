package botfarm.misc

import kotlin.math.roundToInt
import kotlin.random.Random

class RandomConfig private constructor(
   // joshr: These can't be private because they are serialized
   val fixed: Double? = null,
   val range: Range? = null
) {
   companion object {
      val zero = RandomConfig.fixed(0.0)
      val one = RandomConfig.fixed(1.0)

      fun fixed(value: Number) = RandomConfig(
         fixed = value.toDouble()
      )

      fun range(min: Number, max: Number) = RandomConfig(
         range = Range(
            min = min.toDouble(),
            max = max.toDouble()
         )
      )
   }

   class Range(
      val min: Double,
      val max: Double
   )

   fun rollDouble(): Double {
      if (this.fixed != null) {
         return this.fixed.toDouble()
      }

      if (this.range != null) {
         return Random.nextDouble(this.range.min, this.range.max)
      }

      throw Exception("Invalid RandomConfig")
   }

   fun rollInt(): Int {
      return this.rollDouble().roundToInt()
   }
}