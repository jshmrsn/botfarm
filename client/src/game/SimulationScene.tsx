import Phaser from "phaser";
import {Config} from "../simulation/EntityData";

import {getUnixTimeSeconds, throwError} from "../misc/utils";
import {DynamicState} from "../components/SimulationComponent";
import {Simulation} from "../simulation/Simulation";
import {Vector2} from "../misc/Vector2";
import {Vector2Animation} from "../misc/Vector2Animation";
import {
  CharacterBodySelectionsConfig,
  CompositeAnimationRegistryConfig,
  RegisteredCompositeAnimation,
  SpriteAnimation,
  SpriteConfig
} from "../common/common";
import {RenderContext} from "../common/RenderContext";
import {CharacterComponentData} from "./CharacterComponentData";
import {ItemComponentData, ItemConfig} from "./ItemComponentData";
import {Entity} from "../simulation/Entity";
import {
  PositionComponentData,
  resolveEntityPositionForCurrentTime,
  resolveEntityPositionForTime, resolvePositionForCurrentTime,
  resolvePositionForTime
} from "../common/PositionComponentData";
import {AgentComponentData} from "./agentComponentData";
import {UserControlledComponentData} from "./userControlledComponentData";
import {IconHandGrab, IconTool} from "@tabler/icons-react";
import {CompositeAnimation} from "./CompositeAnimation";
import Layer = Phaser.GameObjects.Layer;

interface AnimationConfig {
  name: string
  spriteCellRowIndex: number
  numberOfFrames: number
  frameRate: number
}

interface AnimationsConfig {
  spriteCellWidth: number
  spriteCellHeight: number
  animations: AnimationConfig[]
}

interface RawSheetDefinitionLayer {
  zPos: number
  custom_animation?: string
  // category name: string
}

interface RawSheetDefinition {
  name: string
  type_name: string,
  variants: string[]
  match_body_color?: boolean
  //layer_N?: SheetDefinitionLayer
}

interface SheetDefinitionLayer {
  zPos: number
  custom_animation: string | null
  textureKeysByCategoryByVariant: Record<string, Record<string, string>>
  animationsConfig: AnimationsConfig
  animationConfigsByName: Record<string, AnimationConfig>
}

interface SheetDefinition {
  name: string
  type_name: string,
  includedVariants: string[]
  match_body_color: boolean
  layers: SheetDefinitionLayer[]
}

export interface SimulationSceneContext {
  dynamicState: DynamicState,
  sendWebSocketMessage: (type: string, data: object) => void
  setSelectedEntityId: (entityId: string | null) => void
  closePanels: () => void
  showHelpPanel: () => void
}

export enum AutoInteractActionType {
  Clear,
  SelectEntity,
  Interact
}

export interface AutoInteractAction {
  type: AutoInteractActionType
  actionTitle: string
  targetEntity?: Entity
  actionIcon: JSX.Element | null
}

export class SimulationScene extends Phaser.Scene {
  simulationId: string
  dynamicState: DynamicState
  simulation: Simulation

  focusChatTextAreaKey: Phaser.Input.Keyboard.Key | undefined = undefined

  private cursorKeys: Phaser.Types.Input.Keyboard.CursorKeys | null | undefined = null

  private onCreateFunctions: { (): void } [] = []
  private onLoadCompleteFunctions: { (): void } [] = []

  private simulationContext: SimulationSceneContext;
  readonly userId: string

  uiCamera!: Phaser.Cameras.Scene2D.Camera
  mainCamera!: Phaser.Cameras.Scene2D.Camera

  backgroundLayer!: Layer
  observationRingsLayer!: Layer
  navigationPathLayer!: Layer
  mainLayer!: Layer
  highlightRingLayer!: Layer
  characterNameLayer!: Layer
  chatBubbleLayer!: Layer

  private lastClickTime: number = -1.0
  readonly renderContext = new RenderContext(this)
  readonly compositeAnimationRegistryConfig: CompositeAnimationRegistryConfig
  readonly characterBodySelectionsConfig: CharacterBodySelectionsConfig
  private backgroundGrass!: Phaser.GameObjects.TileSprite

  private readonly worldBounds = new Vector2(10000.0, 10000.0)

  constructor(
    simulation: Simulation,
    context: SimulationSceneContext
  ) {
    super("SimulationPhaserScene");

    this.simulation = simulation
    this.userId = context.dynamicState.userId
    this.simulationId = simulation.simulationId

    this.simulationContext = context
    this.dynamicState = context.dynamicState
    this.compositeAnimationRegistryConfig = this.simulation.getConfig<CompositeAnimationRegistryConfig>("composite-animation-registry", "CompositeAnimationRegistryConfig")
    this.characterBodySelectionsConfig = this.simulation.getConfig<CharacterBodySelectionsConfig>("character-body-selections-config", "CharacterBodySelectionsConfig")
  }

  init() {
  }

  onLoadComplete(callback: () => void) {
    this.onLoadCompleteFunctions.push(callback)
  }

  preload() {
    const uiCamera = this.cameras.add(0, 0, this.scale.width, this.scale.height);
    this.uiCamera = uiCamera
    const mainCamera = this.cameras.main;
    this.mainCamera = mainCamera

    var screenWidth = uiCamera.width;
    var screenHeight = uiCamera.height;

    var progressBar = this.add.graphics();
    var progressBox = this.add.graphics();
    progressBox.fillStyle(0x222222, 0.8);
    const progressBoxWidth = 320
    const progressBoxHeight = 50
    const progressBoxX = screenWidth / 2 - progressBoxWidth / 2
    const progressBoxY = screenHeight / 2 - progressBoxHeight / 2
    progressBox.fillRect(progressBoxX, progressBoxY, progressBoxWidth, progressBoxHeight);

    this.load.image("background-grass", "assets/environment/grass1.png")

    var loadingText = this.make.text({
      x: screenWidth / 2,
      y: screenHeight / 2 - 50,
      text: 'Loading...',
      style: {
        font: '20px monospace',
        color: '#ffffff'
      }
    });
    loadingText.setOrigin(0.5, 0.5);
    mainCamera.ignore(loadingText)

    var percentText = this.make.text({
      x: screenWidth / 2,
      y: screenHeight / 2,
      text: '0%',
      style: {
        font: '18px monospace',
        color: '#ffffff'
      }
    });
    percentText.setOrigin(0.5, 0.5);
    mainCamera.ignore(percentText)

    var assetText = this.make.text({
      x: screenWidth / 2,
      y: screenHeight / 2 + 50,
      text: '',
      style: {
        font: '18px monospace',
        color: '#ffffff'
      }
    });
    assetText.setOrigin(0.5, 0.5);
    mainCamera.ignore(assetText)

    this.load.on('progress', (value: number) => {
      // joshr: Avoid errors modifying text if an error during preload causes simulation scene to be destroyed
      // before progress callbacks stop
      if (this.dynamicState.phaserScene === this) {
        percentText.setText((value * 100).toFixed(0) + '%');
        progressBar.clear();
        progressBar.fillStyle(0xffffff, 1);

        const progressBarWidth = progressBoxWidth - 6
        const progressBarHeight = progressBoxHeight - 6
        const progressBarX = screenWidth / 2 - progressBarWidth / 2
        const progressBarY = screenHeight / 2 - progressBarHeight / 2

        progressBar.fillRect(progressBarX, progressBarY, progressBarWidth * value, progressBarHeight);
      }
    });

    this.load.on('fileprogress', (file: any) => {
      if (this.dynamicState.phaserScene === this) {
        assetText.setText('Loading asset: ' + file.key);
      }
    });

    this.load.on('complete', () => {
      progressBar.destroy();
      progressBox.destroy();
      loadingText.destroy();
      percentText.destroy();
      assetText.destroy();
      for (let onLoadCompleteFunction of this.onLoadCompleteFunctions) {
        onLoadCompleteFunction()
      }
    });

    this.load.image("circle", "assets/misc/circle.png");
    this.load.image("ring", "assets/misc/ring.png");

    this.preloadJson(() => {
      this.preloadSprites()
    })
  }

  readonly sheetDefinitionsUrl = "assets/liberated-pixel-cup-characters/sheet-definitions.json"
  readonly universalAnimationsUrl = "assets/liberated-pixel-cup-characters/animations/animations-universal.json"
  readonly universalAtlasUrl = "assets/liberated-pixel-cup-characters/atlases/animations-universal.json"

  readonly atlasUrlsByCustomAnimationName: Record<string, string> = {
    "slash_oversize": "assets/liberated-pixel-cup-characters/atlases/animations-slash-oversize.json",
    "thrust_oversize": "assets/liberated-pixel-cup-characters/atlases/animations-thrust-oversize.json",
    "slash_128": "assets/liberated-pixel-cup-characters/atlases/animations-slash-oversize.json", // todo
    "walk_128": "assets/liberated-pixel-cup-characters/atlases/animations-slash-oversize.json" // todo
    // "slash_128": "assets/liberated-pixel-cup-characters/animations/animations-slash-128.json",
    // "walk_128": "assets/liberated-pixel-cup-characters/animations/animations-walk-128.json"
  }


  readonly animationsUrlsByCustomAnimationName: Record<string, string> = {
    "slash_oversize": "assets/liberated-pixel-cup-characters/animations/animations-slash-oversize.json",
    "thrust_oversize": "assets/liberated-pixel-cup-characters/animations/animations-thrust-oversize.json",
    "slash_128": "assets/liberated-pixel-cup-characters/animations/animations-slash-oversize.json", // todo
    "walk_128": "assets/liberated-pixel-cup-characters/animations/animations-slash-oversize.json" // todo
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

        this.load.json(loadingName, jsonToLoadUrl)
        // https://phaser.discourse.group/t/preloading-json-file-before-the-assets/9690
        this.load.on("filecomplete-json-" + loadingName, () => {
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

  sheetDefinitionsByKey: Record<string, SheetDefinition> = {}

  loadSpriteConfig(textureKey: string, spriteConfig: SpriteConfig) {
    const anims = this.anims

    if (spriteConfig.atlasUrl != null) {
      this.load.atlas(
        textureKey,
        spriteConfig.textureUrl,
        spriteConfig.atlasUrl
      );
    } else {
      this.load.image(textureKey, spriteConfig.textureUrl)
    }


    let animations = spriteConfig.animations

    if (spriteConfig.animationsUrl != null && spriteConfig.animationsUrl.length > 0) {
      const animationsConfig = this.cache.json.get(spriteConfig.animationsUrl) as AnimationsConfig
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
      this.onCreateFunctions.push(() => {
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

    const rawSheetDefinitions: Record<string, RawSheetDefinition> = this.cache.json.get(this.sheetDefinitionsUrl)

    const rawSheetDefinition = rawSheetDefinitions[compositeAnimationKey]

    if (rawSheetDefinition == null) {
      throw new Error("Can't find sheet definition for registered key: " + compositeAnimationKey)
    }


    const rawLayers: RawSheetDefinitionLayer[] = []
    var layerIndexToCheck = 1
    while (true) {
      var layerKey = "layer_" + layerIndexToCheck
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

    const includedCategories = this.compositeAnimationRegistryConfig.includedCategories;

    const layers: SheetDefinitionLayer[] = rawLayers.map((rawLayer, layerIndex) => {
      const partialPathsByIncludedCategory: Record<string, string> = {}

      const animationsUrl = rawLayer.custom_animation != null
        ? this.animationsUrlsByCustomAnimationName[rawLayer.custom_animation]
        : this.universalAnimationsUrl

      const atlasUrl = rawLayer.custom_animation != null
        ? this.atlasUrlsByCustomAnimationName[rawLayer.custom_animation]
        : this.universalAtlasUrl

      const animationsConfig = this.cache.json.get(animationsUrl) as AnimationsConfig

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
            const layerTextureUrl = "assets/liberated-pixel-cup-characters/spritesheets/" + partialPath + includedVariant + ".png"
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
              key: layerTextureKey
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

        if (itemConfig.equippedCompositeAnimation != null) {
          this.loadCompositeAnimation(itemConfig.equippedCompositeAnimation.key, [itemConfig.equippedCompositeAnimation.variant])
        }
      }
    })
  }

  blurChatTextArea() {
    if (this.dynamicState.chatTextArea != null) {
      this.dynamicState.chatTextArea.blur()
    }
  }

  focusChatTextArea() {
    if (this.dynamicState.chatTextArea != null) {
      this.dynamicState.chatTextArea.focus()
    }
  }

  resize(gameSize: any, baseSize: any, displaySize: any, resolution: any) {
    const width = gameSize.width;
    const height = gameSize.height;

    this.cameras.resize(width, height);
  }

  setupInput() {
    const mainCamera = this.mainCamera

    if (this.input.mouse) {
      this.input.mouse.disableContextMenu();
    }

    const slashKey = this.input.keyboard?.addKey("FORWARD_SLASH")
    slashKey?.on("down", (event: any) => {
      this.simulationContext.showHelpPanel()
    })

    const escapeKey = this.input.keyboard?.addKey("ESC")

    escapeKey?.on("down", (event: any) => {
      if (this.dynamicState.selectedEntityId != null) {
        this.simulationContext.setSelectedEntityId(null)
        this.simulationContext.sendWebSocketMessage("ClearPendingInteractionTargetMessage", {})
      } else {
        this.simulationContext.closePanels()
      }
    })


    this.focusChatTextAreaKey = this.input.keyboard?.addKey("SHIFT")
    this.focusChatTextAreaKey?.on("down", (event: any) => {
      console.log("KEY DOWN!")
      this.focusChatTextArea()
    })

    // shiftKey?.on("down", (event: any) => {
    //   console.log("W")
    //   this.focusChatTextArea()
    // })

    if (this.cursorKeys) {
      this.cursorKeys.space.on("down", () => {
        this.autoInteract()
      })
    }

    let pointerDownLocation = Vector2.zero
    let clickValid = false
    const clickThreshold = 3.0
    let isDown = false


    this.input.on('pointerdown', (pointer: any) => {
      isDown = true
      clickValid = true
      pointerDownLocation = new Vector2(pointer.x, pointer.y)
    }, this);

    this.input.on('pointerup', (pointer: any) => {
      isDown = false
      const currentPointerLocation = new Vector2(pointer.x, pointer.y)

      const pointerDistanceFromDown = Vector2.distance(currentPointerLocation, pointerDownLocation)
      if (pointerDistanceFromDown > clickThreshold) {
        clickValid = false
      }

      if (clickValid) {
        const worldPoint = this.cameras.main.getWorldPoint(pointer.x, pointer.y)

        console.log("Clicked worldPoint", worldPoint)
        const simulationTime = this.getCurrentSimulationTime()

        let nearestDistance = 10000.0
        let nearestEntity: Entity | null = null

        const simulation = this.simulation
        for (const entity of simulation.entities) {
          const positionComponent = entity.getComponentOrNull<PositionComponentData>("PositionComponentData")
          const userControlledComponent = entity.getComponentOrNull<UserControlledComponentData>("UserControlledComponentData")

          if (positionComponent != null && (userControlledComponent == null || userControlledComponent.data.userId !== this.userId)) {
            const position = resolvePositionForTime(positionComponent, simulationTime)
            const distance = Vector2.distance(worldPoint, position)

            if (distance < nearestDistance) {
              nearestDistance = distance
              nearestEntity = entity
            }
          }
        }

        this.blurChatTextArea()

        if (pointer.rightButtonReleased()) {
          this.lastClickTime = getUnixTimeSeconds()

          if (nearestEntity != null && nearestDistance < 60) {
            this.simulationContext.sendWebSocketMessage("ClearPendingInteractionTargetMessage", {})
            this.simulationContext.setSelectedEntityId(nearestEntity.entityId)
          } else {
            this.simulationContext.setSelectedEntityId(null)
            this.simulationContext.sendWebSocketMessage("ClearPendingInteractionTargetMessage", {})
          }
        } else {
          this.lastClickTime = -1;
          this.simulationContext.sendWebSocketMessage("ClearPendingInteractionTargetMessage", {})
          //this.simulationContext.setSelectedEntityId(null)

          this.simulationContext.sendWebSocketMessage("MoveToPointMessage", {
            point: worldPoint
          })
        }
      }

      clickValid = false
    }, this);

    this.input.on('wheel', (pointer: any, gameObjects: any, deltaX: number, deltaY: number, deltaZ: number) => {
      mainCamera.setZoom(Math.max(0.75, Math.min(mainCamera.zoom * (1 - deltaY * 0.001), 1.5)))
    });

    this.input.on("pointermove", (pointer: any) => {
      if (!pointer.isDown || !isDown) return;

      const currentPointerLocation = new Vector2(pointer.x, pointer.y)
      const pointerDistanceFromDown = Vector2.distance(currentPointerLocation, pointerDownLocation)

      if (clickValid) {
        if (pointerDistanceFromDown > clickThreshold) {
          mainCamera.scrollX -= (pointer.x - pointerDownLocation.x) / mainCamera.zoom;
          mainCamera.scrollY -= (pointer.y - pointerDownLocation.y) / mainCamera.zoom;

          this.blurChatTextArea()

          clickValid = false
        }
      } else if (!clickValid) {
        this.blurChatTextArea()

        mainCamera.scrollX -= (pointer.x - pointer.prevPosition.x) / mainCamera.zoom;
        mainCamera.scrollY -= (pointer.y - pointer.prevPosition.y) / mainCamera.zoom;
      }

      this.clampCamera()
    });
  }

  clampCamera() {
    const mainCamera = this.mainCamera
    mainCamera.scrollX = Math.min(Math.max(mainCamera.scrollX, 0), this.worldBounds.x)
    mainCamera.scrollY = Math.min(Math.max(mainCamera.scrollY, 0), this.worldBounds.y)
  }

  calculateAutoInteractAction(): AutoInteractAction | null {
    const playerControlledEntity = this.simulation.entities.find(entity => {
      const userControlledComponent = entity.getComponentDataOrNull<UserControlledComponentData>("UserControlledComponentData")
      return userControlledComponent != null && userControlledComponent.userId === this.userId
    })

    if (playerControlledEntity == null) {
      return null
    }

    const playerCharacterComponent = playerControlledEntity.getComponentDataOrNull<CharacterComponentData>("CharacterComponentData")
    const equippedItemConfigKey = playerCharacterComponent?.equippedItemConfigKey

    const equippedItemConfig = equippedItemConfigKey ? this.getConfig<ItemConfig>(equippedItemConfigKey, "ItemConfig") : null

    const playerPosition = resolveEntityPositionForCurrentTime(playerControlledEntity)

    const simulationTime = this.getCurrentSimulationTime()

    let nearestDistance = 10000.0
    let nearestInteraction: AutoInteractAction | null = null

    const maxDistance = 300

    const simulation = this.simulation
    for (const entity of simulation.entities) {
      const positionComponent = entity.getComponentOrNull<PositionComponentData>("PositionComponentData")
      const userControlledComponent = entity.getComponentOrNull<UserControlledComponentData>("UserControlledComponentData")

      if (positionComponent != null && (userControlledComponent == null || userControlledComponent.data.userId !== this.userId)) {
        const position = resolvePositionForTime(positionComponent, simulationTime)
        const distance = Vector2.distance(playerPosition, position)

        if (distance <= maxDistance) {
          const itemComponentData = entity.getComponentDataOrNull<ItemComponentData>("ItemComponentData")

          const itemConfig = itemComponentData ? this.getConfig<ItemConfig>(itemComponentData.itemConfigKey, "ItemConfig") : null

          let interaction: AutoInteractAction | null = null
          if (itemConfig != null) {
            if (itemConfig.canBePickedUp) {
              interaction = {
                type: AutoInteractActionType.Interact,
                targetEntity: entity,
                actionTitle: "Pick-up",
                actionIcon: <IconHandGrab/>
              }
            } else if (itemConfig.canBeDamagedByItem != null &&
              equippedItemConfig != null &&
              equippedItemConfig.key === itemConfig.canBeDamagedByItem) {

              interaction = {
                type: AutoInteractActionType.Interact,
                targetEntity: entity,
                actionTitle: "Harvest",
                actionIcon: <IconTool/>
              }
            }
          }

          if (interaction != null && distance < nearestDistance) {
            nearestDistance = distance
            nearestInteraction = interaction
          }
        }
      }
    }

    if (nearestInteraction != null && nearestDistance < 300) {
      return nearestInteraction
    } else {
      return null
    }
  }

  autoInteract() {
    const playerControlledEntity = this.simulation.entities.find(entity => {
      const userControlledComponent = entity.getComponentDataOrNull<UserControlledComponentData>("UserControlledComponentData")
      return userControlledComponent != null && userControlledComponent.userId === this.userId
    })

    if (playerControlledEntity == null) {
      return
    }

    const autoInteractAction = this.calculateAutoInteractAction()

    if (autoInteractAction == null) {
      return
    }

    const playerPosition = resolveEntityPositionForCurrentTime(playerControlledEntity)

    const simulationTime = this.getCurrentSimulationTime()

    const actionType = autoInteractAction.type
    const targetEntity = autoInteractAction.targetEntity

    if (actionType === AutoInteractActionType.Clear) {
      this.simulationContext.setSelectedEntityId(null)
      this.simulationContext.sendWebSocketMessage("ClearPendingInteractionTargetMessage", {})
    } else if (actionType === AutoInteractActionType.SelectEntity) {
      if (targetEntity != null) {
        this.simulationContext.setSelectedEntityId(targetEntity.entityId)
        this.simulationContext.sendWebSocketMessage("ClearPendingInteractionTargetMessage", {})
      }
    } else if (actionType === AutoInteractActionType.Interact) {
      if (targetEntity != null) {
        const targetPosition = resolveEntityPositionForCurrentTime(targetEntity)
        const distance = Vector2.distance(targetPosition, playerPosition)
        const nearestEntityPosition = resolveEntityPositionForTime(targetEntity, simulationTime)
        const delta = Vector2.minus(playerPosition, nearestEntityPosition)
        const desiredDistance = Math.min(distance, 25.0)

        const desiredLocation = Vector2.plus(nearestEntityPosition, Vector2.timesScalar(Vector2.normalize(delta), desiredDistance))

        this.simulationContext.sendWebSocketMessage("MoveToPointMessage", {
          point: desiredLocation,
          pendingInteractionEntityId: targetEntity.entityId
        })
      }
    }
  }

  create() {
    this.scale.on('resize', this.resize, this);

    this.onCreateFunctions.forEach(it => {
      it()
    })

    this.backgroundLayer = this.add.layer()
    this.observationRingsLayer = this.add.layer()
    this.navigationPathLayer = this.add.layer()
    this.mainLayer = this.add.layer()
    this.highlightRingLayer = this.add.layer()
    this.characterNameLayer = this.add.layer()
    this.chatBubbleLayer = this.add.layer()

    const camera = this.cameras.main;
    const uiCamera = this.uiCamera!;

    const worldBounds = this.worldBounds
    this.cursorKeys = this.input.keyboard?.createCursorKeys()

    uiCamera.ignore(this.children.list);

    const playerControlledEntity = this.simulation.entities.find(entity => {
      const userControlledComponent = entity.getComponentOrNull<UserControlledComponentData>("UserControlledComponentData")
      return userControlledComponent != null && userControlledComponent.data.userId === this.userId
    })

    console.log("playerControlledEntity", playerControlledEntity)

    let mainCamera = this.cameras.main;

    if (playerControlledEntity != null) {
      const playerLocation = resolveEntityPositionForCurrentTime(playerControlledEntity)

      mainCamera.centerOn(playerLocation.x, playerLocation.y)
    } else {
      mainCamera.centerOn(this.worldBounds.x * 0.5, this.worldBounds.y * 0.5)
    }

    const backgroundGrass = this.add.tileSprite(
      this.sys.game.canvas.width * 0.5,
      this.sys.game.canvas.height * 0.5,
      this.sys.game.canvas.width * 1.5,
      this.sys.game.canvas.height * 1.5,
      "background-grass"
    )
    backgroundGrass.setScrollFactor(0, 0)
    backgroundGrass.setOrigin(0.5, 0.5)
    this.backgroundGrass = backgroundGrass
    // backgroundGrass.setScale(0.35, 0.35)
    this.uiCamera.ignore(backgroundGrass)
    this.backgroundLayer.add(backgroundGrass)

    this.setupInput()
  }

  getConfig<T extends Config>(configKey: string, serverSerializationTypeName: string): T {
    return this.simulation.getConfig<T>(configKey, serverSerializationTypeName)
  }

  renderEntities() {
    const simulationTime = this.getCurrentSimulationTime()

    const renderContext = this.renderContext

    const playerControlledEntity = this.simulation.entities.find(entity => {
      const userControlledComponent = entity.getComponentOrNull<UserControlledComponentData>("UserControlledComponentData")
      return userControlledComponent != null && userControlledComponent.data.userId === this.userId
    })

    const playerCharacterComponent = playerControlledEntity != null ? playerControlledEntity.getComponentOrNull<CharacterComponentData>("CharacterComponentData") : null;


    this.renderContext.render(() => {
      for (const entity of this.simulation.entities) {
        const positionComponent = entity.getComponentOrNull<PositionComponentData>("PositionComponentData")

        if (positionComponent == null) {
          continue
        }

        const itemComponent = entity.getComponentOrNull<ItemComponentData>("ItemComponentData")
        const characterComponent = entity.getComponentOrNull<CharacterComponentData>("CharacterComponentData")

        let position = resolvePositionForTime(positionComponent, simulationTime)

        if (playerCharacterComponent != null &&
          playerCharacterComponent.data.pendingInteractionTargetEntityId === entity.entityId) {
          renderContext.renderSprite("selected-entity-circle-pending-interaction:" + entity.entityId, {
            layer: this.highlightRingLayer,
            textureName: "ring",
            position: position,
            size: new Vector2(1, 1), // not working
            scale: new Vector2(0.25, 0.25),
            alpha: 1,
            depth: 0
          })
        }

        if (this.dynamicState.selectedEntityId === entity.entityId) {
          renderContext.renderSprite("selected-entity-circle:" + entity.entityId, {
            layer: this.highlightRingLayer,
            textureName: "ring",
            position: position,
            size: new Vector2(1, 1), // not working
            scale: new Vector2(0.2, 0.2),
            alpha: 1,
            depth: 0
          })
        }

        if (this.calculatedAutoInteraction?.targetEntity === entity) {
          renderContext.renderSprite("auto-interact-entity-circle:" + entity.entityId, {
            layer: this.highlightRingLayer,
            textureName: "ring",
            position: position,
            size: new Vector2(1, 1), // not working
            scale: new Vector2(0.2, 0.2),
            alpha: 1,
            depth: 0
          })
        }

        if (itemComponent != null) {
          this.renderItem(
            simulationTime,
            entity,
            renderContext,
            itemComponent.data,
            position
          )
        }

        if (characterComponent != null) {
          const agentComponentData = entity.getComponentDataOrNull<AgentComponentData>("AgentComponentData")

          this.renderCharacter(
            positionComponent.data,
            simulationTime,
            entity,
            renderContext,
            characterComponent.data,
            agentComponentData,
            position
          );
        }
      }
    })
  }

  calculateDepthForPosition(position: Vector2): number {
    return position.y
  }

  private renderItem(
    simulationTime: number,
    entity: Entity,
    renderContext: RenderContext,
    itemComponent: ItemComponentData,
    position: Vector2
  ) {
    const itemConfig = this.getConfig<ItemConfig>(itemComponent.itemConfigKey, "ItemConfig")
    const spriteConfigKey = itemConfig.spriteConfigKey
    const spriteConfig = this.getConfig<SpriteConfig>(spriteConfigKey, "SpriteConfig")

    renderContext.renderSprite("item_" + entity.entityId + "_" + spriteConfigKey, {
      layer: this.mainLayer,
      depth: this.calculateDepthForPosition(position),
      textureName: spriteConfigKey,
      position: Vector2.plus(position, spriteConfig.baseOffset),
      animation: null,
      scale: spriteConfig.baseScale
    })

    if (this.dynamicState.selectedEntityId === entity.entityId ||
      this.calculatedAutoInteraction?.targetEntity === entity) {
      renderContext.renderText("item-name:" + entity.entityId, {
        depth: this.calculateDepthForPosition(position),
        layer: this.characterNameLayer,
        text: itemConfig.name + (itemComponent.amount > 1 ? ` (x${itemComponent.amount})` : ""),
        strokeThickness: 3,
        fontSize: 20,
        useUiCamera: false,
        position: Vector2.plus(position, new Vector2(0, 15)),
        origin: new Vector2(0.5, 0),
        scale: Vector2.timesScalar(Vector2.one, 1.0 / this.mainCamera.zoom)
      })
    }
  }

  private renderCharacter(
    positionComponentData: PositionComponentData,
    simulationTime: number,
    entity: Entity,
    renderContext: RenderContext,
    characterComponentData: CharacterComponentData,
    agentComponentData: AgentComponentData | null,
    position: Vector2,
  ) {
    const mainCamera = this.mainCamera;

    positionComponentData.positionAnimation.keyFrames.forEach((keyFrame, index) => {
      if (keyFrame.time > simulationTime) {
        const spriteKey = entity.entityId + "-position-animation-" + index

        renderContext.renderSprite(spriteKey, {
          textureName: "circle",
          layer: this.navigationPathLayer,
          position: keyFrame.value,
          size: new Vector2(5, 5), // not working
          scale: new Vector2(0.2, 0.2),
          alpha: 0.5,
          depth: 0
        })
      }
    })

    const emoji = characterComponentData.facialExpressionEmoji;
    let statusSuffix = ""

    if (agentComponentData != null) {
      if (agentComponentData.agentIntegrationStatus != null) {
        if (agentComponentData.agentIntegrationStatus.includes("waiting_for_agent")) {
          // statusSuffix += "🕒"
        } else if (agentComponentData.agentIntegrationStatus === "paused") {
          statusSuffix += "⏸️"
        } else if (agentComponentData.agentIntegrationStatus === "error_from_remote_agent") {
          statusSuffix += "⚠️"
        } else if (agentComponentData.agentIntegrationStatus === "exception") {
          statusSuffix += "🚫"
        }
      }

      if (agentComponentData.agentStatus != null) {
        if (agentComponentData.agentStatus === "running-prompt") {
          statusSuffix += "💭"
        } else if (agentComponentData.agentStatus === "prompt-finished") {
          // statusSuffix += "✅"
        } else if (agentComponentData.agentStatus === "updating-memory") {
          statusSuffix += "📝️"
        } else if (agentComponentData.agentStatus === "update-memory-success") {
          // statusSuffix += "🟢"
        }
      }

      if (agentComponentData.agentError != null) {
        statusSuffix += "❌"
      }

      if (agentComponentData.wasRateLimited) {
        statusSuffix += "📉" // ⏳
      }
    }


    if (characterComponentData.equippedItemConfigKey != null) {
      const equippedItemConfig = this.simulation.getConfig<ItemConfig>(characterComponentData.equippedItemConfigKey, "ItemConfig")

      if (equippedItemConfig.equippedCompositeAnimation == null) {
        renderContext.renderSprite("equipped-item:" + entity.entityId + ":" + characterComponentData.equippedItemConfigKey, {
          layer: this.mainLayer,
          textureName: equippedItemConfig.spriteConfigKey,
          position: Vector2.plus(position, new Vector2(0, -20)),
          scale: new Vector2(0.15, 0.15),
          alpha: 1,
          depth: this.calculateDepthForPosition(position) + 1
        })
      }
    }


    const userControlledComponent = entity.getComponentDataOrNull<UserControlledComponentData>("UserControlledComponentData")

    renderContext.renderText("character-name:" + entity.entityId, {
      depth: this.calculateDepthForPosition(position),
      layer: this.characterNameLayer,
      text: (emoji != null ? (emoji + " ") : "") + characterComponentData.name + (statusSuffix != null ? (" " + statusSuffix) : ""),
      strokeThickness: 3,
      fontSize: 20,
      useUiCamera: false,
      position: Vector2.plus(position, new Vector2(0, 15)),
      origin: new Vector2(0.5, 0),
      scale: Vector2.timesScalar(Vector2.one, 1.0 / mainCamera.zoom),
      color: userControlledComponent == null
        ? "white"
        : userControlledComponent.userId === this.userId
          ? "#F3D16A"
          : "#BCEBD2"
    })

    const mostRecentSpokenMessage = characterComponentData.recentSpokenMessages.length > 0
      ? characterComponentData.recentSpokenMessages[characterComponentData.recentSpokenMessages.length - 1]
      : null

    const maxChatBubbleAge = 10

    if (mostRecentSpokenMessage != null && simulationTime - mostRecentSpokenMessage.sentSimulationTime < maxChatBubbleAge) {
      var spokenMessageText = mostRecentSpokenMessage.message

      const maxBubbleWidth = 250;
      const minBubbleWidth = 70;
      const bubblePaddingY = 6;
      const bubblePaddingX = 6;

      const textScale = 1.0

      const chatBubbleDepth = this.calculateDepthForPosition(position)

      const content = renderContext.renderText("character-spoken-message-text:" + entity.entityId + spokenMessageText, {
        layer: this.chatBubbleLayer,
        depth: chatBubbleDepth + 0.0001,
        text: spokenMessageText,
        fontSize: 14,
        useUiCamera: false,
        position: Vector2.zero,
        origin: new Vector2(0, 0),
        scale: Vector2.timesScalar(Vector2.one, textScale),
        color: "black",
        style: {
          align: 'center',
          wordWrap: {width: (maxBubbleWidth - (bubblePaddingX * 2))} // word wrap gets messed up by scale
        }
      })

      const textBounds = content.getBounds();
      const bubbleWidth = Math.max(minBubbleWidth, Math.min(maxBubbleWidth, textBounds.width + bubblePaddingX * 2));

      const bubbleHeight = textBounds.height + bubblePaddingY * 2;
      const arrowHeight = bubbleHeight / 4 + 5;
      const arrowPointOffsetX = bubbleWidth / 8;

      const bubble = renderContext.renderGraphics("character-spoken-message-bubble:" + entity.entityId + ":" + spokenMessageText, this.chatBubbleLayer, bubble => {
        // Bubble shadow
        bubble.clear()
        bubble.fillStyle(0x222222, 0.35);
        bubble.fillRoundedRect(3, 3, bubbleWidth, bubbleHeight, 8);

        //  Bubble color
        bubble.fillStyle(0xffffff, 1);

        //  Bubble outline line style
        bubble.lineStyle(4, 0x565656, 1);

        //  Bubble shape and outline
        bubble.strokeRoundedRect(0, 0, bubbleWidth, bubbleHeight, 8);
        bubble.fillRoundedRect(0, 0, bubbleWidth, bubbleHeight, 8);

        const point1X = Math.floor(arrowPointOffsetX);
        const point1Y = bubbleHeight;
        const point2X = Math.floor(arrowPointOffsetX * 2);
        const point2Y = bubbleHeight;
        const point3X = Math.floor(arrowPointOffsetX);
        const point3Y = Math.floor(bubbleHeight + arrowHeight);

        //  Bubble arrow shadow
        // bubble.lineStyle(4, 0x222222, 0.5);
        // bubble.lineBetween(point2X - 1, point2Y + 6, point3X + 2, point3Y);

        //  Bubble arrow fill
        bubble.fillTriangle(point1X, point1Y, point2X, point2Y, point3X, point3Y);
        bubble.lineStyle(2, 0x565656, 1);
        bubble.lineBetween(point2X, point2Y, point3X, point3Y);
        bubble.lineBetween(point1X, point1Y, point3X, point3Y);
      })

      bubble.setDepth(chatBubbleDepth)
      bubble.setX(position.x - arrowPointOffsetX)
      bubble.setY(position.y - 35 - bubbleHeight - arrowHeight)

      content.setPosition(bubble.x + (bubbleWidth / 2) - (textBounds.width / 2), bubble.y + (bubbleHeight / 2) - (textBounds.height / 2));
    }

    if (agentComponentData != null && this.dynamicState.selectedEntityId === entity.entityId) {
      const observationCircleScale = agentComponentData.observationDistance * 2.0 / 500.0

      renderContext.renderSprite("agent-observation-distance-circle:" + entity.entityId, {
        layer: this.observationRingsLayer,
        textureName: "ring",
        position: position,
        scale: new Vector2(observationCircleScale, observationCircleScale),
        alpha: 0.3,
        depth: 0
      })
    }

    let animation: string | null

    let keyFrames = positionComponentData.positionAnimation.keyFrames;
    let animationSuffix: string

    if (keyFrames.length <= 1) {
      animationSuffix = "down"
    } else {
      const first = keyFrames[0]
      const last = keyFrames[keyFrames.length - 1]

      let withinPositionAnimationRange: boolean

      let from: Vector2
      let to: Vector2

      if (simulationTime <= first.time) {
        from = first.value
        to = keyFrames[1].value
        withinPositionAnimationRange = false
      } else if (simulationTime >= last.time) {
        from = keyFrames[keyFrames.length - 2].value
        to = last.value
        withinPositionAnimationRange = false
      } else {
        from = position
        to = Vector2Animation.resolve(positionComponentData.positionAnimation, simulationTime + 0.001)
        withinPositionAnimationRange = true
      }

      const delta = Vector2.minus(to, from)
      const magnitude = Vector2.magnitude(delta)
      const moving = magnitude > 0.001 && withinPositionAnimationRange

      if (magnitude <= 0.001) {
        // idle
        animationSuffix = "down"
      } else if (Math.abs(delta.x) > Math.abs(delta.y) - 0.01) { // offset to avoid flickering during diagonal movement
        if (delta.x < 0) {
          animationSuffix = moving ? "walk-left" : "left"
        } else {
          animationSuffix = moving ? "walk-right" : "right"
        }
      } else {
        if (delta.y < 0) {
          animationSuffix = moving ? "walk-up" : "up"
        } else {
          animationSuffix = moving ? "walk-down" : "down"
        }
      }
    }

    const characterDepth = this.calculateDepthForPosition(position)

    const preferredCompositeAnimationCategory = characterComponentData.bodySelections.bodyType

    const offset = new Vector2(0.0, -20.0)
    const scale = new Vector2(1.2, 1.2)

    const positionWithOffset = Vector2.plus(position, offset)

    const renderCompositeAnimation = (spriteKeySuffix: string, compositeAnimation: CompositeAnimation) => {
      const sheetDefinition = this.sheetDefinitionsByKey[compositeAnimation.key]

      if (sheetDefinition == null) {
        throw new Error("sheetDefinition not found: " + compositeAnimation.key)
      }

      const variant = compositeAnimation.variant

      sheetDefinition.layers.forEach((layer, layerIndex) => {
        if (layer.animationConfigsByName[animationSuffix]) {
          const textureKeysByCategory = layer.textureKeysByCategoryByVariant[variant]

          if (textureKeysByCategory == null) {
            throw new Error(`textureKeysByCategory is null for variant ${variant} for composite animation ${compositeAnimation.key} layer ${layerIndex}`)
          }

          const textureKey = textureKeysByCategory[preferredCompositeAnimationCategory] ?? textureKeysByCategory["male"]

          if (textureKey) {
            animation = textureKey + "_" + animationSuffix

            const spriteKey = entity.entityId + "-composite-animation-layer-" + layerIndex + textureKey + ":" + spriteKeySuffix

            renderContext.renderSprite(spriteKey, {
              layer: this.mainLayer,
              depth: characterDepth + layer.zPos * 0.0001,
              textureName: textureKey,
              position: positionWithOffset,
              animation: animation,
              scale: scale
            })
          }
        }
      })
    }


    const bodySelections = characterComponentData.bodySelections

    const renderSkinColorCompositionAnimation = (type: string, animationKey: string | null) => {
      if (animationKey == null) {
        return
      }

      renderCompositeAnimation("character:" + type, {
        key: animationKey,
        variant: bodySelections.skinColor
      })
    }

    renderCompositeAnimation("character:shadow", {
      key: "shadow",
      variant: "shadow"
    })

    renderSkinColorCompositionAnimation("body", bodySelections.body)
    renderSkinColorCompositionAnimation("head", bodySelections.head)
    renderSkinColorCompositionAnimation("nose", bodySelections.nose)
    renderSkinColorCompositionAnimation("wrinkles", bodySelections.wrinkles)

    if (bodySelections.hair != null) {
      renderCompositeAnimation("character:hair", bodySelections.hair)
    }

    if (bodySelections.eyes != null) {
      renderCompositeAnimation("character:hair", bodySelections.eyes)
    }

    var hasPants = false
    var hasShirt = false

    if (!hasShirt) {
      renderCompositeAnimation("shirt", {
        key: "torso_clothes_male_sleeveless_laced",
        variant: "white"
      })
    }

    if (!hasPants) {
      renderCompositeAnimation("shirt", {
        key: "legs_pantaloons",
        variant: "white"
      })
    }

    if (characterComponentData.equippedItemConfigKey != null) {
      const equippedItemConfig = this.simulation.getConfig<ItemConfig>(characterComponentData.equippedItemConfigKey, "ItemConfig")

      if (equippedItemConfig.equippedCompositeAnimation != null) {
        renderCompositeAnimation("equipped-item", equippedItemConfig.equippedCompositeAnimation)
      }
    }
  }

  calculatedAutoInteraction: AutoInteractAction | null = null

  update(time: number, deltaMs: number) {
    const backgroundSizeWidth = this.sys.game.canvas.width
    const backgroundSizeHeight = this.sys.game.canvas.height

    const maxZoomHandlingScale = 1.4
    this.backgroundGrass.setScale(maxZoomHandlingScale, maxZoomHandlingScale)
    this.backgroundGrass.setX(this.sys.game.canvas.width * 0.5)
    this.backgroundGrass.setY(this.sys.game.canvas.height * 0.5)
    this.backgroundGrass.setSize(backgroundSizeWidth, backgroundSizeHeight)
    const tileScale = 0.2
    const tileScaleY = 0.7
    this.backgroundGrass.setTileScale(tileScale / maxZoomHandlingScale, tileScale * tileScaleY / maxZoomHandlingScale)
    this.backgroundGrass.setTilePosition(this.mainCamera.scrollX / tileScale, this.mainCamera.scrollY / tileScale / tileScaleY)

    const deltaTime = deltaMs / 1000.0

    this.simulation.update(deltaTime)

    const autoInteraction = this.calculateAutoInteractAction()

    const previousAutoInteraction = this.calculatedAutoInteraction;
    this.calculatedAutoInteraction = autoInteraction

    if (autoInteraction != null) {
      if (previousAutoInteraction == null) {
        this.dynamicState.forceUpdate()
      } else if (previousAutoInteraction.type !== autoInteraction.type ||
        previousAutoInteraction.targetEntity !== autoInteraction.targetEntity) {
        this.dynamicState.forceUpdate()
      }
    } else if (previousAutoInteraction != null) {
      this.dynamicState.forceUpdate()
    }

    const camera_speed = 800;
    const cursors = this.cursorKeys
    const mainCamera = this.cameras.main

    if (cursors) {
      if (cursors.left.isDown) {
        mainCamera.scrollX -= camera_speed * deltaTime
        this.clampCamera()
      }

      if (cursors.right.isDown) {
        mainCamera.scrollX += camera_speed * deltaTime
        this.clampCamera()
      }

      if (cursors.up.isDown) {
        mainCamera.scrollY -= camera_speed * deltaTime
        this.clampCamera()
      }

      if (cursors.down.isDown) {
        mainCamera.scrollY += camera_speed * deltaTime
        this.clampCamera()
      }
    }

    if (this.input.keyboard) {
      this.input.keyboard.disableGlobalCapture()
    }

    this.renderEntities()
  }

  getCurrentSimulationTime(): number {
    return this.simulation.getCurrentSimulationTime()
  }
}
