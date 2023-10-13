package botfarm.game.agentintegration

import botfarm.common.FogOfWarComponentData
import botfarm.common.resolvePosition
import botfarmshared.game.apidata.*
import botfarm.engine.simulation.Entity
import botfarm.game.components.*
import botfarm.game.config.ItemConfig

fun buildEntityInfoForAgent(
   entity: Entity,
   simulationTime: Double
): EntityInfo {
   val simulation = entity.simulation

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

   val damageableComponent = entity.getComponentOrNull<DamageableComponentData>()?.data


   val itemComponent = entity.getComponentOrNull<ItemComponentData>()?.data

   val itemEntityInfo: ItemEntityInfo?
   val damageableEntityInfo: DamageableEntityInfo?
   val growerEntityInfo: GrowerEntityInfo?

   if (itemComponent != null) {
      val itemConfig = simulation.getConfig<ItemConfig>(itemComponent.itemConfigKey)

      itemEntityInfo = ItemEntityInfo(
         itemConfigKey = itemComponent.itemConfigKey,
         itemName = itemConfig.name,
         description = itemConfig.description,
         canBePickedUp = itemConfig.storableConfig != null,
         amount = itemComponent.amount
      )


      growerEntityInfo = if (growerComponent != null &&
         itemConfig.growerConfig != null) {
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
            activeGrowthInfo = activeGrowthInfo,
            canReceiveGrowableItemConfigKeys = itemConfig.growerConfig.canReceiveGrowableItemConfigKeys
         )
      } else {
         null
      }

      damageableEntityInfo = if (damageableComponent != null) {
         DamageableEntityInfo(
            hp = damageableComponent.hp,
            damageableByEquippedToolItemConfigKey = itemConfig.damageableConfig?.damageableByEquippedToolItemConfigKey
         )
      } else {
         null
      }
   } else {
      itemEntityInfo = null
      damageableEntityInfo = null
      growerEntityInfo = null
   }

   val fogOfWarComponentData = entity.getComponent<FogOfWarComponentData>().data

   return EntityInfo(
      observedAtSimulationTime = simulationTime,
      entityId = entity.entityId,
      location = entity.resolvePosition(simulationTime),
      itemInfo = itemEntityInfo,
      characterInfo = characterEntityInfo,
      growerInfo = growerEntityInfo,
      damageableInfo = damageableEntityInfo,
      isVisible = fogOfWarComponentData.isVisible,
      isStale = fogOfWarComponentData.isStale
   )
}