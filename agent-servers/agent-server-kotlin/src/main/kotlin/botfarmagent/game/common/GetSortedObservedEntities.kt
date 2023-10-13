package botfarmagent.game.common

import botfarmshared.engine.apidata.EntityId
import botfarmshared.game.apidata.AgentSyncInput
import botfarmshared.game.apidata.EntityInfo
import botfarmshared.game.apidata.EntityInfoWrapper
import botfarmshared.game.apidata.SelfInfo

class SortedEntitiesResult(
   val uniqueEntities: List<EntityInfoWrapper>,
   val nonUniqueEntities: List<EntityInfoWrapper>
)

fun getGroupedSortedObservedEntities(
   entitiesById: Map<EntityId, EntityInfoWrapper>,
   selfInfo: SelfInfo
): SortedEntitiesResult {
   // jshmrsn: Sort order:
   // All characters, by distance
   // Nearest entity of each item type, by distance
   // All other entities, by distance

   val entitiesSortedByDistance = entitiesById.values
      .sortedBy {
         it.entityInfo.location.distance(selfInfo.entityInfoWrapper.entityInfo.location)
      }

   val characters = entitiesSortedByDistance.filter { it.entityInfo.characterInfo != null }

   val hasAddedItemCategories = mutableSetOf<String>()
   val uniqueCategoryEntities = mutableListOf<EntityInfoWrapper>()
   val nonUniqueEntities = mutableListOf<EntityInfoWrapper>()

   entitiesSortedByDistance.forEach { entityInfoWrapper ->
      val entityInfo = entityInfoWrapper.entityInfo
      if (entityInfo.itemInfo != null && entityInfo.characterInfo == null) {
         val categoryComponents = mutableListOf(entityInfo.itemInfo.itemConfigKey)

         val hasActiveGrowth = entityInfo.growerInfo?.activeGrowthInfo != null
         if (hasActiveGrowth) {
            categoryComponents.add("active-growth")
         }

         val category = categoryComponents.joinToString(":")

         if (!hasAddedItemCategories.contains(category)) {
            hasAddedItemCategories.add(category)
            uniqueCategoryEntities.add(entityInfoWrapper)
         } else {
            nonUniqueEntities.add(entityInfoWrapper)
         }
      }
   }

   val groupedResult = SortedEntitiesResult(
      uniqueEntities = characters + uniqueCategoryEntities,
      nonUniqueEntities = nonUniqueEntities
   )

   val combinedResult = groupedResult.uniqueEntities + groupedResult.nonUniqueEntities

   if (combinedResult.size < entitiesSortedByDistance.size) {
      throw Exception("getSortedObservedEntities: Expected all entities to be included in result")
   } else if (combinedResult.size > entitiesSortedByDistance.size) {
      throw Exception("getSortedObservedEntities: Expected entities to only be included once in result")
   }

   return groupedResult
}

fun getSortedObservedEntities(
   entitiesById: Map<EntityId, EntityInfoWrapper>,
   selfInfo: SelfInfo
): List<EntityInfoWrapper> {
   val grouped = getGroupedSortedObservedEntities(
      entitiesById = entitiesById,
      selfInfo = selfInfo
   )

   return grouped.uniqueEntities + grouped.nonUniqueEntities
}