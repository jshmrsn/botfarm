package botfarm.game.setup

import botfarm.common.SpriteConfig
import botfarm.engine.simulation.Config
import botfarm.game.components.CompositeAnimationSelection
import botfarm.game.config.*
import botfarmshared.game.apidata.ItemCollection
import botfarmshared.game.apidata.ItemCollectionEntry
import botfarmshared.misc.RandomConfig
import botfarmshared.misc.Vector2

fun addItemConfigs(configs: MutableList<Config>) {
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
      damageableConfig: DamageableConfig? = null,
      storableConfig: StorableConfig? = null,
      craftableConfig: CraftableConfig? = null,
      equippableConfig: EquippableConfig? = null,
      spawnItemOnKillConfig: SpawnItemOnKillConfig? = null, // tree spawns wood when cut down
      growerConfig: GrowerConfig? = null, // farm plots receive and grow carrot seeds
      growableConfig: GrowableConfig? = null, // carrot seeds grow into carrots
      spawnItemOnUseConfig: SpawnItemOnUseConfig? = null, // hoe spawns farm plots
      collisionConfig: CollisionConfig? = if (storableConfig == null) {
         CollisionConfig(
            width = 1,
            height = 1
         )
      } else {
         null
      }
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
         damageableConfig = damageableConfig,
         storableConfig = storableConfig,
         equippableConfig = equippableConfig,
         craftableConfig = craftableConfig,
         spawnItemOnKillConfig = spawnItemOnKillConfig,
         growableConfig = growableConfig,
         growerConfig = growerConfig,
         spawnItemOnUseConfig = spawnItemOnUseConfig,
         collisionConfig = collisionConfig,
         useCustomAnimationBaseName = useCustomAnimationBaseName
      )

      configs.add(itemConfig)

      return itemConfig
   }

   addItemConfig(
      key = "stone",
      name = "Stone",
      description = "Useful resource for crafting",
      textureUrl = "/assets/items/stone/stone_ground.png",
      iconUrl = "/assets/items/stone/stone_icon.png",
      spriteBaseScale = Vector2.uniform(0.25),
      storableConfig = StorableConfig(
         maxStackSize = 50
      )
   )

   addItemConfig(
      key = "wood",
      name = "Wood",
      description = "Useful resource for crafting",
      textureUrl = "/assets/items/wood/wood.png",
      iconUrl = "/assets/items/wood/wood.png",
      spriteBaseScale = Vector2(0.3, 0.3),
      storableConfig = StorableConfig(
         maxStackSize = 50
      )
   )

   addItemConfig(
      key = "axe",
      name = "Axe",
      description = "An axe for cutting down trees",
      textureUrl = "/assets/items/axe/axe.png",
      iconUrl = "/assets/items/axe/axe.png",
      useCustomAnimationBaseName = "slash",
      storableConfig = StorableConfig(
         maxStackSize = 1
      ),
      equippableConfig = EquippableConfig(
         equippedCompositeAnimation = CompositeAnimationSelection(
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
      textureUrl = "/assets/items/hoe/hoe.png",
      iconUrl = "/assets/items/hoe/hoe.png",
      spriteBaseScale = Vector2(0.25, 0.25),
      useCustomAnimationBaseName = "thrust",
      storableConfig = StorableConfig(
         maxStackSize = 1
      ),
      equippableConfig = EquippableConfig(
         equippedCompositeAnimation = CompositeAnimationSelection(
            key = "tool_thrust",
            variant = "hoe"
         ),
         equipmentSlot = EquipmentSlot.Tool
      ),
      craftableConfig = CraftableConfig(
         craftingCost = ItemCollection(listOf(ItemCollectionEntry("wood", 25)))
      ),
      spawnItemOnUseConfig = SpawnItemOnUseConfig(
         spawnItemConfigKey = "farm-plot"
      )
   )

   addItemConfig(
      key = "pickaxe",
      name = "Pickaxe",
      description = "A pickaxe for breaking apart boulders",
      textureUrl = "/assets/items/pickaxe/pickaxe.png",
      iconUrl = "/assets/items/pickaxe/pickaxe.png",
      spriteBaseScale = Vector2(0.15, 0.15),
      useCustomAnimationBaseName = "slash",
      storableConfig = StorableConfig(
         maxStackSize = 1
      ),
      craftableConfig = CraftableConfig(
         craftingCost = ItemCollection(listOf(ItemCollectionEntry("wood", 75)))
      ),
      equippableConfig = EquippableConfig(
         equippedCompositeAnimation = CompositeAnimationSelection(
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
      textureUrl = "/assets/items/farm-plot/farm-plot.png",
      iconUrl = "/assets/items/farm-plot/farm-plot.png",
      spriteBaseScale = Vector2.uniform(0.85) * Vector2(1.0, 0.9),
      spriteBaseOffset = Vector2(0.0, 0.0),
      growerConfig = GrowerConfig(
         canReceiveGrowableItemConfigKeys = listOf(
            "tomato-seeds"
         )
      ),
      collisionConfig = CollisionConfig(
         width = 3,
         height = 3,
         flags = listOf(CollisionFlag.Placement)
      )
   )

   addItemConfig(
      key = "tomato-seeds",
      name = "Tomato Seeds",
      description = "Tomato seeds",
      textureUrl = "/assets/items/tomato-seeds/tomato-seeds.png",
      iconUrl = "/assets/items/tomato-seeds/tomato-seeds.png",
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
      textureUrl = "/assets/items/tomato/tomato.png",
      iconUrl = "/assets/items/tomato/tomato.png",
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
      textureUrl = "/assets/items/tree/tree.png",
      iconUrl = "/assets/items/tree/tree.png",
      spriteBaseScale = Vector2.uniform(0.7),
      spriteBaseOffset = Vector2(0.0, -38.0),
      spawnItemOnKillConfig = SpawnItemOnKillConfig(
         spawnItemConfigKey = "wood",
         quantity = RandomItemQuantity.stacksOfAmount(
            stackCount = RandomConfig.range(1, 3),
            amountPerStack = RandomConfig.range(10, 30)
         )
      ),
      damageableConfig = DamageableConfig(
         damageableByEquippedToolItemConfigKey = "axe",
         maxHp = 100
      ),
      collisionConfig = CollisionConfig(
         width = 3,
         height = 4,
         collisionOffset = Vector2(0.0, -20.0),
         overlapHeight = 30.0
      )
   )


   addItemConfig(
      key = "boulder",
      name = "Boulder",
      description = "A boulder",
      textureUrl = "/assets/items/boulder/boulder.png",
      iconUrl = "/assets/items/boulder/boulder.png",
      spawnItemOnKillConfig = SpawnItemOnKillConfig(
         spawnItemConfigKey = "stone",
         quantity = RandomItemQuantity.stacksOfAmount(
            stackCount = RandomConfig.range(1, 3),
            amountPerStack = RandomConfig.range(5, 15)
         )
      ),
      spriteBaseScale = Vector2.uniform(0.35),
      damageableConfig = DamageableConfig(
         damageableByEquippedToolItemConfigKey = "pickaxe",
         maxHp = 100
      ),
      collisionConfig = CollisionConfig(
         width = 2,
         height = 2
      )
   )

   addItemConfig(
      key = "house",
      name = "House",
      description = "A house",
      textureUrl = "/assets/items/house/house.png",
      iconUrl = "/assets/items/house/house.png",
      spriteBaseScale = Vector2.uniform(0.8),
      craftableConfig = CraftableConfig(
         craftingCost = ItemCollection(
            listOf(
               ItemCollectionEntry("wood", 150),
               ItemCollectionEntry("stone", 50)
            )
         )
      )
   )
}