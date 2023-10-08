package botfarm.game.setup.scenarios

import botfarm.game.GameSimulation
import botfarm.game.config.RandomItemQuantity

fun spawnCommonEnvironmentEntities(simulation: GameSimulation) {
   val worldBounds = simulation.worldBounds
   val worldCenter = worldBounds * 0.5

   simulation.spawnItems(
      itemConfigKey = "tomato-seeds",
      quantity = RandomItemQuantity.stacks(3),
      baseLocation = worldCenter,
      randomLocationScale = 400.0
   )

//   simulation.spawnItems(
//      itemConfigKey = "hoe",
//      quantity = RandomItemQuantity.stacks(3),
//      baseLocation = worldCenter,
//      randomLocationScale = 400.0
//   )

   simulation.spawnItems(
      itemConfigKey = "axe",
      quantity = RandomItemQuantity.stacks(3),
      baseLocation = worldCenter,
      randomLocationScale = 400.0
   )

//   simulation.spawnItems(
//      itemConfigKey = "pickaxe",
//      quantity = RandomItemQuantity.stacks(3),
//      baseLocation = worldCenter,
//      randomLocationScale = 400.0
//   )

   simulation.spawnItems(
      itemConfigKey = "tree",
      quantity = RandomItemQuantity.stacks(300),
      baseLocation = worldCenter,
      randomLocationScale = worldBounds.x * 0.5,
      randomLocationExponent = 1.0
   )

   simulation.spawnItems(
      itemConfigKey = "boulder",
      quantity = RandomItemQuantity.stacks(90),
      baseLocation = worldCenter,
      randomLocationScale = worldBounds.x * 0.5,
      randomLocationExponent = 1.0
   )
}