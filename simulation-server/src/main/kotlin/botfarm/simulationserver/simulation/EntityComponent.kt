package botfarm.simulationserver.simulation

import kotlin.reflect.KClass

class EntityComponent<COMPONENT_DATA : EntityComponentData>(
   data: COMPONENT_DATA,
   val componentDataClass: KClass<COMPONENT_DATA>,
   val entity: Entity
) {
   val simulation = this.entity.simulation
   private var previousBroadcastData = data

   fun modifyData(transform: (COMPONENT_DATA) -> COMPONENT_DATA) {
      val typedData = this.data
      val newData = transform(typedData)
      this.setData(newData)
   }

   fun setData(data: EntityComponentData) {
      if (data::class != this.data::class) {
         throw Exception("EntityComponent.setData called with different data class type. Existing data is of type ${this.data::class}, new data is of type ${data::class}")
      }

      @Suppress("UNCHECKED_CAST")
      this.data = data as COMPONENT_DATA

      this.entity.simulation.broadcastEntityComponentMessage(
         entityId = this.entity.entityId,
         componentData = data,
         previousBroadcastData = this.previousBroadcastData
      )

      this.previousBroadcastData = data
   }

   fun start(
      simulation: Simulation,
      entity: Entity,
      startContext: StartContext
   ) {

   }

   fun stop() {

   }

   var data = data
      private set
}