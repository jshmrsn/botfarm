package botfarmagent.game.common

import botfarmshared.game.apidata.AgentSyncInput
import botfarmshared.game.apidata.EntityInfo
import botfarmshared.game.apidata.SelfInfo

class SortedEntitiesResult(
   val uniqueEntities: List<EntityInfo>,
   val nonUniqueEntities: List<EntityInfo>
)

fun getGroupedSortedObservedEntities(
   inputs: AgentSyncInput,
   selfInfo: SelfInfo
): SortedEntitiesResult {
   // jshmrsn: Sort order:
   // All characters, by distance
   // Nearest entity of each item type, by distance
   // All other entities, by distance

   val entitiesSortedByDistance = inputs.newObservations.entitiesById.values
      .sortedBy {
         it.location.distance(selfInfo.entityInfo.location)
      }

   val characters = entitiesSortedByDistance.filter { it.characterInfo != null }

   val hasAddedItemCategories = mutableSetOf<String>()
   val uniqueCategoryEntities = mutableListOf<EntityInfo>()
   val nonUniqueEntities = mutableListOf<EntityInfo>()

   entitiesSortedByDistance.forEach { entityInfo ->
      if (entityInfo.itemInfo != null && entityInfo.characterInfo == null) {
         val categoryComponents = mutableListOf(entityInfo.itemInfo.itemConfigKey)

         val hasActiveGrowth = entityInfo.growerInfo?.activeGrowthInfo != null
         if (hasActiveGrowth) {
            categoryComponents.add("active-growth")
         }

         val category = categoryComponents.joinToString(":")

         if (!hasAddedItemCategories.contains(category)) {
            hasAddedItemCategories.add(category)
            uniqueCategoryEntities.add(entityInfo)
         } else {
            nonUniqueEntities.add(entityInfo)
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
   inputs: AgentSyncInput,
   selfInfo: SelfInfo
): List<EntityInfo> {
   val grouped = getGroupedSortedObservedEntities(
      inputs = inputs,
      selfInfo = selfInfo
   )

   return grouped.uniqueEntities + grouped.nonUniqueEntities
}