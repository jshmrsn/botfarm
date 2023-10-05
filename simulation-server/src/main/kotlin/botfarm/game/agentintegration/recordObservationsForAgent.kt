package botfarm.game.agentintegration

import botfarm.common.PositionComponentData
import botfarm.engine.simulation.Entity
import botfarm.engine.simulation.EntityComponent
import botfarm.game.GameSimulation
import botfarm.game.components.AgentControlledComponentData
import botfarm.game.components.CharacterComponentData
import botfarmshared.game.apidata.ActivityStreamEntryRecord
import botfarmshared.game.apidata.ObservedSpokenMessage

fun recordObservationsForAgent(
   simulation: GameSimulation,
   entity: Entity,
   agentControlledComponent: EntityComponent<AgentControlledComponentData>,
   state: AgentSyncState
) {
   val characterComponent = entity.getComponent<CharacterComponentData>()
   val positionComponent = entity.getComponent<PositionComponentData>()

   val simulationTimeForStep: Double

   synchronized(simulation) {
      val observationDistance = agentControlledComponent.data.observationDistance

      simulationTimeForStep = simulation.getCurrentSimulationTime()
      val currentLocation = positionComponent.data.positionAnimation.resolve(simulationTimeForStep)

      simulation.entities.forEach { otherEntity ->
         val otherCharacterComponentData = otherEntity.getComponentOrNull<CharacterComponentData>()?.data
         val otherPositionComponent = otherEntity.getComponentOrNull<PositionComponentData>()

         if (otherEntity != entity && otherPositionComponent != null) {
            val otherPosition = otherPositionComponent.data.positionAnimation.resolve(simulationTimeForStep)

            val distance = otherPosition.distance(currentLocation)

            if (distance <= observationDistance) {
               state.mutableObservations.entitiesById[otherEntity.entityId] = buildEntityInfoForAgent(
                  entity = otherEntity,
                  simulationTime = simulationTimeForStep
               )

               if (otherCharacterComponentData != null) {
                  val newMessages = otherCharacterComponentData.recentSpokenMessages.filter {
                     it.sentSimulationTime > state.previousNewEventCheckSimulationTime
                  }

                  newMessages.forEach { newMessage ->
                     println("Adding spoken message observation: " + newMessage.message)
                     state.mutableObservations.spokenMessages.add(
                        ObservedSpokenMessage(
                           entityId = otherEntity.entityId,
                           characterName = otherCharacterComponentData.name,
                           message = newMessage.message,
                           speakerLocation = otherPosition,
                           myLocation = currentLocation,
                           time = newMessage.sentSimulationTime
                        )
                     )
                  }
               }
            }
         }
      }

      val activityStream = simulation.getActivityStream()

      val newActivityStreamEntries = activityStream.filter { activityStreamEntry ->
         activityStreamEntry.time > state.previousNewEventCheckSimulationTime &&
                 activityStreamEntry.shouldReportToAi
      }

      state.mutableObservations.activityStreamEntries.addAll(newActivityStreamEntries.map {
         ActivityStreamEntryRecord(
            time = it.time,
            title = it.title,
            message = it.message,
            actionType = it.actionType,
            sourceEntityId = it.sourceEntityId,
            sourceLocation = it.sourceLocation,
            targetEntityId = it.targetEntityId
         )
      })

      state.previousNewEventCheckSimulationTime = simulationTimeForStep
   }
}