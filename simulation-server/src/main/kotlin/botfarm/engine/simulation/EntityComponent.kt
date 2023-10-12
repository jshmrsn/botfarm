package botfarm.engine.simulation

import botfarmshared.misc.DynamicSerialization
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.reflect.KClass

private val excludeDefaultsJsonFormat = Json {
   encodeDefaults = false
}

class EntityComponent<COMPONENT_DATA : EntityComponentData>(
   data: COMPONENT_DATA,
   val componentDataClass: KClass<COMPONENT_DATA>,
   val entity: Entity,
   val sendWebSocketMessage: ((WebSocketMessage) -> Unit)?
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

      val sendWebSocketMessage = this.sendWebSocketMessage

      if (sendWebSocketMessage != null) {
         val diff = DynamicSerialization.serializeDiff(
            previous = this.previousSentData,
            new = data
         )

         if (diff != null) {
//         println("Broadcasting entity component message: entityId = $entityId, ${componentData::class}")
            // jshmrsn: Pre-serialize so we can exclude defaults for less network data
            val serializedDiff = excludeDefaultsJsonFormat.encodeToJsonElement(diff)

            sendWebSocketMessage(
               EntityComponentWebSocketMessage(
                  entityId = this.entity.entityId,
                  componentTypeName = DynamicSerialization.getSerializationNameForClass(this.componentDataClass),
                  diff = serializedDiff,
                  simulationTime = this.simulation.simulationTime
               )
            )
         }
      }

      this.previousSentData = data
   }


   var data = data
      private set
}