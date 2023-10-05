package botfarm.engine.simulation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class EndCoroutineThrowable() : Throwable("Ending coroutine")

class CoroutineSystem(
   val simulation: Simulation,
   val registeredCoroutineSystem: RegisteredCoroutineSystem
) {
   class Entry(
      val entity: Entity,
      val job: Job,
      val context: CoroutineSystemContext
   )

   private val entries = mutableListOf<Entry>()

   fun activateForEntity(
      entity: Entity,
      simulationContainer: SimulationContainer
   ) {
      val callback = this.registeredCoroutineSystem.checkEntity(entity)

      if (callback != null) {
         val simulation = this.simulation

         val context = CoroutineSystemContext(
            entity = entity,
            simulation = simulation,
            simulationContainer = simulationContainer,
            delayImplementation = simulation.context.coroutineDelayImplementation
         )

         val job = CoroutineScope(simulationContainer.coroutineDispatcher).launch {
            try {
               callback(context)
            } catch (end: EndCoroutineThrowable) {
               println("Coroutine system job for entity has ended via stack unwind: ${entity.entityId}}")
            } catch (exception: Exception) {
               println("Exception in coroutine system logic for entity: ${entity.entityId}\nException was : ${exception.stackTraceToString()}")
            }
         }

         this.entries.add(
            Entry(
               entity = entity,
               job = job,
               context = context
            )
         )
      }
   }

   fun handleTermination() {
      this.entries.forEach {
         it.context.cancel()
      }
   }

   fun cleanUp() {
      this.entries.removeIf {
         if (it.job.isCompleted || it.job.isCancelled) {
            println("Removing completed coroutine system entry: " + it.entity.entityId)
            true
         } else {
            false
         }
      }
   }

   fun deactivateForEntity(entity: Entity) {
      this.entries.forEach {
         if (it.entity == entity) {
            it.context.cancel()
         }
      }
   }
}
