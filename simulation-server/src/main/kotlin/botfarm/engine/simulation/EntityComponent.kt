package botfarm.engine.simulation

import kotlin.reflect.KClass


class EntityComponent<COMPONENT_DATA : EntityComponentData>(
   data: COMPONENT_DATA,
   val componentDataClass: KClass<COMPONENT_DATA>,
   val entity: Entity,
   val onComponentChanged: ((EntityComponent<*>, previousData: Any, newData: Any) -> Unit)?
) {
   val simulation = this.entity.simulation
   private var previousSentData = data

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

      val onComponentChanged = this.onComponentChanged

      if (onComponentChanged != null) {
         onComponentChanged(this, this.previousSentData, data)
      }

      this.previousSentData = data
   }


   var data = data
      private set
}