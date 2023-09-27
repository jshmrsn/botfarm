package botfarm.simulationserver.simulation

import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf



class Entity(
   data: EntityData,
   val simulation: Simulation
) {
   val entityId = data.entityId

   var destroyedAtTime: Double? = null
      private set


   private val mutableComponents: MutableList<EntityComponent<*>> = data.components.map {
      EntityComponent<EntityComponentData>(
         data = it,
         entity = this,
         componentDataClass = it.javaClass.kotlin
      )
   }.toMutableList()

   val components: List<EntityComponent<*>> = this.mutableComponents

   fun start(
      startContext: StartContext,
      simulation: Simulation
   ) {
      this.mutableComponents.forEach {
         it.start(
            startContext = startContext,
            simulation = simulation,
            entity = this
         )
      }
   }



   fun <T : EntityComponentData> getComponentOrNull(componentType: KClass<T>): EntityComponent<T>? {
      val untypedResult = this.mutableComponents.find {
         it.data::class.isSubclassOf(componentType)
      }

      if (untypedResult != null) {
         @Suppress("UNCHECKED_CAST")
         return untypedResult as EntityComponent<T>
      } else {
         return null
      }
   }

   inline fun <reified T : EntityComponentData> getComponentOrNull(): EntityComponent<T>? {
      return this.getComponentOrNull(T::class)
   }

   fun <T : EntityComponentData> getComponent(componentType: KClass<T>): EntityComponent<T> {
      return this.getComponentOrNull(componentType)
         ?: throw Exception("Entity does not have component for type: $componentType")
   }

   inline fun <reified T : EntityComponentData> getComponent(): EntityComponent<T> {
      return this.getComponent<T>(T::class)
   }

   fun destroy() {
      this.destroyedAtTime = this.simulation.getCurrentSimulationTime()
      this.simulation.handleEntityDestroyed(entity = this)
   }

   fun buildData(): EntityData {
      return EntityData(
         entityId = this.entityId,
         components = this.mutableComponents.map {
            it.data
         }
      )
   }

   fun stop() {
      this.mutableComponents.forEach {
         it.stop()
      }
   }
}