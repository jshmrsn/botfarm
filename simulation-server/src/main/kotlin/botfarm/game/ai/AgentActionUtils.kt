package botfarm.game.ai

import botfarm.common.PositionComponentData
import botfarm.common.resolvePosition
import botfarm.engine.simulation.Entity
import botfarm.engine.simulation.EntityComponent
import botfarm.game.GameSimulation
import botfarm.game.components.CharacterComponentData
import botfarm.game.components.isDead
import botfarm.game.systems.AgentState
import botfarmshared.game.apidata.SelfSpokenMessage

class AgentActionUtils(
   val entity: Entity,
   val state: AgentState,
   val simulation: GameSimulation,
   val debugInfo: String
) {
   fun waitForMovement(
      positionComponent: EntityComponent<PositionComponentData>,
      movementResult: GameSimulation.MoveToResult.Success,
      callback: () -> Unit
   ) {
      val simulation = this.simulation

      simulation.queueCallback(
         condition = {
            val keyFrames = positionComponent.data.positionAnimation.keyFrames

            positionComponent.entity.isDead ||
                    positionComponent.data.movementId != movementResult.movementId ||
                    keyFrames.isEmpty() ||
                    simulation.getCurrentSimulationTime() > keyFrames.last().time
         }
      ) {
         callback()
      }
   }

   fun interactWithEntity(
      targetEntity: Entity
   ): GameSimulation.MoveToResult {
      val simulation = this.simulation
      val entity = this.entity

      synchronized(simulation) {
         val characterComponent = entity.getComponent<CharacterComponentData>()

         val movementResult = simulation.moveEntityToPoint(
            entity = entity,
            endPoint = targetEntity.resolvePosition()
         )

         characterComponent.modifyData {
            it.copy(
               pendingInteractionTargetEntityId = targetEntity.entityId
            )
         }

         return movementResult
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
}