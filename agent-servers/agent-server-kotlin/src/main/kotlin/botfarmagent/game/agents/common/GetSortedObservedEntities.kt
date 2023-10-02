package botfarmagent.game.agents.common

import botfarmshared.game.apidata.AgentSyncInputs
import botfarmshared.game.apidata.SelfInfo

fun getSortedObservedEntities(
   inputs: AgentSyncInputs,
   selfInfo: SelfInfo
) = inputs.newObservations.entitiesById
   .values
   .sortedWith { a, b ->
      val isCharacterA = a.characterEntityInfo != null
      val isCharacterB = b.characterEntityInfo != null
      if (isCharacterA != isCharacterB) {
         if (isCharacterA) {
            -1
         } else {
            1
         }
      } else {
         val distanceA = a.location.distance(selfInfo.entityInfo.location)
         val distanceB = b.location.distance(selfInfo.entityInfo.location)
         distanceA.compareTo(distanceB)
      }
   }