package botfarm.simulationserver.game

import botfarm.simulationserver.common.resolvePosition
import botfarm.simulationserver.simulation.EntityComponent
import botfarm.simulationserver.simulation.TickSystemContext

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