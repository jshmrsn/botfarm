package botfarm.game.setup.scenarios

import botfarm.game.GameSimulation
import botfarm.game.config.RandomItemQuantity

fun spawnCommonEnvironmentEntities(simulation: GameSimulation) {
   val worldBounds = simulation.worldBounds
   val worldCenter = worldBounds * 0.5

   simulation.spawnItems(
      itemConfigKey = "tree",
      quantity = RandomItemQuantity.stacks(250),
      baseLocation = worldCenter,
      randomLocationScale = worldBounds.x * 0.5,
      randomLocationExponent = 1.0
   )

   simulation.spawnItems(
      itemConfigKey = "boulder",
      quantity = RandomItemQuantity.stacks(60),
      baseLocation = worldCenter,
      randomLocationScale = worldBounds.x * 0.5,
      randomLocationExponent = 1.0
   )

   simulation.spawnItems(
      itemConfigKey = "tomato-seeds",
      quantity = RandomItemQuantity.stacks(3),
      baseLocation = worldCenter,
      randomLocationScale = 400.0
   )

   simulation.spawnItems(
      itemConfigKey = "axe",
      quantity = RandomItemQuantity.stacks(5),
      baseLocation = worldCenter,
      randomLocationScale = 700.0
   )
}