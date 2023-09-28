package botfarm.simulationserver.simulation

private class QueuedSimulationCallback(
   val condition: () -> Boolean,
   val isValid: () -> Boolean,
   val logic: () -> Unit
)

class QueuedCallbacks {
   private val queuedCallbacks = mutableListOf<QueuedSimulationCallback>()

   fun update() {
      var index = 0
      var size = this.queuedCallbacks.size

      while (index < size) {
         val queuedCallback = this.queuedCallbacks[index]
         val isValid = queuedCallback.isValid()

         if (!isValid) {
            this.queuedCallbacks.removeAt(index)
            --size
         } else {
            val condition = queuedCallback.condition()

            if (condition) {
               this.queuedCallbacks.removeAt(index)
               --size
               queuedCallback.logic()
            } else {
               ++index
            }
         }
      }
   }

   fun queueCallback(
      condition: () -> Boolean,
      isValid: () -> Boolean,
      logic: () -> Unit
   ) {
      this.queuedCallbacks.add(
         QueuedSimulationCallback(
            condition = condition,
            isValid = isValid,
            logic = logic
         )
      )
   }
}