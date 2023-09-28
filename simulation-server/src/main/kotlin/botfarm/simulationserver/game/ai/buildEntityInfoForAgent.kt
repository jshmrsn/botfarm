package botfarm.simulationserver.game.ai

import botfarm.apidata.*
import botfarm.simulationserver.common.resolvePosition
import botfarm.simulationserver.game.*
import botfarm.simulationserver.simulation.Entity

fun buildEntityInfoForAgent(
   interactingEntity: Entity?,
   entity: Entity,
   simulationTime: Double
): EntityInfo {
   val simulation = entity.simulation

   val interactingEquippedItemKey = interactingEntity?.getEquippedItemConfig(EquipmentSlot.Tool)?.key

   var availableActionIds: MutableList<String>? = null

   val characterComponent = entity.getComponentOrNull<CharacterComponentData>()?.data

   val characterEntityInfo = if (characterComponent != null) {
      CharacterEntityInfo(
         description = "A human",
         name = characterComponent.name,
         age = characterComponent.age,
         gender = characterComponent.bodySelections.bodyType,
         hairColor = characterComponent.bodySelections.hair?.variant,
         hairStyle = characterComponent.bodySelections.hair?.key,
         skinColor = characterComponent.bodySelections.skinColor
      )
   } else {
      null
   }

   val growerComponent = entity.getComponentOrNull<GrowerComponentData>()?.data


   val growerEntityInfo = if (growerComponent != null) {
      val activeGrowth = growerComponent.activeGrowth

      val activeGrowthInfo = if (activeGrowth != null) {
         val activeGrowthItemConfig = simulation.getConfig<ItemConfig>(activeGrowth.itemConfigKey)
         val growableConfig = activeGrowthItemConfig.growableConfig ?:  throw Exception("growableConfig is null for active active growth")

         ActiveGrowthInfo(
            growableItemConfigKey = activeGrowth.itemConfigKey,
            startTime = activeGrowth.startTime,
            duration = growableConfig.timeToGrow,
            growingIntoItemConfigKey = growableConfig.growsIntoItemConfigKey
         )
      } else {
         null
      }

      GrowerEntityInfo(
         activeGrowthInfo = activeGrowthInfo
      )
   } else {
      null
   }

   val itemComponent = entity.getComponentOrNull<ItemComponentData>()?.data

   val itemEntityInfo: ItemEntityInfo?

   if (itemComponent != null) {
      val itemConfig = simulation.getConfig<ItemConfig>(itemComponent.itemConfigKey)

      itemEntityInfo = ItemEntityInfo(
         itemConfigKey = itemComponent.itemConfigKey,
         itemName = itemConfig.name,
         description = itemConfig.description
      )

      if (interactingEntity != null) {
         availableActionIds = mutableListOf()

         if (itemConfig.storableConfig != null) {
            availableActionIds.add("pickupItem")
         }

         if (itemConfig.killableConfig?.canBeDamagedByToolItemConfigKey != null &&
            itemConfig.killableConfig.canBeDamagedByToolItemConfigKey == interactingEquippedItemKey
         ) {
            availableActionIds.add("harvestItem")
         }

         if (itemConfig.growerConfig != null &&
            interactingEquippedItemKey != null &&
            itemConfig.growerConfig.canReceiveGrowableItemConfigKeys.contains(interactingEquippedItemKey)) {

            if (growerComponent != null && growerComponent.activeGrowth == null) {
               availableActionIds.add("plantItem")
            }
         }
      }
   } else {
      itemEntityInfo = null
   }

   if (availableActionIds != null && availableActionIds.isEmpty()) {
      availableActionIds = null
   }

   return EntityInfo(
      observedAtSimulationTime = simulationTime,
      entityId = entity.entityId,
      location = entity.resolvePosition(simulationTime),
      availableActionIds = availableActionIds,
      itemEntityInfo = itemEntityInfo,
      characterEntityInfo = characterEntityInfo,
      growerEntityInfo = growerEntityInfo
   )
}