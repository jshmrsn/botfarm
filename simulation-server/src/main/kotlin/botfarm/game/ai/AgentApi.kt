package botfarm.game.ai

import botfarm.common.resolvePosition
import botfarm.engine.simulation.Entity
import botfarm.game.GameSimulation
import botfarm.game.components.CharacterComponentData
import botfarm.game.systems.AgentState
import botfarmshared.engine.apidata.EntityId
import botfarmshared.game.apidata.MovementRecord
import botfarmshared.game.apidata.SelfSpokenMessage
import botfarmshared.misc.Vector2

class AgentApi(
   val entity: Entity,
   val state: AgentState,
   val simulation: GameSimulation,
   val debugInfo: String
) {
   fun getEntity(
      entityId: EntityId,
      debugReason: String
   ): Entity {
      val simulation = this.simulation

      synchronized(simulation) {
         val targetEntity = simulation.getEntityOrNull(entityId)

         if (targetEntity == null) {
            val destroyedTargetEntity = simulation.getDestroyedEntityOrNull(entityId)
            if (destroyedTargetEntity != null) {
               simulation.broadcastAlertAsGameMessage("Can't find entity for action from AI (but did exist before) ($debugInfo): $debugReason, targetEntityId = $entityId")
            } else {
               simulation.broadcastAlertAsGameMessage("Can't find entity for action from AI ($debugInfo): $debugReason, targetEntityId = $entityId")
            }

            throw Exception("Entity not found: $entityId")
         } else {
            return targetEntity
         }
      }
   }

   fun interactWithEntity(
      targetEntity: Entity
   ) {
      val simulation = this.simulation
      val entity = this.entity

      synchronized(simulation) {
         val characterComponent = entity.getComponent<CharacterComponentData>()

         simulation.moveEntityToPoint(
            entity = entity,
            endPoint = targetEntity.resolvePosition()
         )

         characterComponent.modifyData {
            it.copy(
               pendingInteractionTargetEntityId = targetEntity.entityId
            )
         }
      }
   }

   fun speak(whatToSay: String) {
      val simulation = this.simulation

      synchronized(simulation) {
         this.state.newObservations.selfSpokenMessages.add(
            SelfSpokenMessage(
               message = whatToSay,
               reason = "",
               location = this.entity.resolvePosition(),
               time = simulation.getCurrentSimulationTime()
            )
         )

         simulation.addCharacterMessage(
            entity = this.entity,
            message = whatToSay
         )
      }
   }

   fun walk(
      endPoint: Vector2,
      reason: String
   ) {
      val simulation = this.simulation

      synchronized(simulation) {
         this.state.newObservations.movementRecords.add(
            MovementRecord(
               startedAtTime = simulation.getCurrentSimulationTime(),
               startPoint = this.entity.resolvePosition(),
               endPoint = endPoint,
               reason = reason
            )
         )

         simulation.moveEntityToPoint(
            entity = entity,
            endPoint = endPoint
         )
      }
   }
}