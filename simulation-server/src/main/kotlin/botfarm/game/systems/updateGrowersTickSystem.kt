package botfarm.game.systems

import botfarm.common.resolvePosition
import botfarm.engine.simulation.EntityComponent
import botfarm.engine.simulation.TickSystemContext
import botfarm.game.GameSimulation
import botfarm.game.components.GrowerComponentData
import botfarm.game.config.ItemConfig

fun updateGrowersTickSystem(
   context: TickSystemContext,
   growerComponent: EntityComponent<GrowerComponentData>
) {
   val simulation = context.simulation as GameSimulation

   val simulationTime = simulation.getCurrentSimulationTime()

   val growerComponentData = growerComponent.data

   if (growerComponentData.activeGrowth != null) {
      val activeGrowthGrowableItemConfig =
         simulation.getConfig<ItemConfig>(growerComponentData.activeGrowth.itemConfigKey)
      val growableConfig = activeGrowthGrowableItemConfig.growableConfig
         ?: throw Exception("growableConfig is null on active growth item: " + activeGrowthGrowableItemConfig.key)

      if (simulationTime - growerComponentData.activeGrowth.startTime > growableConfig.timeToGrow) {
         simulation.spawnItems(
            itemConfigKey = growableConfig.growsIntoItemConfigKey,
            quantity = growableConfig.growsIntoItemQuantity,
            randomLocationScale = 30.0,
            baseLocation = context.entity.resolvePosition()
         )

         growerComponent.modifyData {
            it.copy(
               activeGrowth = null
            )
         }
      }
   }
}