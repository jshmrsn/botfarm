package botfarm.simulationserver.game.ai

import botfarm.apidata.CharacterEntityInfo
import botfarm.apidata.EntityInfo
import botfarm.apidata.ItemEntityInfo
import botfarm.simulationserver.game.CharacterComponentData
import botfarm.simulationserver.common.resolvePosition
import botfarm.simulationserver.game.ItemComponentData
import botfarm.simulationserver.game.ItemConfig
import botfarm.simulationserver.simulation.Entity

fun buildEntityInfo(
   interactingEntity: Entity?,
   entity: Entity,
   simulationTime: Double
): EntityInfo {
   val simulation = entity.simulation

   val interactingCharacterComponentData = interactingEntity?.getComponent<CharacterComponentData>()?.data
   val interactingEquippedItemKey = interactingCharacterComponentData?.equippedItemConfigKey

   var availableActionIds: MutableList<String>? = null

   val characterComponent = entity.getComponentOrNull<CharacterComponentData>()?.data

   val characterEntityInfo = if (characterComponent != null) {
      CharacterEntityInfo(
         description = "A human",
         name = characterComponent.name,
         age = characterComponent.age,
         gender = characterComponent.gender
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

         if (itemConfig.canBePickedUp) {
            availableActionIds.add("pickupItem")
         }

         if (itemConfig.canBeDamagedByItem != null &&
            itemConfig.canBeDamagedByItem == interactingEquippedItemKey
         ) {
            availableActionIds.add("harvestItem")
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
      characterEntityInfo = characterEntityInfo
   )
}