import Phaser from "phaser";
import {Config, EntityId} from "../simulation/EntityData";

import {getUnixTimeSeconds, throwError} from "../misc/utils";
import {Simulation, SimulationId, UserId} from "../simulation/Simulation";
import {clampZeroOne, lerp, Vector2} from "../misc/Vector2";
import {Vector2Animation} from "../misc/Vector2Animation";
import {
  CharacterBodySelectionsConfig,
  CompositeAnimationRegistryConfig,
  RegisteredCompositeAnimation,
  SpriteAnimation,
  SpriteConfig
} from "../common/common";
import {RenderContext} from "../common/RenderContext";
import {
  ActionTypes,
  CharacterBodySelections,
  CharacterComponentData,
  InventoryComponentData,
  UseEquippedToolItemRequest
} from "./CharacterComponentData";
import {
  EquipmentSlots,
  GrowerComponent,
  ItemComponent,
  ItemComponentData,
  ItemConfig,
  KillableComponent
} from "./ItemComponentData";
import {Entity} from "../simulation/Entity";
import {
  PositionComponent,
  PositionComponentData,
  resolveEntityPositionForCurrentTime,
  resolveEntityPositionForTime,
  resolvePositionForTime
} from "../common/PositionComponentData";
import {AgentComponentData} from "./agentComponentData";
import {UserControlledComponent, UserControlledComponentData} from "./userControlledComponentData";
import {IconHandGrab, IconTool} from "@tabler/icons-react";
import {CompositeAnimationSelection} from "./CompositeAnimationSelection";
import {GameSimulation} from "./GameSimulation";
import Layer = Phaser.GameObjects.Layer;
import FilterMode = Phaser.Textures.FilterMode;
import {DynamicState} from "../components/DynamicState";

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
  actionIcon: JSX.Element | null
}

interface MoveToPointRequest {
  point: Vector2
  pendingUseEquippedToolItemRequest?: UseEquippedToolItemRequest
  pendingInteractionEntityId?: EntityId
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

export class SimulationScene extends Phaser.Scene {
  readonly simulationId: SimulationId
  readonly dynamicState: DynamicState
  readonly simulation: Simulation
  readonly isReplay: boolean

  focusChatTextAreaKey: Phaser.Input.Keyboard.Key | undefined = undefined

  private cursorKeys: Phaser.Types.Input.Keyboard.CursorKeys | null | undefined = null

  private onCreateFunctions: { (): void } [] = []
  private onLoadCompleteFunctions: { (): void } [] = []

  private simulationContext: SimulationSceneContext;
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

  private readonly worldBounds = new Vector2(10000.0, 10000.0)

  constructor(
    simulation: Simulation,
    context: SimulationSceneContext
  ) {
    super("SimulationPhaserScene");

    this.simulation = simulation
    this.isReplay = simulation.isReplay
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
    progressBox.fillStyle(0x222222, 0.2);
    const progressBoxWidth = 320
    const progressBoxHeight = 20
    const progressBoxX = screenWidth / 2 - progressBoxWidth / 2
    const progressBoxY = screenHeight / 2 - progressBoxHeight / 2
    progressBox.fillRect(progressBoxX, progressBoxY, progressBoxWidth, progressBoxHeight);

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
    });
    loadingText.setOrigin(0.5, 0.5);
    mainCamera.ignore(loadingText)

    // var percentText = this.make.text({
    //   x: screenWidth / 2,
    //   y: screenHeight / 2,
    //   text: '0%',
    //   style: {
    //     font: '18px monospace',
    //     color: '#ffffff'
    //   }
    // });
    // percentText.setOrigin(0.5, 0.5);
    // mainCamera.ignore(percentText)

    var assetText = this.make.text({
      x: screenWidth / 2,
      y: screenHeight / 2 + 50,
      text: '',
      style: {
        font: '18px ',
        color: 'black'
      },
      alpha: 0.25
    });
    assetText.setOrigin(0.5, 0.5)
    mainCamera.ignore(assetText)

    this.load.on('progress', (value: number) => {
      // joshr: Avoid errors modifying text if an error during preload causes simulation scene to be destroyed
      // before progress callbacks stop
      if (this.dynamicState.phaserScene === this) {
        // percentText.setText((value * 100).toFixed(0) + '%');
        progressBar.clear();
        progressBar.fillStyle(0xffffff, 1);

        const progressBarWidth = progressBoxWidth - 2
        const progressBarHeight = progressBoxHeight - 2
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
      // loadingText.destroy();
      // percentText.destroy();
      assetText.destroy();
      for (let onLoadCompleteFunction of this.onLoadCompleteFunctions) {
        onLoadCompleteFunction()
      }
    });

    this.load.image("circle", "/assets/misc/circle.png");
    this.load.image("ring", "/assets/misc/ring.png");
    this.load.image("character-shadow", "/assets/misc/character-shadow.png");

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
      const layers = sheetDefinitionsByKey[spriteDefinitionKey].layers
      const layer = layers[layers.length - 1]

      const partialPathsByCategory = layer.texturePartialPathsByCategoryByVariant[variant]
      const partialPath = partialPathsByCategory[category] ?? partialPathsByCategory["male"]
      const profileIconLayerPath = "/assets/liberated-pixel-cup-characters/profile-icons/" + partialPath + variant + ".png"
      result.push(profileIconLayerPath)
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
    const width = gameSize.width;
    const height = gameSize.height;

    this.cameras.resize(width, height);
  }

  sendMoveToPointRequest(moveToPointRequest: MoveToPointRequest) {
    if (this.isReplay) {
      return
    }

    this.simulationContext.sendWebSocketMessage("MoveToPointRequest", moveToPointRequest)
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
        this.clearPendingInteractionTargetRequest()
      } else {
        this.simulationContext.showMenuPanel()
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

        // console.log("Clicked worldPoint", worldPoint)
        const simulationTime = this.getCurrentSimulationTime()

        let nearestDistance = 10000.0
        let nearestEntity: Entity | null = null

        const simulation = this.simulation
        for (const entity of simulation.entities) {
          const positionComponent = entity.getComponentOrNull<PositionComponentData>("PositionComponentData")

          if (positionComponent != null) {
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
            this.clearPendingInteractionTargetRequest()
            this.simulationContext.setSelectedEntityId(nearestEntity.entityId)
          } else {
            this.simulationContext.setSelectedEntityId(null)
            this.clearPendingInteractionTargetRequest()
          }
        } else {
          this.lastClickTime = -1;
          this.clearPendingInteractionTargetRequest()

          this.sendMoveToPointRequest({
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

  private clearPendingInteractionTargetRequest() {
    if (this.isReplay) {
      return
    }

    this.simulationContext.sendWebSocketMessage("ClearPendingInteractionTargetRequest", {})
  }

  clampCamera() {
    const mainCamera = this.mainCamera
    mainCamera.scrollX = Math.min(Math.max(mainCamera.scrollX, 0), this.worldBounds.x)
    mainCamera.scrollY = Math.min(Math.max(mainCamera.scrollY, 0), this.worldBounds.y)
  }

  calculateAutoInteractAction(): AutoInteractAction | null {
    if (this.isReplay) {
      return null
    }

    const playerControlledEntity = this.simulation.entities.find(entity => {
      const userControlledComponent = entity.getComponentDataOrNull<UserControlledComponentData>("UserControlledComponentData")
      return userControlledComponent != null && userControlledComponent.userId === this.userId
    })

    if (playerControlledEntity == null) {
      return null
    }

    const playerInventoryComponent = playerControlledEntity.getComponentData<InventoryComponentData>("InventoryComponentData")

    const equippedToolItemConfig = playerInventoryComponent.inventory.itemStacks
      .filter(it => it.isEquipped)
      .map(itemStack => {
        return {
          itemStack: itemStack,
          itemConfig: this.getConfig<ItemConfig>(itemStack.itemConfigKey, "ItemConfig")
        }
      })
      .find(it => it.itemConfig.equippableConfig && it.itemConfig.equippableConfig.equipmentSlot === EquipmentSlots.Tool)?.itemConfig

    const playerPositionComponentData = PositionComponent.getData(playerControlledEntity)
    const playerPositionAnimation = playerPositionComponentData.positionAnimation

    const playerPosition = resolveEntityPositionForCurrentTime(playerControlledEntity)
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

    const simulation = this.simulation
    for (const targetEntity of simulation.entities) {
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
            } else if (targetItemConfig.killableConfig && targetItemConfig.killableConfig.canBeDamagedByToolItemConfigKey != null &&
              equippedToolItemConfig != null &&
              equippedToolItemConfig.key === targetItemConfig.killableConfig.canBeDamagedByToolItemConfigKey) {

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
      this.clearPendingInteractionTargetRequest()
    } else if (actionType === AutoInteractActionType.SelectEntity) {
      if (targetEntity != null) {
        this.simulationContext.setSelectedEntityId(targetEntity.entityId)
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
      const playerPositionAfterLatencyBuffer = resolveEntityPositionForTime(playerControlledEntity, simulationTime + 0.3)

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
        } else if (itemComponent != null) {
          this.renderItem(
            simulationTime,
            entity,
            renderContext,
            itemComponent.data,
            position
          )
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
      alpha: timeSinceKilled != null ?
        lerp(1, 0.0, timeSinceKilled / deathAnimationTime)
        : 1.0
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
        alpha: lerp(0.5, 1.0, growPercent)
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
        if (agentComponentData.agentStatus.includes("running-prompt")) {
          statusSuffix += "💭"
        } else if (agentComponentData.agentStatus === "prompt-finished") {
          // statusSuffix += "✅"
        } else if (agentComponentData.agentStatus.includes("updating-memory")) {
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

    const userControlledComponent = entity.getComponentDataOrNull<UserControlledComponentData>("UserControlledComponentData")

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
      color: userControlledComponent == null
        ? "white"
        : userControlledComponent.userId === this.userId
          ? "#F3D16A"
          : "#BCEBD2"
    })

    const maxChatBubbleAge = 10

    const spokenMessages = characterComponentData.recentSpokenMessages;

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

      const maxBubbleWidth = 250;
      const minBubbleWidth = 70;
      const bubblePaddingY = 6;
      const bubblePaddingX = 6;

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

      const textBounds = content.getBounds();
      const bubbleWidth = Math.max(minBubbleWidth, Math.min(maxBubbleWidth, textBounds.width + bubblePaddingX * 2));

      const bubbleHeight = textBounds.height + bubblePaddingY * 2;
      const arrowHeight = bubbleHeight / 4 + 5;
      const arrowPointOffsetX = bubbleWidth / 8;

      const bubble = renderContext.renderGraphics("character-spoken-message-bubble:" + entity.entityId + ":" + spokenMessageText + ":" + messageIndex, this.chatBubbleLayer, bubble => {
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

        //  Bubble arrow shadow (disabled because it causes artifacts)
        // bubble.lineStyle(4, 0x222222, 0.5);
        // bubble.lineBetween(point2X - 1, point2Y + 6, point3X + 2, point3Y);

        //  Bubble arrow fill
        bubble.fillTriangle(point1X, point1Y, point2X, point2Y, point3X, point3Y);
        bubble.lineStyle(2, 0x565656, 1);
        bubble.lineBetween(point2X, point2Y, point3X, point3Y);
        bubble.lineBetween(point1X, point1Y, point3X, point3Y);
      })

      bubble.setScale(animationScale, animationScale)
      bubble.setAlpha(animationAlpha)
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

    const characterDepth = this.calculateDepthForPosition(position)

    let keyFrames = positionComponentData.positionAnimation.keyFrames;
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

          if (equippedItemConfig.equippableConfig && equippedItemConfig.equippableConfig.equipmentSlot == EquipmentSlots.Tool) {
            equippedToolItemConfig = equippedItemConfig
          }

          if (equippedItemConfig.equippableConfig != null && equippedItemConfig.equippableConfig.equippedCompositeAnimation == null) {
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
              alpha: 1,
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

      const nearestEntities = (this.simulation as GameSimulation).getNearestEntities(
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

            const sprite = renderContext.renderSprite(spriteKey, {
              layer: this.mainLayer,
              depth: characterDepth + layer.zPos * 0.0001,
              textureName: textureKey,
              position: positionWithOffset,
              animation: animationConfig,
              scale: scale,
              filterMode: FilterMode.NEAREST
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
      alpha: 0.45,
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
