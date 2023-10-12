package botfarm.engine.simulation

import botfarmshared.engine.apidata.EntityId
import botfarmshared.misc.buildShortRandomIdentifier

class EntityContainer(
   val simulation: Simulation,
   val sendWebSocketMessage: ((WebSocketMessage) -> Unit)? = null
) {
   private val mutableEntities: MutableList<Entity> = mutableListOf()
   val entities: List<Entity> = this.mutableEntities

   private val mutableEntitiesById = mutableMapOf<EntityId, Entity>()
   private val mutableDestroyedEntitiesById = mutableMapOf<EntityId, Entity>()

   val entitiesById: Map<EntityId, Entity> = this.mutableEntitiesById
   val destroyedEntitiesById: Map<EntityId, Entity> = this.mutableDestroyedEntitiesById

   fun sync(
      source: EntityContainer,
      skipSendForSnapshot: Boolean = false,
      setEntityIsStale: (Entity, Boolean) -> Unit = { entity, stale -> },
      notifySourceEntityIsVisible: (Entity, Boolean) -> Unit = { entity, sourceEntityIsVisible -> },
      hasVisibilityOfEntity: (Entity) -> Boolean
   ) {
      for (thisEntity in this.entities.toList()) {
         if (!source.entitiesById.containsKey(thisEntity.entityId)) {
            if (hasVisibilityOfEntity(thisEntity)) {
               this.destroyEntity(thisEntity)
            }
         }
      }

      for (sourceEntity in source.entities) {
         val thisEntity = this.entitiesById[sourceEntity.entityId]

         if (hasVisibilityOfEntity(sourceEntity)) {
            if (thisEntity == null) {
               this.addEntity(
                  components = sourceEntity.components.map { it.data },
                  skipSendForSnapshot = skipSendForSnapshot,
                  entityId = sourceEntity.entityId
               )
            } else {
               setEntityIsStale(thisEntity, false)
               notifySourceEntityIsVisible(thisEntity, true)

               sourceEntity.components.forEach { sourceComponent ->
                  val thisComponent = thisEntity.getComponent(sourceComponent.componentDataClass)

                  val thisData = thisComponent.data
                  val sourceData = sourceComponent.data

                  if (thisData != sourceData) {
                     thisComponent.setData(sourceData)
                  }
               }
            }
         } else if (thisEntity != null) {
            notifySourceEntityIsVisible(thisEntity, false)

            if (hasVisibilityOfEntity(thisEntity)) {
               // jshmrsn: If stale thisEntity comes back into vision, but source entity has moved elsewhere and is still
               // not visible, tell client to hide the "ghost" of the stale entity
               setEntityIsStale(thisEntity, true)
            }
         }
      }
   }

   fun addEntity(
      components: List<EntityComponentData>,
      skipSendForSnapshot: Boolean = false,
      entityId: EntityId = EntityId(buildShortRandomIdentifier())
   ): Entity {
      val initialEntityData = EntityData(
         components = components,
         entityId = entityId
      )

      val entity = Entity(
         data = initialEntityData,
         simulation = this.simulation,
         sendWebSocketMessage = this.sendWebSocketMessage
      )

      this.mutableEntities.add(entity)
      this.mutableEntitiesById[entityId] = entity

      val sendWebSocketMessage = this.sendWebSocketMessage

      if (sendWebSocketMessage != null && !skipSendForSnapshot) {
         sendWebSocketMessage(
            EntityCreatedWebSocketMessage(
               entityData = entity.buildData(),
               simulationTime = simulation.simulationTime
            )
         )
      }

      return entity
   }

   fun destroyEntity(entity: Entity) {
      this.mutableEntities.remove(entity)
      this.mutableEntitiesById.remove(entity.entityId)

      this.mutableDestroyedEntitiesById[entity.entityId] = entity

      val sendWebSocketMessage = this.sendWebSocketMessage

      if (sendWebSocketMessage != null) {
         sendWebSocketMessage(
            EntityDestroyedWebSocketMessage(
               entityId = entity.entityId,
               simulationTime = this.simulation.simulationTime
            )
         )
      }
   }
}