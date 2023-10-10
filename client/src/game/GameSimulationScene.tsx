import Phaser from "phaser"
import {Config, EntityId} from "../simulation/EntityData"

import {getUnixTimeSeconds, throwError} from "../misc/utils"
import {SimulationId, UserId} from "../simulation/Simulation"
import {clampZeroOne, lerp, Vector2} from "../misc/Vector2"
import {Vector2Animation} from "../misc/Vector2Animation"
import {
  CharacterBodySelectionsConfig,
  CompositeAnimationRegistryConfig,
  RegisteredCompositeAnimation,
  SpriteAnimation,
  SpriteConfig
} from "../common/common"
import {RenderContext} from "../common/RenderContext"
import {
  ActionTypes,
  CharacterBodySelections,
  CharacterComponent,
  CharacterComponentData,
  InventoryComponentData,
  UseEquippedToolItemRequest
} from "./CharacterComponentData"
import {
  EquipmentSlots,
  GrowerComponent,
  ItemComponent,
  ItemComponentData,
  ItemConfig,
  KillableComponent
} from "./ItemComponentData"
import {Entity} from "../simulation/Entity"
import {
  PositionComponent,
  PositionComponentData,
  resolveEntityPositionForCurrentTime,
  resolveEntityPositionForTime,
  resolvePositionForTime
} from "../common/PositionComponentData"
import {AgentControlledComponent, AgentControlledComponentData} from "./AgentControlledComponentData"
import {UserControlledComponent, UserControlledComponentData} from "./userControlledComponentData"
import {IconHandGrab, IconTool} from "@tabler/icons-react"
import {CompositeAnimationSelection} from "./CompositeAnimationSelection"
import {GameSimulation} from "./GameSimulation"
import {DynamicState} from "../components/DynamicState"
import {Pinch} from 'phaser3-rex-plugins/plugins/gestures.js'
import Layer = Phaser.GameObjects.Layer
import FilterMode = Phaser.Textures.FilterMode
import {DebugInfoComponentData} from "./DebugInfoComponentData";
import {getNearestEntitiesFromList} from "./utils";
import {ReactElement} from "react";

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
  texturePartialPathsByCategoryByVariant: Record<string, Record<string, string>>
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
  setSelectedEntityId: (entityId: EntityId | null) => void
  setPerspectiveEntityIdOverride: (entityId: EntityId | null) => void
  closePanels: () => void
  showHelpPanel: () => void
  showMenuPanel: () => void
}

export enum AutoInteractActionType {
  Clear,
  SelectEntity,
  Pickup,
  UseToolToDamageEntity,
  PlaceGrowableInGrower,
  UseEquippedTool,
  StopMoving
}

export interface AutoInteractAction {
  type: AutoInteractActionType
  equippedToolItemConfig?: ItemConfig | null
  actionTitle: string
  targetEntity?: Entity | null
  actionIcon: ReactElement | null
}

interface MoveToPointRequest {
  point: Vector2
  pendingUseEquippedToolItemRequest?: UseEquippedToolItemRequest
  pendingInteractionEntityId?: EntityId
}

interface FogOfWarInfo {
  center: Vector2
  radius: number
}

function getAnimationDirectionForRelativeLocation(delta: Vector2): string | null {
  const magnitude = Vector2.magnitude(delta)

  if (magnitude <= 0.001) {
    return null
  } else if (Math.abs(delta.x) > Math.abs(delta.y) - 0.01) { // offset to avoid flickering during diagonal movement
    if (delta.x < 0) {
      return "left"
    } else {
      return "right"
    }
  } else {
    if (delta.y < 0) {
      return "up"
    } else {
      return "down"
    }
  }
}

export class GameSimulationScene extends Phaser.Scene {
  readonly simulationId: SimulationId
  readonly dynamicState: DynamicState
  readonly simulation: GameSimulation
  readonly isReplay: boolean

  focusChatTextAreaKey: Phaser.Input.Keyboard.Key | undefined = undefined

  private cursorKeys: Phaser.Types.Input.Keyboard.CursorKeys | null | undefined = null

  private onCreateFunctions: { (): void } [] = []
  private onLoadCompleteFunctions: { (): void } [] = []

  private context: SimulationSceneContext
  readonly userId: UserId

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

  private readonly worldBounds: Vector2
  private pinch?: Pinch
  calculatedAutoInteraction: AutoInteractAction | null = null

  constructor(
    simulation: GameSimulation,
    context: SimulationSceneContext
  ) {
    super("SimulationPhaserScene")

    this.simulation = simulation
    this.worldBounds = simulation.worldBounds
    this.isReplay = simulation.isReplay
    this.userId = context.dynamicState.userId
    this.simulationId = simulation.simulationId

    this.context = context
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
    const uiCamera = this.cameras.add(0, 0, this.scale.width, this.scale.height)
    this.uiCamera = uiCamera
    const mainCamera = this.cameras.main
    this.mainCamera = mainCamera

    var screenWidth = uiCamera.width
    var screenHeight = uiCamera.height

    var progressBar = this.add.graphics()
    var progressBox = this.add.graphics()
    progressBox.fillStyle(0x222222, 0.2)
    const progressBoxWidth = 320
    const progressBoxHeight = 20
    const progressBoxX = screenWidth / 2 - progressBoxWidth / 2
    const progressBoxY = screenHeight / 2 - progressBoxHeight / 2
    progressBox.fillRect(progressBoxX, progressBoxY, progressBoxWidth, progressBoxHeight)

    this.load.image("background-grass", "/assets/environment/grass1.png")

    const loadingText = this.make.text({
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

    var assetText = this.make.text({
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

    this.load.on('progress', (value: number) => {
      // joshr: Avoid errors modifying text if an error during preload causes simulation scene to be destroyed
      // before progress callbacks stop
      if (this.dynamicState.scene === this) {
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

    this.load.on('fileprogress', (file: any) => {
      if (this.dynamicState.scene === this) {
        assetText.setText('Loading asset: ' + file.key)
      }
    })

    this.load.on('complete', () => {
      progressBar.destroy()
      progressBox.destroy()
      // loadingText.destroy()
      // percentText.destroy()
      assetText.destroy()
      for (let onLoadCompleteFunction of this.onLoadCompleteFunctions) {
        onLoadCompleteFunction()
      }
    })

    this.load.image("black-square", "/assets/misc/black-square.png")
    this.load.image("fog-of-war", "/assets/misc/fog-of-war.png")
    this.load.image("circle", "/assets/misc/circle.png")
    this.load.image("ring", "/assets/misc/ring.png")
    this.load.image("character-shadow", "/assets/misc/character-shadow.png")

    this.preloadJson(() => {
      this.preloadSprites()
    })
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
      )
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

    const includedCategories = this.compositeAnimationRegistryConfig.includedCategories

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
    const width = gameSize.width
    const height = gameSize.height

    this.cameras.resize(width, height)
  }

  sendMoveToPointRequest(moveToPointRequest: MoveToPointRequest) {
    if (this.dynamicState.userControlledEntity == null) {
      return
    }

    this.context.sendWebSocketMessage("MoveToPointRequest", moveToPointRequest)
  }

  sendNotifyClientActiveRequest() {
    if (this.isReplay) {
      return
    }

    this.context.sendWebSocketMessage("NotifyClientActive", {})
  }

  setDebugOverlayValueForKey(key: string, value: any | null) {
    this.dynamicState.setDebugOverlayValueForKey(key, value)
  }

  scrollVelocity: Vector2 = Vector2.zero
  zoomVelocity: number = 0.0
  isZooming = false
  isDragging = false

  setupInput() {
    const mainCamera = this.mainCamera

    this.pinch = new Pinch(this, {})

    this.pinch.on("drag1", (drag: any) => {
      const drag1Vector: { x: number, y: number } = drag.drag1Vector
      const scrollMovement = new Vector2(-drag1Vector.x / mainCamera.zoom, -drag1Vector.y / mainCamera.zoom)
      this.isDragging = true
      const dt = 1.0 / 60.0
      this.scrollVelocity = Vector2.timesScalar(scrollMovement, 1.0 / dt)
      mainCamera.scrollX += scrollMovement.x
      mainCamera.scrollY += scrollMovement.y
      this.blurChatTextArea()
    }, this)

    this.pinch.on("drag1end", (drag: any) => {
      this.isDragging = false
    }, this)

    this.pinch.on("pinchend", (drag: any) => {
      this.isZooming = false
    }, this)

    this.pinch.on("pinch", (pinch: any) => {
      const cursor = new Vector2(pinch.centerX, pinch.centerY)

      const worldPointBeforeZoomChange = this.getWorldPointUnderCanvasPoint(cursor)

      const scaleFactor: number = pinch.scaleFactor
      this.zoomVelocity = scaleFactor
      this.zoomValue *= scaleFactor

      this.clampCamera()

      const worldPointAfterZoomChange = this.getWorldPointUnderCanvasPoint(cursor)

      const delta = Vector2.minus(worldPointAfterZoomChange, worldPointBeforeZoomChange)

      this.mainCamera.scrollX -= delta.x
      this.mainCamera.scrollY -= delta.y

      const movementCenter: { x: number, y: number } = {x: pinch.movementCenterX, y: pinch.movementCenterY}

      // jshmrsn: I am not sure why the 0.5 multiplier is needed, but it seems to precisely achieve maintaining
      // the same world point under the gesture during drag
      const scrollMovement = Vector2.timesScalar(new Vector2(
        -movementCenter.x / mainCamera.zoom,
        -movementCenter.y / mainCamera.zoom
      ), 0.5)

      mainCamera.scrollX += scrollMovement.x
      mainCamera.scrollY += scrollMovement.y

      this.isDragging = true
      const dt = 1.0 / 60.0
      this.scrollVelocity = Vector2.timesScalar(scrollMovement, 1.0 / dt)


      this.isZooming = true
      this.clampCamera()
    }, this)

    if (this.input.mouse) {
      this.input.mouse.disableContextMenu()
    }

    const slashKey = this.input.keyboard?.addKey("FORWARD_SLASH")
    slashKey?.on("down", (event: any) => {
      this.context.showHelpPanel()
    })

    const escapeKey = this.input.keyboard?.addKey("ESC")

    escapeKey?.on("down", (event: any) => {
      if (this.dynamicState.selectedEntityId != null) {
        this.context.setSelectedEntityId(null)
        this.clearPendingInteractionTargetRequest()
      } else {
        this.context.showMenuPanel()
      }
    })


    this.focusChatTextAreaKey = this.input.keyboard?.addKey("SHIFT")
    this.focusChatTextAreaKey?.on("down", (event: any) => {
      this.focusChatTextArea()
    })

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
    }, this)

    this.input.on('pointerup', (pointer: any) => {
      isDown = false
      const currentPointerLocation = new Vector2(pointer.x, pointer.y)

      const pointerDistanceFromDown = Vector2.distance(currentPointerLocation, pointerDownLocation)
      if (pointerDistanceFromDown > clickThreshold) {
        clickValid = false
      }

      if (clickValid) {
        const forPerspectiveOverride = this.cursorKeys?.shift?.isDown || false

        const worldPoint = this.getWorldPointUnderCanvasPoint(new Vector2(pointer.x, pointer.y))

        const simulationTime = this.getCurrentSimulationTime()

        const entitiesNearCenter = this.fogOfWarVisibleEntities
          .filter(entity => PositionComponent.getOrNull(entity) != null && (!forPerspectiveOverride || CharacterComponent.getOrNull(entity) != null))
          .map(entity => {
            const positionComponent = PositionComponent.get(entity)

            const position = resolvePositionForTime(positionComponent, simulationTime)
            const distance = Vector2.distance(worldPoint, position)

            return {
              entity: entity,
              distance: distance
            }
          })
          .filter(it => it.distance < 60.0)

        entitiesNearCenter.sort((a, b) => {
          if (a.distance < b.distance) {
            return -1
          } else if (a.distance > b.distance) {
            return 1
          } else {
            if (a.entity.entityId < b.entity.entityId) {
              return -1
            } else {
              return 1
            }
          }
        })

        this.blurChatTextArea()

        if (pointer.rightButtonReleased()) {
          this.lastClickTime = getUnixTimeSeconds()

          if (entitiesNearCenter.length !== 0) {
            const selectedEntityId = forPerspectiveOverride ? this.dynamicState.perspectiveEntity?.entityId : this.dynamicState.selectedEntityId

            const selectedIndex = entitiesNearCenter
              .findIndex(it => it.entity.entityId === selectedEntityId)

            const nextIndex = (selectedIndex === -1 || selectedIndex === (entitiesNearCenter.length - 1))
              ? 0
              : selectedIndex + 1

            if (forPerspectiveOverride) {
              this.context.setPerspectiveEntityIdOverride(entitiesNearCenter[nextIndex].entity.entityId)
            } else {
              this.context.setSelectedEntityId(entitiesNearCenter[nextIndex].entity.entityId)
            }
          } else {
            if (forPerspectiveOverride) {
              this.context.setPerspectiveEntityIdOverride(null)
            } else {
              this.context.setSelectedEntityId(null)
              this.clearPendingInteractionTargetRequest()
            }
          }
        } else {
          this.lastClickTime = -1
          this.clearPendingInteractionTargetRequest()

          const userControlledEntity = this.dynamicState.userControlledEntity

          if (userControlledEntity != null) {
            this.sendMoveToPointRequest({
              point: worldPoint
            })
          } else {
            this.sendNotifyClientActiveRequest()
          }
        }
      } else {
        this.sendNotifyClientActiveRequest()
      }

      clickValid = false
    }, this)

    this.input.on('wheel', (pointer: any, gameObjects: any, deltaX: number, deltaY: number, deltaZ: number) => {
      this.scrollVelocity = Vector2.zero

      const cursor = new Vector2(pointer.x, pointer.y)

      const worldPointBeforeZoomChange = this.getWorldPointUnderCanvasPoint(cursor)

      this.zoomValue = this.zoomValue * (1 - deltaY * 0.001)
      this.clampCamera()

      const worldPointAfterZoomChange = this.getWorldPointUnderCanvasPoint(cursor)

      const delta = Vector2.minus(worldPointAfterZoomChange, worldPointBeforeZoomChange)

      this.mainCamera.scrollX -= delta.x
      this.mainCamera.scrollY -= delta.y

      this.clampCamera()
    })

    this.input.on("pointermove", (pointer: any) => {
      if (!pointer.isDown || !isDown) return

      const currentPointerLocation = new Vector2(pointer.x, pointer.y)
      const pointerDistanceFromDown = Vector2.distance(currentPointerLocation, pointerDownLocation)

      if (clickValid) {
        if (pointerDistanceFromDown > clickThreshold) {
          this.blurChatTextArea()
          clickValid = false
        }
      } else if (!clickValid) {
        this.blurChatTextArea()
      }

      this.clampCamera()
    })
  }

  getWorldPointUnderCanvasPoint(canvasPoint: Vector2) {
    // jshmrsn: For unknown this reasons, this function generally produces slightly
    // different result than Phaser3's mainCamera.getWorldPoint. More importantly,
    // this function produces significantly different results immediately after
    // camera zoom is changes (within the same frame). The output of this function
    // behaved in a usable way for maintaining point-under-cursor while off-center
    // zooming.
    const mainCamera = this.mainCamera
    const canvasSize = new Vector2(this.game.canvas.width, this.game.canvas.height)
    const canvasCenter = Vector2.timesScalar(canvasSize, 0.5)
    const cameraScroll = new Vector2(mainCamera.scrollX, mainCamera.scrollY)
    const cameraCenter = Vector2.plus(cameraScroll, Vector2.timesScalar(canvasSize, 0.5))
    const cursorRelativeToCenter = Vector2.minus(canvasPoint, canvasCenter)

    return Vector2.plus(cameraCenter, Vector2.timesScalar(cursorRelativeToCenter, 1.0 / mainCamera.zoom))
  }

  private clearPendingInteractionTargetRequest() {
    if (this.isReplay) {
      return
    }

    this.context.sendWebSocketMessage("ClearPendingInteractionTargetRequest", {})
  }

  private zoomValue = 1.0
  readonly maxZoom = 4.0
  readonly minZoom = 0.25

  clampCamera() {
    const canvasSize = new Vector2(this.game.canvas.width, this.game.canvas.height)
    const canvasBuffer = Vector2.timesScalar(canvasSize, 0.5)

    const extraBuffer = 500.0

    this.zoomValue = Math.max(this.minZoom, Math.min(this.zoomValue, this.maxZoom))

    const mainCamera = this.mainCamera
    mainCamera.scrollX = Math.min(Math.max(mainCamera.scrollX, -canvasBuffer.x - extraBuffer), this.worldBounds.x - canvasBuffer.x + extraBuffer)
    mainCamera.scrollY = Math.min(Math.max(mainCamera.scrollY, -canvasBuffer.y - extraBuffer), this.worldBounds.y - canvasBuffer.y + extraBuffer)

    // console.log("this.game.canvas.width", this.game.canvas.width)
    const canvasSizeFactor = 1.0 //lerp(this.game.canvas.width / 2000.0, 1.0, 0.5)
    // console.log("canvasSizeFactor", canvasSizeFactor)

    mainCamera.setZoom(this.zoomValue * canvasSizeFactor)

    const backgroundSizeWidth = this.game.canvas.width
    const backgroundSizeHeight = this.game.canvas.height

    const maxZoomHandlingScale = 1.0 / this.minZoom / canvasSizeFactor
    this.backgroundGrass.setScale(maxZoomHandlingScale, maxZoomHandlingScale)
    this.backgroundGrass.setX(this.game.canvas.width * 0.5)
    this.backgroundGrass.setY(this.game.canvas.height * 0.5)
    this.backgroundGrass.setSize(backgroundSizeWidth, backgroundSizeHeight)
    const tileScale = 0.2
    const tileScaleY = 0.7
    this.backgroundGrass.setTileScale(tileScale / maxZoomHandlingScale, tileScale * tileScaleY / maxZoomHandlingScale)
    this.backgroundGrass.setTilePosition(this.mainCamera.scrollX / tileScale, this.mainCamera.scrollY / tileScale / tileScaleY)
  }

  calculateAutoInteractAction(): AutoInteractAction | null {
    const userControlledEntity = this.dynamicState.userControlledEntity

    if (userControlledEntity == null) {
      return null
    }

    const playerInventoryComponent = userControlledEntity.getComponentData<InventoryComponentData>("InventoryComponentData")

    const equippedToolItemConfig = playerInventoryComponent.inventory.itemStacks
      .filter(it => it.isEquipped)
      .map(itemStack => {
        return {
          itemStack: itemStack,
          itemConfig: this.getConfig<ItemConfig>(itemStack.itemConfigKey, "ItemConfig")
        }
      })
      .find(it => it.itemConfig.equippableConfig && it.itemConfig.equippableConfig.equipmentSlot === EquipmentSlots.Tool)?.itemConfig

    const playerPositionComponentData = PositionComponent.getData(userControlledEntity)
    const playerPositionAnimation = playerPositionComponentData.positionAnimation

    const playerPosition = resolveEntityPositionForCurrentTime(userControlledEntity)
    const simulationTime = this.getCurrentSimulationTime()

    const isPlayerMoving = playerPositionAnimation.keyFrames.length !== 0 &&
      simulationTime <= playerPositionAnimation.keyFrames[playerPositionAnimation.keyFrames.length - 1].time

    if (equippedToolItemConfig != null && equippedToolItemConfig.spawnItemOnUseConfig != null) {
      if (isPlayerMoving) {
        return {
          type: AutoInteractActionType.StopMoving,
          targetEntity: null,
          actionTitle: "Stop",
          actionIcon: <IconTool/>
        }
      } else {
        return {
          type: AutoInteractActionType.UseEquippedTool,
          targetEntity: null,
          actionTitle: "Use Tool",
          actionIcon: <IconTool/>,
          equippedToolItemConfig: equippedToolItemConfig
        }
      }
    }


    let nearestDistance = 10000.0
    let nearestInteraction: AutoInteractAction | null = null

    const maxDistance = 300

    for (const targetEntity of this.fogOfWarVisibleEntities) {
      const positionComponent = PositionComponent.getOrNull(targetEntity)
      const userControlledComponent = UserControlledComponent.getOrNull(targetEntity)
      const killableComponent = KillableComponent.getOrNull(targetEntity)

      if (positionComponent != null &&
        (userControlledComponent == null || userControlledComponent.data.userId !== this.userId) &&
        (killableComponent == null || killableComponent.data.killedAtTime == null)) {
        const position = resolvePositionForTime(positionComponent, simulationTime)
        const distance = Vector2.distance(playerPosition, position)

        if (distance <= maxDistance) {
          const targetItemComponentData = targetEntity.getComponentDataOrNull<ItemComponentData>("ItemComponentData")

          const targetItemConfig = targetItemComponentData ? this.getConfig<ItemConfig>(targetItemComponentData.itemConfigKey, "ItemConfig") : null
          const targetGrowerConfig = targetItemConfig?.growerConfig

          const targetGrowerComponentData = GrowerComponent.getDataOrNull(targetEntity)

          let interaction: AutoInteractAction | null = null
          if (targetItemConfig != null) {
            if (targetItemConfig.storableConfig != null) {
              interaction = {
                type: AutoInteractActionType.Pickup,
                targetEntity: targetEntity,
                actionTitle: "Pick-up",
                actionIcon: <IconHandGrab/>
              }
            } else if (targetItemConfig.damageableConfig && targetItemConfig.damageableConfig.damageableByEquippedToolItemConfigKey != null &&
              equippedToolItemConfig != null &&
              equippedToolItemConfig.key === targetItemConfig.damageableConfig.damageableByEquippedToolItemConfigKey) {

              interaction = {
                type: AutoInteractActionType.UseToolToDamageEntity,
                targetEntity: targetEntity,
                actionTitle: "Harvest",
                actionIcon: <IconTool/>
              }
            } else if (equippedToolItemConfig != null &&
              equippedToolItemConfig.growableConfig != null &&
              targetGrowerConfig != null &&
              targetGrowerComponentData != null &&
              targetGrowerComponentData.activeGrowth == null &&
              targetGrowerConfig.canReceiveGrowableItemConfigKeys.includes(equippedToolItemConfig.key)) {
              interaction = {
                type: AutoInteractActionType.PlaceGrowableInGrower,
                targetEntity: targetEntity,
                actionTitle: "Plant",
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
    if (this.isReplay) {
      return
    }

    const userControlledEntity = this.dynamicState.userControlledEntity

    if (userControlledEntity == null) {
      return
    }

    const autoInteractAction = this.calculateAutoInteractAction()

    if (autoInteractAction == null) {
      return
    }

    const playerPosition = resolveEntityPositionForCurrentTime(userControlledEntity)

    const simulationTime = this.getCurrentSimulationTime()

    const actionType = autoInteractAction.type
    const targetEntity = autoInteractAction.targetEntity

    if (actionType === AutoInteractActionType.Clear) {
      this.context.setSelectedEntityId(null)
      this.clearPendingInteractionTargetRequest()
    } else if (actionType === AutoInteractActionType.SelectEntity) {
      if (targetEntity != null) {
        this.context.setSelectedEntityId(targetEntity.entityId)
        this.clearPendingInteractionTargetRequest()
      }
    } else if (actionType === AutoInteractActionType.UseEquippedTool) {
      this.sendMoveToPointRequest({
        point: playerPosition,
        pendingUseEquippedToolItemRequest: {
          expectedItemConfigKey: autoInteractAction.equippedToolItemConfig!.key
        }
      })
    } else if (actionType === AutoInteractActionType.StopMoving) {
      const playerPositionAfterLatencyBuffer = resolveEntityPositionForTime(userControlledEntity, simulationTime + 0.3)

      this.sendMoveToPointRequest({
        point: playerPositionAfterLatencyBuffer
      })
    } else if (actionType === AutoInteractActionType.UseToolToDamageEntity ||
      actionType === AutoInteractActionType.Pickup ||
      actionType === AutoInteractActionType.PlaceGrowableInGrower) {
      if (targetEntity != null) {
        const targetPosition = resolveEntityPositionForCurrentTime(targetEntity)
        const distance = Vector2.distance(targetPosition, playerPosition)
        const nearestEntityPosition = resolveEntityPositionForTime(targetEntity, simulationTime)
        const delta = Vector2.minus(playerPosition, nearestEntityPosition)
        const desiredDistance = Math.min(distance, 25.0)

        const desiredLocation = Vector2.plus(nearestEntityPosition, Vector2.timesScalar(Vector2.normalize(delta), desiredDistance))

        this.sendMoveToPointRequest({
          point: desiredLocation,
          pendingInteractionEntityId: targetEntity.entityId
        })
      }
    }
  }

  centerCameraOnLocation(location: Vector2) {
    this.mainCamera.centerOn(location.x, location.y)
  }

  centerCameraOnEntityId(entityId: EntityId) {
    const visibleEntity = this.fogOfWarVisibleEntitiesById[entityId]

    if (visibleEntity != null) {
      this.centerCameraOnLocation(resolveEntityPositionForCurrentTime(visibleEntity))
    }
  }

  create() {
    this.scale.on('resize', this.resize, this)

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

    const uiCamera = this.uiCamera!

    const worldBounds = this.worldBounds
    this.cursorKeys = this.input.keyboard?.createCursorKeys()

    uiCamera.ignore(this.children.list)

    const userControlledEntity = this.dynamicState.userControlledEntity

    const anyCharacterEntity = this.simulation.entities.find(entity => {
      return CharacterComponent.getOrNull(entity) != null
    })

    const centerCameraOnEntity = userControlledEntity ?? anyCharacterEntity
    if (centerCameraOnEntity != null) {
      const centerOnLocation = resolveEntityPositionForCurrentTime(centerCameraOnEntity)
      this.centerCameraOnLocation(centerOnLocation)
    } else {
      this.centerCameraOnLocation(new Vector2(worldBounds.x * 0.5, worldBounds.y * 0.5))
    }

    const backgroundGrass = this.add.tileSprite(
      this.game.canvas.width * 0.5,
      this.game.canvas.height * 0.5,
      this.game.canvas.width * 1.5,
      this.game.canvas.height * 1.5,
      "background-grass"
    )
    backgroundGrass.setScrollFactor(0, 0)
    backgroundGrass.setOrigin(0.5, 0.5)
    this.backgroundGrass = backgroundGrass
    // backgroundGrass.setScale(0.35, 0.35)
    this.uiCamera.ignore(backgroundGrass)
    this.backgroundLayer.add(backgroundGrass)

    this.setupInput()
    this.clampCamera()
  }

  getConfig<T extends Config>(configKey: string, serverSerializationTypeName: string): T {
    return this.simulation.getConfig<T>(configKey, serverSerializationTypeName)
  }

  readonly fogOfWarVisibleEntities: Entity[] = []
  fogOfWarVisibleEntitiesById: Record<EntityId, Entity> = {}

  renderEntities(didRenderDebugInfo: boolean, fogOfWarInfo: FogOfWarInfo | null, perspectiveEntity: Entity | null) {
    this.fogOfWarVisibleEntities.splice(0, this.fogOfWarVisibleEntities.length)
    const previousFogOfWarVisibleEntitiesById = this.fogOfWarVisibleEntitiesById
    this.fogOfWarVisibleEntitiesById = {}

    const simulationTime = this.getCurrentSimulationTime()

    const renderContext = this.renderContext

    const userControlledEntity = this.dynamicState.userControlledEntity

    const playerCharacterComponent = userControlledEntity != null ? userControlledEntity.getComponentOrNull<CharacterComponentData>("CharacterComponentData") : null

    for (const entity of this.simulation.entities) {
      const positionComponent = entity.getComponentOrNull<PositionComponentData>("PositionComponentData")

      if (positionComponent == null) {
        continue
      }

      const itemComponent = entity.getComponentOrNull<ItemComponentData>("ItemComponentData")
      const characterComponent = entity.getComponentOrNull<CharacterComponentData>("CharacterComponentData")

      let position = resolvePositionForTime(positionComponent, simulationTime)

      let fogOfWarOccludePercent
      if (fogOfWarInfo == null) {
        fogOfWarOccludePercent = 0.0
      } else {
        const distanceFromFogOfWarCenter = Vector2.distance(position, fogOfWarInfo.center)
        fogOfWarOccludePercent = Math.min(Math.max(0, (distanceFromFogOfWarCenter - fogOfWarInfo.radius) / 50.0), 1.0)
      }

      if (fogOfWarOccludePercent < 0.1) {
        this.fogOfWarVisibleEntitiesById[entity.entityId] = entity
        this.fogOfWarVisibleEntities.push(entity)
      }

      const fogOfWarAlpha = lerp(1.0, 0.0, fogOfWarOccludePercent)

      if (didRenderDebugInfo) {
        const spriteScale = Vector2.uniform(5.0 / 64.0)

        renderContext.renderSprite("center-debug:" + entity.entityId, {
          layer: this.highlightRingLayer,
          textureName: "circle",
          position: position,
          scale: spriteScale,
          alpha: 0.8,
          depth: 10000
        })
      }

      if (playerCharacterComponent != null &&
        playerCharacterComponent.data.pendingInteractionTargetEntityId === entity.entityId) {
        renderContext.renderSprite("selected-entity-circle-pending-interaction:" + entity.entityId, {
          layer: this.highlightRingLayer,
          textureName: "ring",
          position: position,
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
          scale: new Vector2(0.2, 0.2),
          alpha: 1,
          depth: 0
        })
      }

      if (characterComponent != null) {
        const agentControlledComponentData = AgentControlledComponent.getDataOrNull(entity)

        this.renderCharacter(
          positionComponent.data,
          simulationTime,
          entity,
          renderContext,
          characterComponent.data,
          agentControlledComponentData,
          position,
          fogOfWarAlpha,
          perspectiveEntity
        )
      } else if (itemComponent != null) {
        this.renderItem(
          simulationTime,
          entity,
          renderContext,
          itemComponent.data,
          position,
          fogOfWarAlpha
        )
      }
    }

    if (this.dynamicState.selectedEntityId != null) {
      const selectedVisibleFogOfWarEntity = this.fogOfWarVisibleEntitiesById[this.dynamicState.selectedEntityId]

      if (!selectedVisibleFogOfWarEntity) {
        this.context.setSelectedEntityId(null)
      }
    }

    var anyChangedFogOfWarEntities = false

    for (let fogOfWarVisibleEntityId in this.fogOfWarVisibleEntitiesById) {
      if (!previousFogOfWarVisibleEntitiesById[fogOfWarVisibleEntityId]) {
        anyChangedFogOfWarEntities = true
      }
    }

    for (let previousFogOfWarVisibleEntityId in previousFogOfWarVisibleEntitiesById) {
      if (!this.fogOfWarVisibleEntitiesById[previousFogOfWarVisibleEntityId]) {
        anyChangedFogOfWarEntities = true
      }
    }

    if (anyChangedFogOfWarEntities) {
      this.dynamicState.forceUpdate()
    }
  }

  renderDebugInfo(): boolean {
    const debugInfoEntity = this.simulation.getEntityOrNull("debug-info")
    if (debugInfoEntity == null) {
      return false
    }

    const renderContext = this.renderContext

    const debugInfoComponentData = debugInfoEntity.getComponent<DebugInfoComponentData>("DebugInfoComponentData").data
    const collisionMapDebugInfo = debugInfoComponentData.collisionMapDebugInfo

    var cellIndex = 0
    for (let cell of collisionMapDebugInfo.cells) {
      const spriteScale = Vector2.timesScalar(collisionMapDebugInfo.cellSize, 1.0 / 64.0)

      renderContext.renderSprite("cell:" + cellIndex, {
        layer: this.observationRingsLayer,
        textureName: "circle",
        position: cell.center,
        scale: spriteScale,
        alpha: cell.occupied ? 0.75 : 0.3,
        depth: 0
      })

      ++cellIndex
    }

    return collisionMapDebugInfo.cells.length > 0
  }

  render() {
    this.renderContext.render(() => {
      const renderContext = this.renderContext

      const perspectiveEntity: Entity | null = this.dynamicState.perspectiveEntity

      let fogOfWarInfo: FogOfWarInfo | null
      if (perspectiveEntity != null) {
        fogOfWarInfo = {
          center: resolveEntityPositionForCurrentTime(perspectiveEntity),
          radius: CharacterComponent.getData(perspectiveEntity).observationRadius
        }
      } else {
        fogOfWarInfo = null
      }

      this.renderWorldBounds(renderContext)

      if (fogOfWarInfo != null) {
        this.renderFogOfWar(fogOfWarInfo, renderContext);
      }

      const didRenderDebugInfo = this.renderDebugInfo()
      this.renderEntities(didRenderDebugInfo, fogOfWarInfo, perspectiveEntity)
    })
  }

  private renderWorldBounds(renderContext: RenderContext) {
    const fogOfWarDistance = Vector2.timesScalar(this.simulation.worldBounds, 0.5)
    const fogOfWarTextureSize = 2048.0
    const fogOfWarTextureOpenPercent = 1024.0 / fogOfWarTextureSize

    const fogOfWarInfo = {
      center: Vector2.timesScalar(this.simulation.worldBounds, 0.5)
    }

    const extraFogOfWarSize = 10000.0
    const extraFogOfWarSideHeight = extraFogOfWarSize * 2 + fogOfWarDistance.y / fogOfWarTextureOpenPercent
    const extraFogOfWarTextureSize = 128.0
    renderContext.renderSprite("bounds-left", {
      layer: this.highlightRingLayer,
      textureName: "black-square",
      position: Vector2.plus(fogOfWarInfo.center, new Vector2(-(fogOfWarDistance.x / fogOfWarTextureOpenPercent) * 0.5 - extraFogOfWarSize * 0.5, 0)),
      scale: new Vector2(extraFogOfWarSize / extraFogOfWarTextureSize, extraFogOfWarSideHeight / extraFogOfWarTextureSize),
      alpha: 0.5,
      depth: 10000
    })

    renderContext.renderSprite("bounds-right", {
      layer: this.highlightRingLayer,
      textureName: "black-square",
      position: Vector2.plus(fogOfWarInfo.center, new Vector2((fogOfWarDistance.x / fogOfWarTextureOpenPercent) * 0.5 + extraFogOfWarSize * 0.5, 0)),
      scale: new Vector2(extraFogOfWarSize / extraFogOfWarTextureSize, extraFogOfWarSideHeight / extraFogOfWarTextureSize),
      alpha: 0.5,
      depth: 10000
    })

    renderContext.renderSprite("bounds-top", {
      layer: this.highlightRingLayer,
      textureName: "black-square",
      position: Vector2.plus(fogOfWarInfo.center, new Vector2(0, (fogOfWarDistance.y / fogOfWarTextureOpenPercent) * 0.5 + extraFogOfWarSize * 0.5)),
      scale: new Vector2(fogOfWarDistance.x / fogOfWarTextureOpenPercent / extraFogOfWarTextureSize, extraFogOfWarSize / extraFogOfWarTextureSize),
      alpha: 0.5,
      depth: 10000
    })

    renderContext.renderSprite("bounds-bottom", {
      layer: this.highlightRingLayer,
      textureName: "black-square",
      position: Vector2.plus(fogOfWarInfo.center, new Vector2(0, -(fogOfWarDistance.y / fogOfWarTextureOpenPercent) * 0.5 - extraFogOfWarSize * 0.5)),
      scale: new Vector2(fogOfWarDistance.x / fogOfWarTextureOpenPercent / extraFogOfWarTextureSize, extraFogOfWarSize / extraFogOfWarTextureSize),
      alpha: 0.5,
      depth: 10000
    })
  }

  private renderFogOfWar(fogOfWarInfo: FogOfWarInfo, renderContext: RenderContext) {
    const fogOfWarDistance = fogOfWarInfo.radius * 2.0
    const fogOfWarTextureSize = 2048.0
    const fogOfWarTextureOpenPercent = 1024.0 / fogOfWarTextureSize

    const spriteScale = Vector2.uniform(fogOfWarDistance / fogOfWarTextureSize / fogOfWarTextureOpenPercent)

    renderContext.renderSprite("fog-of-war", {
      layer: this.highlightRingLayer,
      textureName: "fog-of-war",
      position: fogOfWarInfo.center,
      scale: spriteScale,
      alpha: 0.5,
      depth: 10000
    })

    const extraFogOfWarSize = 10000.0
    const extraFogOfWarSideHeight = extraFogOfWarSize * 2 + fogOfWarDistance / fogOfWarTextureOpenPercent
    const extraFogOfWarTextureSize = 128.0
    renderContext.renderSprite("fog-of-war-left", {
      layer: this.highlightRingLayer,
      textureName: "black-square",
      position: Vector2.plus(fogOfWarInfo.center, new Vector2(-(fogOfWarDistance / fogOfWarTextureOpenPercent) * 0.5 - extraFogOfWarSize * 0.5, 0)),
      scale: new Vector2(extraFogOfWarSize / extraFogOfWarTextureSize, extraFogOfWarSideHeight / extraFogOfWarTextureSize),
      alpha: 0.5,
      depth: 10000
    })

    renderContext.renderSprite("fog-of-war-right", {
      layer: this.highlightRingLayer,
      textureName: "black-square",
      position: Vector2.plus(fogOfWarInfo.center, new Vector2((fogOfWarDistance / fogOfWarTextureOpenPercent) * 0.5 + extraFogOfWarSize * 0.5, 0)),
      scale: new Vector2(extraFogOfWarSize / extraFogOfWarTextureSize, extraFogOfWarSideHeight / extraFogOfWarTextureSize),
      alpha: 0.5,
      depth: 10000
    })

    renderContext.renderSprite("fog-of-war-top", {
      layer: this.highlightRingLayer,
      textureName: "black-square",
      position: Vector2.plus(fogOfWarInfo.center, new Vector2(0, (fogOfWarDistance / fogOfWarTextureOpenPercent) * 0.5 + extraFogOfWarSize * 0.5)),
      scale: new Vector2(fogOfWarDistance / fogOfWarTextureOpenPercent / extraFogOfWarTextureSize, extraFogOfWarSize / extraFogOfWarTextureSize),
      alpha: 0.5,
      depth: 10000
    })

    renderContext.renderSprite("fog-of-war-bottom", {
      layer: this.highlightRingLayer,
      textureName: "black-square",
      position: Vector2.plus(fogOfWarInfo.center, new Vector2(0, -(fogOfWarDistance / fogOfWarTextureOpenPercent) * 0.5 - extraFogOfWarSize * 0.5)),
      scale: new Vector2(fogOfWarDistance / fogOfWarTextureOpenPercent / extraFogOfWarTextureSize, extraFogOfWarSize / extraFogOfWarTextureSize),
      alpha: 0.5,
      depth: 10000
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
    position: Vector2,
    fogOfWarAlpha: number
  ) {
    const killedAtTime = KillableComponent.getDataOrNull(entity)?.killedAtTime
    const grower = GrowerComponent.getDataOrNull(entity)

    const itemConfig = this.getConfig<ItemConfig>(itemComponent.itemConfigKey, "ItemConfig")
    const spriteConfigKey = itemConfig.spriteConfigKey
    const spriteConfig = this.getConfig<SpriteConfig>(spriteConfigKey, "SpriteConfig")

    const timeSinceKilled = killedAtTime != null ? Math.max(0, simulationTime - killedAtTime) : null

    const deathAnimationTime = 0.5

    const baseDepth = this.calculateDepthForPosition(position)

    renderContext.renderSprite("item_" + entity.entityId + "_" + spriteConfig.key, {
      layer: this.mainLayer,
      depth: baseDepth + spriteConfig.depthOffset,
      textureName: spriteConfigKey,
      position: Vector2.plus(position, spriteConfig.baseOffset),
      animation: null,
      scale: timeSinceKilled != null
        ? Vector2.timesScalar(spriteConfig.baseScale, lerp(1, 0.7, timeSinceKilled / deathAnimationTime))
        : spriteConfig.baseScale,
      alpha: (timeSinceKilled != null ?
        lerp(1, 0.0, timeSinceKilled / deathAnimationTime)
        : 1.0) * fogOfWarAlpha
    })

    if (grower != null && grower.activeGrowth != null) {
      const activeGrowthItemConfig = this.getConfig<ItemConfig>(grower.activeGrowth.itemConfigKey, "ItemConfig")
      const growableConfig = activeGrowthItemConfig.growableConfig

      if (growableConfig == null) {
        throw new Error("growableConfig is null for an active growth: " + activeGrowthItemConfig.key)
      }

      const timeToGrow = growableConfig?.timeToGrow ?? 1
      const growAge = Math.max(0, simulationTime - grower.activeGrowth.startTime)
      const growPercent = clampZeroOne(growAge / timeToGrow)

      const growIntoItemConfig = this.getConfig<ItemConfig>(growableConfig.growsIntoItemConfigKey, "ItemConfig")
      const growIntoSpriteConfig = this.getConfig<SpriteConfig>(growIntoItemConfig.spriteConfigKey, "SpriteConfig")

      renderContext.renderSprite("item_" + entity.entityId + "_" + growIntoSpriteConfig.key + "_activeGrowth", {
        layer: this.mainLayer,
        depth: baseDepth + 0.001,
        textureName: growIntoSpriteConfig.key,
        position: Vector2.plus(position, spriteConfig.baseOffset),
        animation: null,
        scale: Vector2.timesScalar(growIntoSpriteConfig.baseScale, lerp(0.5, 1.0, growPercent)),
        alpha: lerp(0.5, 1.0, growPercent) * fogOfWarAlpha
      })
    }

    if (this.dynamicState.selectedEntityId === entity.entityId ||
      this.calculatedAutoInteraction?.targetEntity === entity) {
      renderContext.renderText("item-name:" + entity.entityId, {
        depth: baseDepth,
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
    agentControlledComponentData: AgentControlledComponentData | null,
    position: Vector2,
    fogOfWarAlpha: number,
    perspectiveEntity: Entity | null
  ) {
    const mainCamera = this.mainCamera

    if (entity === perspectiveEntity) {
      positionComponentData.positionAnimation.keyFrames.forEach((keyFrame, index) => {
        if (keyFrame.time > simulationTime) {
          const spriteKey = entity.entityId + "-position-animation-" + index

          renderContext.renderSprite(spriteKey, {
            textureName: "circle",
            layer: this.navigationPathLayer,
            position: keyFrame.value,
            scale: new Vector2(0.2, 0.2),
            alpha: 0.5,
            depth: 0
          })
        }
      })
    }

    const emoji = characterComponentData.facialExpressionEmoji
    let statusSuffix = ""

    if (agentControlledComponentData != null) {
      if (agentControlledComponentData.agentIntegrationStatus != null) {
        if (agentControlledComponentData.agentIntegrationStatus.includes("waiting_for_agent")) {
          // statusSuffix += "🕒"
        } else if (agentControlledComponentData.agentIntegrationStatus === "paused") {
          statusSuffix += "⏸️"
        } else if (agentControlledComponentData.agentIntegrationStatus === "error_from_remote_agent") {
          statusSuffix += "⚠️"
        } else if (agentControlledComponentData.agentIntegrationStatus === "exception") {
          statusSuffix += "🚫"
        }
      }

      if (agentControlledComponentData.agentStatus != null) {
        if (agentControlledComponentData.agentStatus.includes("running-prompt")) {
          statusSuffix += "💭"
        } else if (agentControlledComponentData.agentStatus === "prompt-finished") {
          // statusSuffix += "✅"
        } else if (agentControlledComponentData.agentStatus === "running-script") {
          statusSuffix += "📜"
        } else if (agentControlledComponentData.agentStatus === "script-done") {
          statusSuffix += "📜✅"
        } else if (agentControlledComponentData.agentStatus === "waiting-for-action") {
          statusSuffix += "📜🕒"
        } else if (agentControlledComponentData.agentStatus === "action-done") {
          statusSuffix += "📜✔️"
        } else if (agentControlledComponentData.agentStatus === "exception") {
          statusSuffix += "🚫🤖"
        } else if (agentControlledComponentData.agentStatus === "script-exception") {
          statusSuffix += "🚫📜"
        } else if (agentControlledComponentData.agentStatus.includes("updating-memory")) {
          statusSuffix += "📝️"
        } else if (agentControlledComponentData.agentStatus === "update-memory-success") {
          // statusSuffix += "🟢"
        }
      }

      if (agentControlledComponentData.agentError != null) {
        statusSuffix += "❌"
      }

      if (agentControlledComponentData.wasRateLimited) {
        statusSuffix += "📉" // ⏳
      }
    }

    const userControlledComponent = entity.getComponentDataOrNull<UserControlledComponentData>("UserControlledComponentData")

    if (fogOfWarAlpha > 0.01) {
      const perspectiveColor = "#F3D16A"
      const botColor = "#BCEBD2"
      const playerColor = "white"

      renderContext.renderText("character-name:" + entity.entityId, {
        depth: this.calculateDepthForPosition(position),
        layer: this.characterNameLayer,
        text: (emoji != null ? (emoji + " ") : "") + characterComponentData.name + statusSuffix,
        strokeThickness: 3,
        fontSize: 20,
        useUiCamera: false,
        position: Vector2.plus(position, new Vector2(0, 15)),
        origin: new Vector2(0.5, 0),
        scale: Vector2.timesScalar(Vector2.one, 1.0 / mainCamera.zoom),
        color: entity.entityId === perspectiveEntity?.entityId
            ? perspectiveColor
            : userControlledComponent == null
            ? playerColor
            : botColor,
        alpha: fogOfWarAlpha
      })

      const maxChatBubbleAge = 10

      const spokenMessages = characterComponentData.recentSpokenMessages

      for (let messageIndex = 0; messageIndex < spokenMessages.length; messageIndex++) {
        const message = spokenMessages[messageIndex]
        const chatMessageAge = simulationTime - message.sentSimulationTime

        if (chatMessageAge >= maxChatBubbleAge) {
          continue
        }

        const fadeInTime = 0.25
        const fadeInPercent = lerp(0, 1, chatMessageAge / fadeInTime)
        const fadeOutTime = 0.25
        const fadeOutPercent = lerp(0, 1, (chatMessageAge - maxChatBubbleAge + fadeOutTime) / fadeOutTime)

        let interruptFadeOutPercent = 0
        if (messageIndex !== (spokenMessages.length - 1)) {
          const interruptedByMessage = spokenMessages[messageIndex + 1]
          const interruptedByMessageAge = simulationTime - interruptedByMessage.sentSimulationTime
          const interruptFadeOutTime = 0.15
          interruptFadeOutPercent = lerp(0, 1, interruptedByMessageAge / interruptFadeOutTime)
        }

        const animationAlpha = lerp(lerp(0, 1, fadeInPercent), 0, fadeOutPercent + interruptFadeOutPercent)

        // jshmrsn: I wasn't able to get scaling to work well with the text and bubble, so just using a subtle scale
        // factor and fast animations to hide the weirdness
        const animationScale = lerp(lerp(0.85, 1, fadeInPercent), 0.85, fadeOutPercent + interruptFadeOutPercent) *
          lerp(1.15, 1.0, Math.pow(fadeInPercent, 2.0))

        var spokenMessageText = message.message

        const maxBubbleWidth = 250
        const minBubbleWidth = 70
        const bubblePaddingY = 6
        const bubblePaddingX = 6

        const textScale = 1.0

        const chatBubbleDepth = this.calculateDepthForPosition(position)

        const content = renderContext.renderText("character-spoken-message-text:" + entity.entityId + spokenMessageText + ":" + messageIndex, {
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
          },
          alpha: animationAlpha
        })

        const textBounds = content.getBounds()
        const bubbleWidth = Math.max(minBubbleWidth, Math.min(maxBubbleWidth, textBounds.width + bubblePaddingX * 2))

        const bubbleHeight = textBounds.height + bubblePaddingY * 2
        const arrowHeight = bubbleHeight / 4 + 5
        const arrowPointOffsetX = bubbleWidth / 8

        const bubble = renderContext.renderGraphics("character-spoken-message-bubble:" + entity.entityId + ":" + spokenMessageText + ":" + messageIndex, this.chatBubbleLayer, bubble => {
          // Bubble shadow
          bubble.clear()
          bubble.fillStyle(0x222222, 0.35)
          bubble.fillRoundedRect(3, 3, bubbleWidth, bubbleHeight, 8)

          //  Bubble color
          bubble.fillStyle(0xffffff, 1)

          //  Bubble outline line style
          bubble.lineStyle(4, 0x565656, 1)

          //  Bubble shape and outline
          bubble.strokeRoundedRect(0, 0, bubbleWidth, bubbleHeight, 8)
          bubble.fillRoundedRect(0, 0, bubbleWidth, bubbleHeight, 8)

          const point1X = Math.floor(arrowPointOffsetX)
          const point1Y = bubbleHeight
          const point2X = Math.floor(arrowPointOffsetX * 2)
          const point2Y = bubbleHeight
          const point3X = Math.floor(arrowPointOffsetX)
          const point3Y = Math.floor(bubbleHeight + arrowHeight)

          //  Bubble arrow shadow (disabled because it causes artifacts)
          // bubble.lineStyle(4, 0x222222, 0.5)
          // bubble.lineBetween(point2X - 1, point2Y + 6, point3X + 2, point3Y)

          //  Bubble arrow fill
          bubble.fillTriangle(point1X, point1Y, point2X, point2Y, point3X, point3Y)
          bubble.lineStyle(2, 0x565656, 1)
          bubble.lineBetween(point2X, point2Y, point3X, point3Y)
          bubble.lineBetween(point1X, point1Y, point3X, point3Y)
        })

        bubble.setScale(animationScale, animationScale)
        bubble.setAlpha(animationAlpha * fogOfWarAlpha)
        bubble.setDepth(chatBubbleDepth)
        bubble.setX(position.x - arrowPointOffsetX)
        bubble.setY(position.y - 35 - bubbleHeight - arrowHeight)

        content.setPosition(bubble.x + (bubbleWidth / 2) - (textBounds.width / 2), bubble.y + (bubbleHeight / 2) - (textBounds.height / 2))
      }

      if (agentControlledComponentData != null && this.dynamicState.selectedEntityId === entity.entityId) {
        const observationCircleScale = agentControlledComponentData.observationDistance * 2.0 / 500.0

        renderContext.renderSprite("agent-observation-distance-circle:" + entity.entityId, {
          layer: this.observationRingsLayer,
          textureName: "ring",
          position: position,
          scale: new Vector2(observationCircleScale, observationCircleScale),
          alpha: 0.3 * fogOfWarAlpha,
          depth: 0
        })
      }

      const characterDepth = this.calculateDepthForPosition(position)

      let keyFrames = positionComponentData.positionAnimation.keyFrames
      let animationRepeat = -1

      let isMoving = false
      let movementAnimationDirection = "down"

      if (keyFrames.length <= 1) {
      } else {
        const first = keyFrames[0]
        const last = keyFrames[keyFrames.length - 1]


        let firstKeyFrameWithChange = first
        for (let keyFrame of keyFrames) {
          if (Vector2.distance(keyFrame.value, position) > 0.1) {
            firstKeyFrameWithChange = keyFrame
            break
          }
        }

        let withinPositionAnimationRange: boolean

        let from: Vector2
        let to: Vector2

        if (simulationTime <= firstKeyFrameWithChange.time) {
          from = position
          to = firstKeyFrameWithChange.value
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
        isMoving = magnitude > 0.001 && withinPositionAnimationRange
        movementAnimationDirection = getAnimationDirectionForRelativeLocation(delta) ?? movementAnimationDirection
      }

      let animationName = isMoving ? "walk-" + movementAnimationDirection : movementAnimationDirection

      let performedAction = characterComponentData.performedAction
      if (performedAction != null) {
        if (simulationTime - performedAction.startedAtSimulationTime > performedAction.duration) {
          performedAction = null
        }
      }

      const inventoryComponent = entity.getComponentDataOrNull<InventoryComponentData>("InventoryComponentData")
      let equippedToolItemConfig: ItemConfig | null = null

      if (inventoryComponent != null) {
        for (let itemStack of inventoryComponent.inventory.itemStacks) {
          if (itemStack.isEquipped) {
            const equippedItemConfig = this.simulation.getConfig<ItemConfig>(itemStack.itemConfigKey, "ItemConfig")

            if (equippedItemConfig.equippableConfig &&
              equippedItemConfig.equippableConfig.equipmentSlot === EquipmentSlots.Tool) {
              equippedToolItemConfig = equippedItemConfig
            }

            if (equippedItemConfig.equippableConfig != null &&
              equippedItemConfig.equippableConfig.equippedCompositeAnimation == null) {
              const spriteConfig = this.getConfig<SpriteConfig>(equippedItemConfig.spriteConfigKey, "SpriteConfig")

              const holdOffset = movementAnimationDirection === "left"
                ? new Vector2(-10, -5)
                : movementAnimationDirection === "right"
                  ? new Vector2(-10, -5)
                  : movementAnimationDirection === "up"
                    ? new Vector2(10, -5)
                    : new Vector2(-10, -5)

              renderContext.renderSprite("equipped-item:" + entity.entityId + ":" + itemStack.itemConfigKey, {
                layer: this.mainLayer,
                textureName: equippedItemConfig.spriteConfigKey,
                position: Vector2.plus(position, holdOffset),
                scale: Vector2.timesScalar(spriteConfig.baseScale, 0.65),
                alpha: fogOfWarAlpha,
                depth: this.calculateDepthForPosition(position) + 1
              })
            }
          }
        }
      }

      if (equippedToolItemConfig != null &&
        equippedToolItemConfig.spawnItemOnUseConfig &&
        userControlledComponent &&
        userControlledComponent.userId === this.dynamicState.userId) {
        const spawnItemConfigKey = equippedToolItemConfig.spawnItemOnUseConfig.spawnItemConfigKey
        const spawnItemConfig = this.getConfig<ItemConfig>(spawnItemConfigKey, "ItemConfig")

        const spawnItemSpriteConfig = this.getConfig<SpriteConfig>(spawnItemConfig.spriteConfigKey, "SpriteConfig")

        const nearestEntities = getNearestEntitiesFromList(
          this.fogOfWarVisibleEntities,
          position,
          100.0, // TODO: has to match value in useEquippedToolItem in simulation server Kotlin
          entityToCheck => {
            const entityToCheckItemConfigKey = ItemComponent.getDataOrNull(entityToCheck)?.itemConfigKey
            if (entityToCheckItemConfigKey == null) {
              return false
            }
            const itemConfigToCheck = this.getConfig<ItemConfig>(entityToCheckItemConfigKey, "ItemConfig")

            return itemConfigToCheck.blocksPlacement
          }
        )

        const isValid = nearestEntities.length === 0

        renderContext.renderSprite("spawn_item_on_use_preview_" + entity.entityId + "_" + spawnItemSpriteConfig.key, {
          layer: this.mainLayer,
          depth: characterDepth + spawnItemSpriteConfig.depthOffset,
          textureName: spawnItemSpriteConfig.key,
          position: Vector2.plus(position, spawnItemSpriteConfig.baseOffset),
          animation: null,
          scale: spawnItemSpriteConfig.baseScale,
          alpha: isValid ? 0.35 : 0.1
        })
      }

      if (performedAction != null) {
        const targetEntity = performedAction.targetEntityId != null
          ? this.simulation.getEntityOrNull(performedAction.targetEntityId)
          : null

        let actionAnimationDirection = "down"
        if (targetEntity != null) {
          const targetEntityPosition = resolveEntityPositionForCurrentTime(targetEntity)
          const delta = Vector2.minus(targetEntityPosition, position)

          actionAnimationDirection = getAnimationDirectionForRelativeLocation(delta) ?? actionAnimationDirection
        }

        let actionAnimationBaseName = "pickup"
        animationRepeat = 0

        if (performedAction.actionType === ActionTypes.UseEquippedTool) {
          actionAnimationBaseName = equippedToolItemConfig?.useCustomAnimationBaseName ?? "thrust"
        } else if (performedAction.actionType === ActionTypes.UseToolToDamageEntity) {
          actionAnimationBaseName = equippedToolItemConfig?.useCustomAnimationBaseName ?? "slash"
        } else if (performedAction.actionType === ActionTypes.PlaceGrowableInGrower) {
          actionAnimationBaseName = "pickup"
        } else if (performedAction.actionType === ActionTypes.DropItem) {
          actionAnimationBaseName = "pickup"
        } else if (performedAction.actionType === ActionTypes.PickupItem) {
          actionAnimationBaseName = "pickup"
        } else if (performedAction.actionType === ActionTypes.EquipItem) {
          actionAnimationBaseName = "equip"
        }

        animationName = actionAnimationBaseName + "-" + actionAnimationDirection
      }


      const preferredCompositeAnimationCategory = characterComponentData.bodySelections.bodyType
      const offset = new Vector2(0.0, -20.0)
      const scale = new Vector2(1.2, 1.2)

      const positionWithOffset = Vector2.plus(position, offset)

      const renderCompositeAnimation = (spriteKeySuffix: string, compositeAnimation: CompositeAnimationSelection) => {
        const sheetDefinition = this.sheetDefinitionsByKey[compositeAnimation.key]

        if (sheetDefinition == null) {
          throw new Error("sheetDefinition not found: " + compositeAnimation.key)
        }

        const variant = compositeAnimation.variant

        sheetDefinition.layers.forEach((layer, layerIndex) => {
          if (layer.animationConfigsByName[animationName]) {
            const textureKeysByCategory = layer.textureKeysByCategoryByVariant[variant]

            if (textureKeysByCategory == null) {
              throw new Error(`textureKeysByCategory is null for variant ${variant} for composite animation ${compositeAnimation.key} layer ${layerIndex}`)
            }

            const textureKey = textureKeysByCategory[preferredCompositeAnimationCategory] ?? textureKeysByCategory["male"]

            if (textureKey) {
              let animationKey = textureKey + "_" + animationName

              let animationConfig: Phaser.Types.Animations.PlayAnimationConfig = {
                key: animationKey,
                repeat: animationRepeat
              }

              const spriteKey = entity.entityId + "-composite-animation-layer-" + layerIndex + textureKey + ":" + spriteKeySuffix

              renderContext.renderSprite(spriteKey, {
                layer: this.mainLayer,
                depth: characterDepth + layer.zPos * 0.0001,
                textureName: textureKey,
                position: positionWithOffset,
                animation: animationConfig,
                scale: scale,
                filterMode: this.zoomValue > 1.25 ? FilterMode.NEAREST : FilterMode.LINEAR,
                alpha: fogOfWarAlpha
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

      renderContext.renderSprite("character-shadow:" + entity.entityId, {
        layer: this.mainLayer,
        textureName: "character-shadow",
        position: Vector2.plus(position, new Vector2(-2, 10)),
        scale: new Vector2(0.45, 0.35),
        alpha: 0.45 * fogOfWarAlpha,
        depth: characterDepth - 0.01
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

      if (inventoryComponent != null) {
        var hasPants = false
        var hasShirt = false

        for (let itemStack of inventoryComponent.inventory.itemStacks) {
          if (itemStack.isEquipped) {
            const equippedItemConfig = this.simulation.getConfig<ItemConfig>(itemStack.itemConfigKey, "ItemConfig")

            if (equippedItemConfig.equippableConfig) {
              if (equippedItemConfig.equippableConfig.equipmentSlot === EquipmentSlots.Pants) {
                hasPants = true
              } else if (equippedItemConfig.equippableConfig.equipmentSlot === EquipmentSlots.Chest) {
                hasShirt = true
              }

              if (equippedItemConfig.equippableConfig.equippedCompositeAnimation != null) {
                renderCompositeAnimation("equipped-item", equippedItemConfig.equippableConfig.equippedCompositeAnimation)
              }
            }
          }
        }

        if (!hasShirt) {
          renderCompositeAnimation("default-shirt", {
            key: "torso_clothes_male_sleeveless_laced",
            variant: "white"
          })
        }

        if (!hasPants) {
          renderCompositeAnimation("default-pants", {
            key: "legs_pantaloons",
            variant: "white"
          })
        }
      }
    }
  }

  update(time: number, deltaMs: number) {
    const deltaTime = deltaMs / 1000.0

    this.scrollVelocity = Vector2.lerp(this.scrollVelocity, Vector2.zero, deltaTime * 5.0)

    if (!this.isDragging) {
      if (Vector2.magnitude(this.scrollVelocity) < 3) {
        this.scrollVelocity = Vector2.zero
      }

      const scrollMovement = Vector2.timesScalar(this.scrollVelocity, deltaTime)
      this.mainCamera.scrollX += scrollMovement.x
      this.mainCamera.scrollY += scrollMovement.y
    }

    this.zoomVelocity = lerp(this.zoomVelocity, 1.0, deltaTime * 6.0)

    if (!this.isZooming) {
      this.zoomValue *= this.zoomVelocity
    }

    this.simulation.update(deltaTime)

    const autoInteraction = this.calculateAutoInteractAction()

    const previousAutoInteraction = this.calculatedAutoInteraction
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

    const camera_speed = 800
    const cursors = this.cursorKeys
    const mainCamera = this.cameras.main

    if (cursors) {
      if (cursors.left.isDown) {
        mainCamera.scrollX -= camera_speed * deltaTime
      }

      if (cursors.right.isDown) {
        mainCamera.scrollX += camera_speed * deltaTime
      }

      if (cursors.up.isDown) {
        mainCamera.scrollY -= camera_speed * deltaTime
      }

      if (cursors.down.isDown) {
        mainCamera.scrollY += camera_speed * deltaTime
      }
    }

    if (this.input.keyboard) {
      this.input.keyboard.disableGlobalCapture()
    }

    this.clampCamera()

    this.render()
  }

  getCurrentSimulationTime(): number {
    return this.simulation.getCurrentSimulationTime()
  }
}


