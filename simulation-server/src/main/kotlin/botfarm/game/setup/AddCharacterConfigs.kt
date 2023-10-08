package botfarm.game.setup

import botfarm.common.SpriteConfig
import botfarm.engine.simulation.Config
import botfarm.game.components.CompositeAnimationRegistryConfig
import botfarm.game.components.RegisteredCompositeAnimation
import botfarm.game.config.*

fun addCharacterConfigs(configs: MutableList<Config>) {
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
      noses = listOf("head_nose_straight"),
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
            textureUrl = "/assets/liberated-pixel-cup-characters/spritesheets/$spriteSheetRelativePath",
            atlasUrl = "/assets/liberated-pixel-cup-characters/atlases/animations-$animationsConfigName.json",
            animationsUrl = "/assets/liberated-pixel-cup-characters/animations/animations-$animationsConfigName.json",
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

