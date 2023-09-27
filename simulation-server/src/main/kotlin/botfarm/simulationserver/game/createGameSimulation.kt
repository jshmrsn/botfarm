package botfarm.simulationserver.game

import botfarm.apidata.ItemCollection
import botfarm.apidata.ItemCollectionEntry
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
      tickedSimulationTime = 0.0,
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
      entityId = "activity-stream"
   )

   val worldBounds = Vector2(5000.0, 5000.0)

   val worldCenter = worldBounds * 0.5

   simulation.spawnItems(
      itemConfigKey = "axe",
      minStacks = 3,
      maxStacks = 3,
      baseLocation = worldCenter,
      randomLocationScale = 400.0
   )

   simulation.spawnItems(
      itemConfigKey = "tree",
      minStacks = 100,
      maxStacks = 200,
      baseLocation = worldCenter,
      randomLocationScale = worldBounds.x * 0.5,
      randomLocationExponent = 0.95
   )

   simulation.spawnItems(
      itemConfigKey = "boulder",
      minStacks = 50,
      maxStacks = 60,
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
      canBePickedUp: Boolean = false,
      canBeDropped: Boolean = true,
      maxHp: Int = 100,
      canBeEquipped: Boolean = false,
      canBeDamagedByItem: String? = null,
      spawnItemOnDestruction: String? = null,
      spawnMinStacks: Int = 1,
      spawnMaxStacks: Int = 1,
      spawnMinAmountPerStack: Int = 1,
      spawnMaxAmountPerStack: Int = 1,
      maxStackSize: Int? = null,
      spriteBaseScale: Vector2 = Vector2.one,
      spriteBaseOffset: Vector2 = Vector2.zero,
      craftingCost: List<ItemCollectionEntry>? = null,
      equippedCompositeAnimation: CompositeAnimation? = null
   ): ItemConfig {
      val spriteConfigKey = key + "_sprite"

      configs.add(
         SpriteConfig(
            key = spriteConfigKey,
            textureUrl = textureUrl,
            baseOffset = spriteBaseOffset,
            baseScale = spriteBaseScale
         )
      )

      val itemConfig = ItemConfig(
         key = key,
         name = name,
         description = description,
         spriteConfigKey = spriteConfigKey,
         iconUrl = iconUrl ?: textureUrl,
         canBePickedUp = canBePickedUp,
         canBeDropped = canBeDropped,
         maxHp = maxHp,
         canBeEquipped = canBeEquipped,
         canBeDamagedByItem = canBeDamagedByItem,
         spawnItemOnDestruction = spawnItemOnDestruction,
         spawnMinStacks = spawnMinStacks,
         spawnMaxStacks = spawnMaxStacks,
         spawnMinAmountPerStack = spawnMinAmountPerStack,
         spawnMaxAmountPerStack = spawnMaxAmountPerStack,
         maxStackSize = maxStackSize,
         craftingCost = craftingCost?.let {
            ItemCollection(
               entries = it
            )
         },
         equippedCompositeAnimation = equippedCompositeAnimation
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
      canBePickedUp = true,
      spriteBaseScale = Vector2.uniform(0.25),
      maxStackSize = 50
   )

   addItemConfig(
      key = "wood",
      name = "Wood",
      description = "Useful resource for crafting",
      textureUrl = "assets/items/wood/wood.png",
      iconUrl = "assets/items/wood/wood.png",
      canBePickedUp = true,
      spriteBaseScale = Vector2(0.3, 0.3),
      maxStackSize = 50
   )

   addItemConfig(
      key = "axe",
      name = "Axe",
      description = "An axe for cutting down trees",
      textureUrl = "assets/items/axe/axe.png",
      iconUrl = "assets/items/axe/axe.png",
      canBePickedUp = true,
      canBeEquipped = true,
      spriteBaseScale = Vector2(0.25, 0.25),
      craftingCost = listOf(
         ItemCollectionEntry("wood", 25)
      ),
      maxStackSize = 1,
      equippedCompositeAnimation = CompositeAnimation(
         key = "weapon_blunt_waraxe",
         variant = "waraxe"
      )
   )

   addItemConfig(
      key = "hoe",
      name = "Hoe",
      description = "A hoe for preparing farm plots",
      textureUrl = "assets/items/axe/axe.png",
      iconUrl = "assets/items/axe/axe.png",
      canBePickedUp = true,
      canBeEquipped = true,
      spriteBaseScale = Vector2(0.15, 0.15),
      craftingCost = listOf(
         ItemCollectionEntry("wood", 25)
      ),
      maxStackSize = 1,
      equippedCompositeAnimation = CompositeAnimation(
         key = "tool_thrust",
         variant = "hoe"
      )
   )

   addItemConfig(
      key = "pickaxe",
      name = "Pickaxe",
      description = "A pickaxe for breaking apart stone",
      textureUrl = "assets/items/pickaxe/pickaxe.png",
      iconUrl = "assets/items/pickaxe/pickaxe.png",
      canBePickedUp = true,
      canBeEquipped = true,
      spriteBaseScale = Vector2(0.15, 0.15),
      craftingCost = listOf(
         ItemCollectionEntry("wood", 75)
      ),
      maxStackSize = 1,
      equippedCompositeAnimation = CompositeAnimation(
         key = "weapon_polearm_halberd",
         variant = "halberd"
      )
   )

   addItemConfig(
      key = "tree",
      name = "Tree",
      description = "A tree",
      textureUrl = "assets/items/tree/tree.png",
      iconUrl = "assets/items/tree/tree.png",
      canBeDamagedByItem = "axe",
      spriteBaseScale = Vector2.uniform(0.8),
      spriteBaseOffset = Vector2(0.0, -70.0),
      spawnItemOnDestruction = "wood",
      maxStackSize = 1,
      spawnMinStacks = 1,
      spawnMaxStacks = 3,
      spawnMinAmountPerStack = 10,
      spawnMaxAmountPerStack = 30
   )

   addItemConfig(
      key = "boulder",
      name = "Boulder",
      description = "A boulder",
      textureUrl = "assets/items/boulder/boulder.png",
      iconUrl = "assets/items/boulder/boulder.png",
      canBeDamagedByItem = "pickaxe",
      spawnItemOnDestruction = "stone",
      maxStackSize = 1,
      spawnMinStacks = 1,
      spawnMaxStacks = 3,
      spawnMinAmountPerStack = 5,
      spawnMaxAmountPerStack = 15,
      spriteBaseScale = Vector2.uniform(0.6)
   )

   addItemConfig(
      key = "house",
      name = "House",
      description = "A house",
      textureUrl = "assets/items/house/house.png",
      iconUrl = "assets/items/house/house.png",
      craftingCost = listOf(
         ItemCollectionEntry("wood", 200),
         ItemCollectionEntry("stone", 75)
      ),
      spriteBaseScale = Vector2.uniform(0.6),
      maxStackSize = 1
   )
}

private fun addCharacterConfigs(configs: MutableList<Config>) {
   fun buildSpriteConfigForCharacter(
      key: String,
      textureBaseName: String
   ): SpriteConfig {
      return SpriteConfig(
         textureUrl = "assets/liberated-pixel-cup-characters/spritesheets/body/bodies/male/universal.png",//  "assets/characters/$textureBaseName.png",
         atlasUrl = "assets/liberated-pixel-cup-characters/atlases/animations-universal.json",
         animationsUrl = "assets/liberated-pixel-cup-characters/animations/animations-universal.json",
         key = key
      )
   }

   val includedSkinColors = listOf(
      "light",
      "amber",
      "olive",
//      "bronze",
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
      eyes = listOf(RegisteredCompositeAnimation(
         key = "eyes",
         includedVariants = listOf("blue", "brown", "green")
      )),
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
            key = "shadow",
            includedVariants = listOf("shadow")
         ),
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
