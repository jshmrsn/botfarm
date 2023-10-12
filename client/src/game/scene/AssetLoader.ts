import {GameSimulationScene} from "./GameSimulationScene";
import {DynamicState} from "../ui/DynamicState";
import {GameSimulation} from "../simulation/GameSimulation";
import {
  CharacterBodySelectionsConfig,
  CompositeAnimationRegistryConfig,
  RegisteredCompositeAnimation,
  SpriteAnimation,
  SpriteConfig
} from "../../common/common";
import {
  AnimationConfig,
  AnimationsConfig,
  RawSheetDefinition,
  RawSheetDefinitionLayer,
  SheetDefinition,
  SheetDefinitionLayer
} from "./AnimationConfig";
import Phaser from "phaser";
import {throwError} from "../../misc/utils";
import {Vector2} from "../../misc/Vector2";
import {ItemConfig} from "../simulation/ItemComponentData";
import {CharacterBodySelections} from "../simulation/CharacterComponentData";
import LoaderPlugin = Phaser.Loader.LoaderPlugin;


export class AssetLoader {
  readonly scene: GameSimulationScene
  readonly dynamicState: DynamicState
  readonly simulation: GameSimulation

  readonly compositeAnimationRegistryConfig: CompositeAnimationRegistryConfig
  readonly characterBodySelectionsConfig: CharacterBodySelectionsConfig

  sheetDefinitionsByKey: Record<string, SheetDefinition> = {}


  private get load(): LoaderPlugin {
    return this.scene.load
  }

  constructor(scene: GameSimulationScene) {
    this.scene = scene
    this.dynamicState = scene.dynamicState
    const simulation = scene.simulation;
    this.simulation = simulation

    this.compositeAnimationRegistryConfig = simulation.getConfig<CompositeAnimationRegistryConfig>("composite-animation-registry", "CompositeAnimationRegistryConfig")
    this.characterBodySelectionsConfig = simulation.getConfig<CharacterBodySelectionsConfig>("character-body-selections-config", "CharacterBodySelectionsConfig")
  }

  readonly onLoadCompleteFunctions: { (): void } [] = []

  onLoadComplete(callback: () => void) {
    this.onLoadCompleteFunctions.push(callback)
  }

  preload() {
    const scene = this.scene
    
    const uiCamera = scene.cameras.add(0, 0, scene.scale.width, scene.scale.height)
    scene.uiCamera = uiCamera
    const mainCamera = scene.cameras.main
    scene.mainCamera = mainCamera

    let screenWidth = uiCamera.width
    let screenHeight = uiCamera.height

    let progressBar = scene.add.graphics()
    let progressBox = scene.add.graphics()
    progressBox.fillStyle(0x222222, 0.2)
    const progressBoxWidth = 320
    const progressBoxHeight = 20
    const progressBoxX = screenWidth / 2 - progressBoxWidth / 2
    const progressBoxY = screenHeight / 2 - progressBoxHeight / 2
    progressBox.fillRect(progressBoxX, progressBoxY, progressBoxWidth, progressBoxHeight)

    scene.load.image("background-grass", "/assets/environment/grass1.png")

    const loadingText = scene.make.text({
      x: screenWidth / 2,
      y: screenHeight / 2 - 30,
      text: 'Loading...',
      style: {
        font: '20px',
        color: 'black'
      },
      alpha: 0.5
    })
    loadingText.setOrigin(0.5, 0.5)
    mainCamera.ignore(loadingText)

    let assetText = scene.make.text({
      x: screenWidth / 2,
      y: screenHeight / 2 + 50,
      text: '',
      style: {
        font: '18px ',
        color: 'black'
      },
      alpha: 0.25
    })
    assetText.setOrigin(0.5, 0.5)
    mainCamera.ignore(assetText)

    scene.load.on('progress', (value: number) => {
      // joshr: Avoid errors modifying text if an error during preload causes simulation scene to be destroyed
      // before progress callbacks stop
      if (scene.dynamicState.scene === scene) {
        // percentText.setText((value * 100).toFixed(0) + '%')
        progressBar.clear()
        progressBar.fillStyle(0xffffff, 1)

        const progressBarWidth = progressBoxWidth - 2
        const progressBarHeight = progressBoxHeight - 2
        const progressBarX = screenWidth / 2 - progressBarWidth / 2
        const progressBarY = screenHeight / 2 - progressBarHeight / 2

        progressBar.fillRect(progressBarX, progressBarY, progressBarWidth * value, progressBarHeight)
      }
    })

    scene.load.on('fileprogress', (file: any) => {
      if (scene.dynamicState.scene === scene) {
        assetText.setText('Loading asset: ' + file.key)
      }
    })

    scene.load.on('complete', () => {
      progressBar.destroy()
      progressBox.destroy()
      // loadingText.destroy()
      // percentText.destroy()
      assetText.destroy()
      for (let onLoadCompleteFunction of this.onLoadCompleteFunctions) {
        onLoadCompleteFunction()
      }
    })

    scene.load.image("black-square", "/assets/misc/black-square.png")
    scene.load.image("fog-of-war", "/assets/misc/fog-of-war.png")
    scene.load.image("circle", "/assets/misc/circle.png")
    scene.load.image("ring", "/assets/misc/ring.png")
    scene.load.image("character-shadow", "/assets/misc/character-shadow.png")

    this.preloadJson(() => {
      this.preloadSprites()
    })
  }

  getProfileIconLayerUrlsForBodySelections(characterBodySelections: CharacterBodySelections): string[] {
    const sheetDefinitionsByKey = this.sheetDefinitionsByKey
    const head = characterBodySelections.head
    const skinColor = characterBodySelections.skinColor
    const hair = characterBodySelections.hair

    const category = characterBodySelections.bodyType

    const result: string[] = []

    function addForCompositeAnimation(
      spriteDefinitionKey: string,
      variant: string
    ) {
      const sheetDefinition = sheetDefinitionsByKey[spriteDefinitionKey]

      if (sheetDefinition) {
        const layers = sheetDefinition.layers
        const layer = layers[layers.length - 1]

        const partialPathsByCategory = layer.texturePartialPathsByCategoryByVariant[variant]
        const partialPath = partialPathsByCategory[category] ?? partialPathsByCategory["male"]
        const profileIconLayerPath = "/assets/liberated-pixel-cup-characters/profile-icons/" + partialPath + variant + ".png"
        result.push(profileIconLayerPath)
      }
    }

    addForCompositeAnimation(head, skinColor)

    if (hair != null) {
      addForCompositeAnimation(hair.key, hair.variant)
    }

    return result
  }



  readonly sheetDefinitionsUrl = "/assets/liberated-pixel-cup-characters/sheet-definitions.json"
  readonly universalAnimationsUrl = "/assets/liberated-pixel-cup-characters/animations/animations-universal.json"
  readonly universalAtlasUrl = "/assets/liberated-pixel-cup-characters/atlases/animations-universal.json"

  readonly atlasUrlsByCustomAnimationName: Record<string, string> = {
    "slash_oversize": "/assets/liberated-pixel-cup-characters/atlases/animations-slash-oversize.json",
    "thrust_oversize": "/assets/liberated-pixel-cup-characters/atlases/animations-thrust-oversize.json",
    "slash_128": "/assets/liberated-pixel-cup-characters/atlases/animations-slash-128.json",
    "walk_128": "/assets/liberated-pixel-cup-characters/atlases/animations-slash-oversize.json" // todo
    // "slash_128": "assets/liberated-pixel-cup-characters/animations/animations-slash-128.json",
    // "walk_128": "assets/liberated-pixel-cup-characters/animations/animations-walk-128.json"
  }


  readonly animationsUrlsByCustomAnimationName: Record<string, string> = {
    "slash_oversize": "/assets/liberated-pixel-cup-characters/animations/animations-slash-oversize.json",
    "thrust_oversize": "/assets/liberated-pixel-cup-characters/animations/animations-thrust-oversize.json",
    "slash_128": "/assets/liberated-pixel-cup-characters/animations/animations-slash-128.json", // todo
    "walk_128": "/assets/liberated-pixel-cup-characters/animations/animations-slash-oversize.json" // todo
    // "slash_128": "assets/liberated-pixel-cup-characters/animations/animations-slash-128.json",
    // "walk_128": "assets/liberated-pixel-cup-characters/animations/animations-walk-128.json"
  }

  gatherJsonsToLoad(addJsonToLoad: (jsonUrl: string) => void) {
    addJsonToLoad(this.sheetDefinitionsUrl)

    addJsonToLoad(this.universalAnimationsUrl)
    for (let customAnimationName in this.animationsUrlsByCustomAnimationName) {
      addJsonToLoad(this.animationsUrlsByCustomAnimationName[customAnimationName])
    }
    // addJsonToLoad(this.universalAtlasUrl)

    this.simulation.configs.forEach(config => {
      const configType = config.type

      if (configType === "SpriteConfig") {
        const spriteConfig: SpriteConfig = config as any
        if (spriteConfig.animationsUrl) {
          addJsonToLoad(spriteConfig.animationsUrl)
        }
      }
    })
  }

  preloadJson(next: () => void) {
    const load = this.load
    let waitingForJsonUrls: Record<string, boolean> = {}

    function addJsonToLoad(jsonUrl: string) {
      waitingForJsonUrls[jsonUrl] = true
    }

    this.gatherJsonsToLoad(addJsonToLoad)

    let jsonUrlsToLoad: string[] = Object.keys(waitingForJsonUrls)

    if (jsonUrlsToLoad.length === 0) {
      next()
    } else {
      for (let jsonToLoadUrl of jsonUrlsToLoad) {
        const loadingName = jsonToLoadUrl

        load.json(loadingName, jsonToLoadUrl)
        // https://phaser.discourse.group/t/preloading-json-file-before-the-assets/9690
        load.on("filecomplete-json-" + loadingName, () => {
          waitingForJsonUrls[jsonToLoadUrl] = false

          let stillWaiting = false
          for (let waitingForJsonUrlsKey in waitingForJsonUrls) {
            if (waitingForJsonUrls[waitingForJsonUrlsKey]) {
              stillWaiting = true
              break
            }
          }

          if (!stillWaiting) {
            next()
          }
        })
      }
    }
  }

  loadSpriteConfig(textureKey: string, spriteConfig: SpriteConfig) {
    const anims = this.scene.anims

    if (spriteConfig.atlasUrl != null) {
      this.load.atlas(
        textureKey,
        spriteConfig.textureUrl,
        spriteConfig.atlasUrl
      )
    } else {
      this.load.image(textureKey, spriteConfig.textureUrl)
    }


    let animations = spriteConfig.animations

    if (spriteConfig.animationsUrl != null && spriteConfig.animationsUrl.length > 0) {
      const animationsConfig = this.scene.cache.json.get(spriteConfig.animationsUrl) as AnimationsConfig
      // console.log("spriteConfig.animationsUrl", spriteConfig.animationsUrl)
      // console.log("animationsConfig", animationsConfig)

      animations = animationsConfig.animations.map(animationConfig => {
        const result: SpriteAnimation = {
          keySuffix: null,
          frameRate: animationConfig.frameRate,
          repeat: -1,
          framesPrefix: animationConfig.name + ".",
          framesStart: 0,
          framesEnd: animationConfig.numberOfFrames - 1,
          framesZeroPad: 3,
          duration: undefined,
          singleFrame: null
        }

        return result
      })
    }

    // console.log("animations", animations)
    if (animations != null) {
      this.scene.onCreate(() => {
        animations.forEach(animation => {
          let animationKeySuffix: string
          let frames: Phaser.Types.Animations.AnimationFrame[]

          if (animation.singleFrame != null) {
            animationKeySuffix = animation.singleFrame

            frames = [{
              key: textureKey,
              frame: animation.singleFrame,
              duration: 1,
              visible: true
            }]
          } else if (animation.framesPrefix != null) {
            animationKeySuffix = animation.framesPrefix.replace(".", "")

            frames = anims.generateFrameNames(textureKey, {
              prefix: animation.framesPrefix,
              start: animation.framesStart,
              end: animation.framesEnd,
              zeroPad: animation.framesZeroPad
            })
          } else {
            throwError("No animation frames specified")
          }

          const animationKey = textureKey + "_" + animationKeySuffix

          anims.create({
            key: animationKey,
            frames: frames,
            frameRate: animation.frameRate,
            repeat: animation.repeat,
            duration: animation.duration
          })
        })
      })
    }
  }

  loadCompositeAnimation(compositeAnimationKey: string, includedVariants: string[]) {
    const existing = this.sheetDefinitionsByKey[compositeAnimationKey]

    if (existing != null) {
      for (let includedVariant of includedVariants) {
        if (!existing.includedVariants.includes(includedVariant)) {
          throw new Error("loadCompositeAnimation called for the same compositeAnimationKey, but the recent call included a variant which wasn't in the first call. This isn't currently supported (to support it, existing sheetDefinition needs to be updated with new variants)")
        }
      }

      return
    }

    const rawSheetDefinitions: Record<string, RawSheetDefinition> = this.scene.cache.json.get(this.sheetDefinitionsUrl)

    const rawSheetDefinition = rawSheetDefinitions[compositeAnimationKey]

    if (rawSheetDefinition == null) {
      throw new Error("Can't find sheet definition for registered key: " + compositeAnimationKey)
    }


    const rawLayers: RawSheetDefinitionLayer[] = []
    let layerIndexToCheck = 1
    while (true) {
      let layerKey = "layer_" + layerIndexToCheck
      const rawLayer = (rawSheetDefinition as any as Record<string, RawSheetDefinitionLayer>)[layerKey]
      if (rawLayer == null) {
        break
      }
      rawLayers.push(rawLayer)
      ++layerIndexToCheck
    }


    // Example
    // key: "arms_armour"
    // category: "female"
    // partial path: arms/armour/plate/female/
    // variant: "steel"
    // actual asset path: assets/liberated-pixel-cup-characters/spritesheets/arms/armour/plate/female/steel.png

    const includedCategories = this.compositeAnimationRegistryConfig.includedCategories

    const layers: SheetDefinitionLayer[] = rawLayers.map((rawLayer, layerIndex) => {
      const partialPathsByIncludedCategory: Record<string, string> = {}

      const animationsUrl = rawLayer.custom_animation != null
        ? this.animationsUrlsByCustomAnimationName[rawLayer.custom_animation]
        : this.universalAnimationsUrl

      const atlasUrl = rawLayer.custom_animation != null
        ? this.atlasUrlsByCustomAnimationName[rawLayer.custom_animation]
        : this.universalAtlasUrl

      const animationsConfig = this.scene.cache.json.get(animationsUrl) as AnimationsConfig

      for (let includedCategory of includedCategories) {
        const partialLayerPathForCategory = (rawLayer as any as Record<string, string>)[includedCategory]

        if (partialLayerPathForCategory) {
          partialPathsByIncludedCategory[includedCategory] = partialLayerPathForCategory
        }
      }

      const uniqueLayerPartialPaths: string[] = []

      for (let category in partialPathsByIncludedCategory) {
        const partialPath = partialPathsByIncludedCategory[category]
        if (!uniqueLayerPartialPaths.includes(partialPath)) {
          const uniqueLayerPartialPathIndex = uniqueLayerPartialPaths.length
          uniqueLayerPartialPaths.push(partialPath)

          for (let includedVariant of includedVariants) {
            const layerTextureUrl = "/assets/liberated-pixel-cup-characters/spritesheets/" + partialPath + includedVariant + ".png"
            const layerTextureKey = compositeAnimationKey + "_" + includedVariant + "_" + layerIndex + "_" + uniqueLayerPartialPathIndex

            if (!animationsUrl) {
              throw new Error(`Can't find animation url for custom animation name: ${rawLayer.custom_animation}, for sheet definition ${compositeAnimationKey} layer ${layerIndex}`)
            }

            this.loadSpriteConfig(layerTextureKey, {
              baseScale: Vector2.one,
              baseOffset: Vector2.zero,
              textureUrl: layerTextureUrl,
              atlasUrl: atlasUrl,
              animationsUrl: animationsUrl,
              animations: [],
              type: "SpriteConfig",
              key: layerTextureKey,
              depthOffset: 0
            })
          }
        }
      }

      const textureKeysByCategoryByVariant: Record<string, Record<string, string>> = {}
      const texturePartialPathsByCategoryByVariant: Record<string, Record<string, string>> = {}

      for (let includedVariant of includedVariants) {
        const textureKeysByCategory: Record<string, string> = {}
        const texturePartialPathsByCategory: Record<string, string> = {}

        for (let includedCategory in partialPathsByIncludedCategory) {
          const partialPath = partialPathsByIncludedCategory[includedCategory]

          if (partialPath != null) {
            const uniqueLayerIndex = uniqueLayerPartialPaths.indexOf(partialPath)
            const key = compositeAnimationKey + "_" + includedVariant + "_" + layerIndex + "_" + uniqueLayerIndex
            textureKeysByCategory[includedCategory] = key
            texturePartialPathsByCategory[includedCategory] = partialPath
          }
        }

        textureKeysByCategoryByVariant[includedVariant] = textureKeysByCategory
        texturePartialPathsByCategoryByVariant[includedVariant] = texturePartialPathsByCategory
      }

      const animationConfigsByName: Record<string, AnimationConfig> = {}
      for (let animationConfig of animationsConfig.animations) {
        animationConfigsByName[animationConfig.name] = animationConfig
      }

      const layer: SheetDefinitionLayer = {
        zPos: rawLayer.zPos,
        custom_animation: rawLayer.custom_animation ?? null,
        textureKeysByCategoryByVariant: textureKeysByCategoryByVariant,
        texturePartialPathsByCategoryByVariant: texturePartialPathsByCategoryByVariant,
        animationsConfig: animationsConfig,
        animationConfigsByName: animationConfigsByName
      }

      return layer
    })

    const sheetDefinition: SheetDefinition = {
      name: rawSheetDefinition.name,
      type_name: rawSheetDefinition.type_name,
      includedVariants: includedVariants,
      match_body_color: rawSheetDefinition.match_body_color || false,
      layers: layers
    }

    this.sheetDefinitionsByKey[compositeAnimationKey] = sheetDefinition
  }

  preloadSprites() {
    const registerCompositionAnimations = (options: RegisteredCompositeAnimation[]) => {
      for (let registeredCompositeAnimation of options) {
        const compositeAnimationKey = registeredCompositeAnimation.key
        const includedVariants = registeredCompositeAnimation.includedVariants
        this.loadCompositeAnimation(compositeAnimationKey, includedVariants)
      }
    }

    registerCompositionAnimations(this.compositeAnimationRegistryConfig.registeredCompositeAnimations)

    const skinColors = this.characterBodySelectionsConfig.skinColors
    const registerSkinColorVariantOptions = (options: string[]) => {
      for (let compositeAnimationKey of options) {
        this.loadCompositeAnimation(compositeAnimationKey, skinColors)
      }
    }

    registerSkinColorVariantOptions(this.characterBodySelectionsConfig.bodies)
    registerSkinColorVariantOptions(this.characterBodySelectionsConfig.heads)
    registerSkinColorVariantOptions(this.characterBodySelectionsConfig.noses)
    registerCompositionAnimations(this.characterBodySelectionsConfig.eyes)
    registerSkinColorVariantOptions(this.characterBodySelectionsConfig.wrinkles)
    registerCompositionAnimations(this.characterBodySelectionsConfig.hairs)

    this.simulation.configs.forEach(config => {
      const configKey = config.key
      const configType = config.type

      if (configType === "SpriteConfig") {
        const spriteConfig: SpriteConfig = config as any
        const textureKey = configKey

        this.loadSpriteConfig(textureKey, spriteConfig)
      } else if (configType === "ItemConfig") {
        const itemConfig: ItemConfig = config as any

        if (itemConfig.equippableConfig != null && itemConfig.equippableConfig.equippedCompositeAnimation != null) {
          this.loadCompositeAnimation(itemConfig.equippableConfig.equippedCompositeAnimation.key, [itemConfig.equippableConfig.equippedCompositeAnimation.variant])
        }
      }
    })
  }
}

