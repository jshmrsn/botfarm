import Phaser from "phaser"
import {Config, EntityId} from "../../simulation/EntityData"
import {SimulationId, UserId} from "../../simulation/Simulation"
import {Vector2} from "../../misc/Vector2"
import {RenderContext} from "../../common/RenderContext"
import {CharacterComponent, UseEquippedToolItemRequest} from "../CharacterComponentData"
import {ItemConfig} from "../ItemComponentData"
import {Entity} from "../../simulation/Entity"
import {resolveEntityPositionForCurrentTime} from "../../common/PositionComponentData"
import {GameSimulation} from "../GameSimulation"
import {DynamicState} from "../../components/DynamicState"
import {renderEntities} from "./renderEntities";
import {FogOfWarInfo, renderFogOfWar} from "./renderFogWar";
import {AutoInteraction} from "./AutoInteraction";
import {renderDebugInfo} from "./renderDebugInfo";
import {AssetLoader} from "./AssetLoader";
import {GameInput} from "./GameInput";
import Layer = Phaser.GameObjects.Layer;
import {renderWorldBounds} from "./renderWorldBounds";

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
}

interface MoveToPointRequest {
  point: Vector2
  pendingUseEquippedToolItemRequest?: UseEquippedToolItemRequest
  pendingInteractionEntityId?: EntityId
}


export class GameSimulationScene extends Phaser.Scene {
  readonly simulationId: SimulationId
  readonly dynamicState: DynamicState
  readonly simulation: GameSimulation
  readonly isReplay: boolean

  readonly autoInteraction: AutoInteraction


  private onCreateFunctions: { (): void } [] = []

  readonly context: SimulationSceneContext
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

  readonly renderContext = new RenderContext(this)
  private backgroundGrass!: Phaser.GameObjects.TileSprite

  private readonly worldBounds: Vector2
  readonly assetLoader: AssetLoader
  readonly gameInput: GameInput

  readonly fogOfWarVisibleEntities: Entity[] = []
  fogOfWarVisibleEntitiesById: Record<EntityId, Entity> = {}


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
    this.autoInteraction = new AutoInteraction(this)
    this.assetLoader = new AssetLoader(this)
    this.gameInput = new GameInput(this)
  }

  onCreate(onCreateFunction: () => void) {
    this.onCreateFunctions.push(onCreateFunction)
  }

  get calculatedAutoInteraction(): AutoInteractAction | null {
    return this.autoInteraction.calculatedAutoInteraction
  }

  preload() {
    this.assetLoader.preload()
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

  setDebugOverlayValueForKey(key: string, value: any | null) {
    this.dynamicState.setDebugOverlayValueForKey(key, value)
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

  zoomValue = 1.0
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

  autoInteract() {
    this.autoInteraction.autoInteract()
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

    this.gameInput.setupInput()
    this.clampCamera()
  }

  getConfig<T extends Config>(configKey: string, serverSerializationTypeName: string): T {
    return this.simulation.getConfig<T>(configKey, serverSerializationTypeName)
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

      renderWorldBounds(this, renderContext)

      if (fogOfWarInfo != null) {
        renderFogOfWar(this, fogOfWarInfo, renderContext);
      }

      const didRenderDebugInfo = renderDebugInfo(this)
      renderEntities(this, didRenderDebugInfo, fogOfWarInfo, perspectiveEntity)
    })
  }

  calculateDepthForPosition(position: Vector2): number {
    return position.y
  }

  update(time: number, deltaMs: number) {
    const deltaTime = deltaMs / 1000.0

    this.gameInput.update(deltaTime)

    this.simulation.update(deltaTime)

    this.autoInteraction.update()

    this.clampCamera()

    this.render()
  }

  getCurrentSimulationTime(): number {
    return this.simulation.getCurrentSimulationTime()
  }
}
