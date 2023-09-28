package botfarm.simulationserver.game

import botfarm.apidata.ItemCollection
import botfarm.apidata.ItemCollectionEntry
import botfarm.misc.RandomConfig
import botfarm.misc.Vector2
import botfarm.misc.getCurrentUnixTimeSeconds
import botfarm.simulationserver.common.SpriteConfig
import botfarm.simulationserver.game.ai.AgentServerIntegration
import botfarm.simulationserver.simulation.Config
import botfarm.simulationserver.simulation.SimulationContainer
import botfarm.simulationserver.simulation.SimulationData

fun createGameSimulation(
   simulationContainer: SimulationContainer,
   agentServerIntegration: AgentServerIntegration
): SimulationContainer.CreateSimulationResult {
   val configs = mutableListOf<Config>()

   addCharacterConfigs(configs)
   addItemConfigs(configs)

   val unixTime = getCurrentUnixTimeSeconds()

   val simulationData = SimulationData(
      lastTickUnixTime = unixTime,
      configs = configs
   )

   val simulation = GameSimulation(
      data = simulationData,
      simulationContainer = simulationContainer,
      agentServerIntegration = agentServerIntegration
   )

   simulation.createEntity(
      components = listOf(ActivityStreamComponentData()),
      entityId = GameSimulation.activityStreamEntityId
   )

   val worldBounds = Vector2(5000.0, 5000.0)

   val worldCenter = worldBounds * 0.5

   simulation.spawnItems(
      itemConfigKey = "tomato-seeds",
      quantity = RandomItemQuantity.stacks(3),
      baseLocation = worldCenter,
      randomLocationScale = 400.0
   )

   simulation.spawnItems(
      itemConfigKey = "hoe",
      quantity = RandomItemQuantity.stacks(3),
      baseLocation = worldCenter,
      randomLocationScale = 400.0
   )

   simulation.spawnItems(
      itemConfigKey = "axe",
      quantity = RandomItemQuantity.stacks(3),
      baseLocation = worldCenter,
      randomLocationScale = 400.0
   )

   simulation.spawnItems(
      itemConfigKey = "pickaxe",
      quantity = RandomItemQuantity.stacks(3),
      baseLocation = worldCenter,
      randomLocationScale = 400.0
   )

   simulation.spawnItems(
      itemConfigKey = "tree",
      quantity = RandomItemQuantity.stacks(100, 200),
      baseLocation = worldCenter,
      randomLocationScale = worldBounds.x * 0.5,
      randomLocationExponent = 0.95
   )

   simulation.spawnItems(
      itemConfigKey = "boulder",
      quantity = RandomItemQuantity.stacks(50, 60),
      baseLocation = worldCenter,
      randomLocationScale = worldBounds.x * 0.5,
      randomLocationExponent = 0.8
   )

   return simulationContainer.addSimulation(simulation)
}


private fun addItemConfigs(configs: MutableList<Config>) {
   fun addItemConfig(
      key: String,
      name: String,
      description: String = "",
      textureUrl: String,
      iconUrl: String? = null,
      spriteDepthOffset: Double = 0.0,
      spriteBaseScale: Vector2 = Vector2.one,
      spriteBaseOffset: Vector2 = Vector2.zero,
      useCustomAnimationBaseName: String? = null,
      killableConfig: KillableConfig? = null,
      storableConfig: StorableConfig? = null,
      craftableConfig: CraftableConfig? = null,
      equippableConfig: EquippableConfig? = null,
      spawnItemOnDestructionConfig: SpawnItemOnDestructionConfig? = null, // tree spawns wood when cut down
      growerConfig: GrowerConfig? = null, // farm plots receive and grow carrot seeds
      growableConfig: GrowableConfig? = null, // carrot seeds grow into carrots
      spawnItemOnUseConfig: SpawnItemOnUseConfig? = null, // hoe spawns farm plots
      blocksPlacement: Boolean = storableConfig == null
   ): ItemConfig {
      val spriteConfigKey = key + "_sprite"

      configs.add(
         SpriteConfig(
            key = spriteConfigKey,
            textureUrl = textureUrl,
            baseOffset = spriteBaseOffset,
            baseScale = spriteBaseScale,
            depthOffset = spriteDepthOffset
         )
      )

      val itemConfig = ItemConfig(
         key = key,
         name = name,
         description = description,
         spriteConfigKey = spriteConfigKey,
         iconUrl = iconUrl ?: textureUrl,
         killableConfig = killableConfig,
         storableConfig = storableConfig,
         equippableConfig = equippableConfig,
         craftableConfig = craftableConfig,
         spawnItemOnDestructionConfig = spawnItemOnDestructionConfig,
         growableConfig = growableConfig,
         growerConfig = growerConfig,
         spawnItemOnUseConfig = spawnItemOnUseConfig,
         blocksPlacement = blocksPlacement,
         useCustomAnimationBaseName = useCustomAnimationBaseName
      )

      configs.add(itemConfig)

      return itemConfig
   }

   addItemConfig(
      key = "stone",
      name = "Stone",
      description = "Useful resource for crafting",
      textureUrl = "assets/items/stone/stone_ground.png",
      iconUrl = "assets/items/stone/stone_icon.png",
      spriteBaseScale = Vector2.uniform(0.25),
      storableConfig = StorableConfig(
         maxStackSize = 50
      )
   )

   addItemConfig(
      key = "wood",
      name = "Wood",
      description = "Useful resource for crafting",
      textureUrl = "assets/items/wood/wood.png",
      iconUrl = "assets/items/wood/wood.png",
      spriteBaseScale = Vector2(0.3, 0.3),
      storableConfig = StorableConfig(
         maxStackSize = 50
      )
   )

   addItemConfig(
      key = "axe",
      name = "Axe",
      description = "An axe for cutting down trees",
      textureUrl = "assets/items/axe/axe.png",
      iconUrl = "assets/items/axe/axe.png",
      useCustomAnimationBaseName = "slash",
      storableConfig = StorableConfig(
         maxStackSize = 1
      ),
      equippableConfig = EquippableConfig(
         equippedCompositeAnimation = CompositeAnimation(
            key = "weapon_blunt_waraxe",
            variant = "waraxe"
         ),
         equipmentSlot = EquipmentSlot.Tool
      ),
      spriteBaseScale = Vector2(0.25, 0.25),
      craftableConfig = CraftableConfig(
         craftingCost = ItemCollection(listOf(ItemCollectionEntry("wood", 25)))
      )
   )

   addItemConfig(
      key = "hoe",
      name = "Hoe",
      description = "A hoe for preparing farm plots",
      textureUrl = "assets/items/hoe/hoe.png",
      iconUrl = "assets/items/hoe/hoe.png",
      spriteBaseScale = Vector2(0.25, 0.25),
      useCustomAnimationBaseName = "thrust",
      storableConfig = StorableConfig(
         maxStackSize = 1
      ),
      equippableConfig = EquippableConfig(
         equippedCompositeAnimation = CompositeAnimation(
            key = "tool_thrust",
            variant = "hoe"
         ),
         equipmentSlot = EquipmentSlot.Tool
      ),
      craftableConfig = CraftableConfig(
         craftingCost = ItemCollection(listOf(ItemCollectionEntry("wood", 25)))
      ),
      spawnItemOnUseConfig = SpawnItemOnUseConfig(
         spawnItemConfigKey = "farm-plot",
         quantity = RandomItemQuantity.amount(10)
      )
   )

   addItemConfig(
      key = "pickaxe",
      name = "Pickaxe",
      description = "A pickaxe for breaking apart boulders",
      textureUrl = "assets/items/pickaxe/pickaxe.png",
      iconUrl = "assets/items/pickaxe/pickaxe.png",
      spriteBaseScale = Vector2(0.15, 0.15),
      useCustomAnimationBaseName = "slash",
      storableConfig = StorableConfig(
         maxStackSize = 1
      ),
      craftableConfig = CraftableConfig(
         craftingCost = ItemCollection(listOf(ItemCollectionEntry("wood", 75)))
      ),
      equippableConfig = EquippableConfig(
         equippedCompositeAnimation = CompositeAnimation(
            key = "pickaxe",
            variant = "pickaxe"
         ),
         equipmentSlot = EquipmentSlot.Tool
      )
   )

   addItemConfig(
      key = "farm-plot",
      name = "Farm Plot",
      spriteDepthOffset = -200.0,
      description = "A small farm plot",
      textureUrl = "assets/items/farm-plot/farm-plot.png",
      iconUrl = "assets/items/farm-plot/farm-plot.png",
      spriteBaseScale = Vector2.uniform(1.0),
      spriteBaseOffset = Vector2(0.0, 0.0),
      growerConfig = GrowerConfig(
         canReceiveGrowableItemConfigKeys = listOf(
            "tomato-seeds"
         )
      )
   )

   addItemConfig(
      key = "tomato-seeds",
      name = "Tomato Seeds",
      description = "Tomato seeds",
      textureUrl = "assets/items/tomato-seeds/tomato-seeds.png",
      iconUrl = "assets/items/tomato-seeds/tomato-seeds.png",
      spriteBaseScale = Vector2.uniform(0.25),
      spriteBaseOffset = Vector2(0.0, 0.0),
      storableConfig = StorableConfig(
         maxStackSize = 10
      ),
      growableConfig = GrowableConfig(
         growingSpriteConfigKey = "tomato_sprite",
         growsIntoItemConfigKey = "tomato",
         timeToGrow = 10.0,
         progressAnimationNames = listOf(),
         growsIntoItemQuantity = RandomItemQuantity.stacksOfAmount(
            stackCount = RandomConfig.range(1, 3),
            amountPerStack = RandomConfig.range(1, 3)
         )
      ),
      equippableConfig = EquippableConfig(
         equipmentSlot = EquipmentSlot.Tool
      )
   )

   addItemConfig(
      key = "tomato",
      name = "Tomato",
      description = "Tomato",
      textureUrl = "assets/items/tomato/tomato.png",
      iconUrl = "assets/items/tomato/tomato.png",
      spriteBaseScale = Vector2.uniform(0.2),
      spriteBaseOffset = Vector2(0.0, 0.0),
      storableConfig = StorableConfig(
         maxStackSize = 10
      )
   )

   addItemConfig(
      key = "tree",
      name = "Tree",
      description = "A tree",
      textureUrl = "assets/items/tree/tree.png",
      iconUrl = "assets/items/tree/tree.png",
      spriteBaseScale = Vector2.uniform(0.8),
      spriteBaseOffset = Vector2(0.0, -70.0),
      spawnItemOnDestructionConfig = SpawnItemOnDestructionConfig(
         spawnItemConfigKey = "wood",
         quantity = RandomItemQuantity.stacksOfAmount(
            stackCount = RandomConfig.range(1, 3),
            amountPerStack = RandomConfig.range(10, 30)
         )
      ),
      killableConfig = KillableConfig(
         canBeDamagedByToolItemConfigKey = "axe",
         maxHp = 100
      )
   )

   addItemConfig(
      key = "boulder",
      name = "Boulder",
      description = "A boulder",
      textureUrl = "assets/items/boulder/boulder.png",
      iconUrl = "assets/items/boulder/boulder.png",
      spawnItemOnDestructionConfig = SpawnItemOnDestructionConfig(
         spawnItemConfigKey = "stone",
         quantity = RandomItemQuantity.stacksOfAmount(
            stackCount = RandomConfig.range(1, 3),
            amountPerStack = RandomConfig.range(5, 15)
         )
      ),
      spriteBaseScale = Vector2.uniform(0.6),
      killableConfig = KillableConfig(
         canBeDamagedByToolItemConfigKey = "pickaxe",
         maxHp = 100
      )
   )

   addItemConfig(
      key = "house",
      name = "House",
      description = "A house",
      textureUrl = "assets/items/house/house.png",
      iconUrl = "assets/items/house/house.png",
      spriteBaseScale = Vector2.uniform(0.6),
      craftableConfig = CraftableConfig(
         craftingCost = ItemCollection(
            listOf(
               ItemCollectionEntry("wood", 200),
               ItemCollectionEntry("stone", 75)
            )
         )
      )
   )
}

private fun addCharacterConfigs(configs: MutableList<Config>) {
   val includedSkinColors = listOf(
      "light",
      "amber",
      "olive",
      "bronze",
      "brown",
      "black"
   )


   val hairColors = listOf("blonde", "ash", "sandy", "black", "gray", "white")

   val characterBodySelectionsConfig = CharacterBodySelectionsConfig(
      key = "character-body-selections-config",
      bodyTypes = listOf("male", "female"),
      skinColors = includedSkinColors,
      bodies = listOf("body"), // skeleton, zombie
      heads = listOf("heads_human_male", "heads_human_female"),
      noses = listOf("head_nose_straight", "head_nose_elderly", "head_nose_button", "head_nose_big"),
      eyes = listOf(
         RegisteredCompositeAnimation(
            key = "eyes",
            includedVariants = listOf("blue", "brown", "green")
         )
      ),
      wrinkles = listOf("head_wrinkles"),
      hairs = listOf(
         RegisteredCompositeAnimation("hair_swoop", hairColors),
         RegisteredCompositeAnimation("hair_spiked", hairColors)
      )
   )

   val includedCategories = characterBodySelectionsConfig.bodyTypes

   val compositeAnimationRegistryConfig = CompositeAnimationRegistryConfig(
      key = "composite-animation-registry",
      includedCategories = includedCategories,
      registeredCompositeAnimations = listOf(
         RegisteredCompositeAnimation(
            key = "torso_clothes_male_sleeveless_laced",
            includedVariants = listOf("white")
         ),
         RegisteredCompositeAnimation(
            key = "legs_pantaloons",
            includedVariants = listOf("white")
         )
      )
   )

   configs.add(compositeAnimationRegistryConfig)

   configs.add(characterBodySelectionsConfig)

   fun buildConfigsForCharacterAnimationLayer(
      key: String,
      animationsConfigName: String = "universal",
      spriteSheetRelativePath: String
   ): List<Config> {
      return listOf(
         SpriteConfig(
            textureUrl = "assets/liberated-pixel-cup-characters/spritesheets/$spriteSheetRelativePath",
            atlasUrl = "assets/liberated-pixel-cup-characters/atlases/animations-$animationsConfigName.json",
            animationsUrl = "assets/liberated-pixel-cup-characters/animations/animations-$animationsConfigName.json",
            key = key
         )
      )
   }

   configs.addAll(
      listOf(
         buildConfigsForCharacterAnimationLayer(
            key = "body",
            spriteSheetRelativePath = "body/bodies/male/universal.png"
         ),
         buildConfigsForCharacterAnimationLayer(
            key = "clothing",
            spriteSheetRelativePath = "torso/armour/plate/male/brass.png"
         )
      ).flatten()
   )
}
