package botfarm.engine.simulation

import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

class Entity(
   data: EntityData,
   val simulation: Simulation,
   val sendWebSocketMessage: ((WebSocketMessage) -> Unit)?
) {
   val entityId = data.entityId

   var destroyedAtTime: Double? = null
      private set

   val isDestroyed: Boolean
      get() = this.destroyedAtTime != null

   val exists: Boolean
      get() = this.destroyedAtTime == null

   private val mutableComponents: MutableList<EntityComponent<*>> = data.components.map {
      EntityComponent<EntityComponentData>(
         data = it,
         entity = this,
         componentDataClass = it.javaClass.kotlin,
         sendWebSocketMessage = this.sendWebSocketMessage
      )
   }.toMutableList()

   val components: List<EntityComponent<*>> = this.mutableComponents

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
         ?: throw Exception("Entity does not have component for type: $componentType (${this.entityId})")
   }

   inline fun <reified T : EntityComponentData> getComponent(): EntityComponent<T> {
      return this.getComponent<T>(T::class)
   }

   fun destroy() {
      synchronized(this.simulation) {
         if (this.destroyedAtTime == null) {
            this.destroyedAtTime = this.simulation.getCurrentSimulationTime()
            this.simulation.handleEntityDestroyed(entity = this)
         }
      }
   }

   fun buildData(): EntityData {
      return EntityData(
         entityId = this.entityId,
         components = this.mutableComponents.map {
            it.data
         }
      )
   }
}