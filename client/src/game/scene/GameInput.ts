import {GameSimulationScene} from "./GameSimulationScene";
import {DynamicState} from "../ui/DynamicState";
import {GameSimulation} from "../simulation/GameSimulation";
import {Pinch} from "phaser3-rex-plugins/plugins/gestures";
import {lerp, Vector2} from "../../misc/Vector2";
import {PositionComponent, resolvePositionForTime} from "../../common/PositionComponentData";
import {CharacterComponent} from "../simulation/CharacterComponentData";
import {getUnixTimeSeconds} from "../../misc/utils";
import Phaser from "phaser";
import InputPlugin = Phaser.Input.InputPlugin;

export class GameInput {
  readonly scene: GameSimulationScene
  readonly dynamicState: DynamicState
  readonly simulation: GameSimulation
  private pinch?: Pinch

  focusChatTextAreaKey: Phaser.Input.Keyboard.Key | undefined = undefined
  private cursorKeys: Phaser.Types.Input.Keyboard.CursorKeys | null | undefined = null

  private scrollVelocity: Vector2 = Vector2.zero
  private zoomVelocity: number = 0.0
  private isZooming = false
  private isDragging = false
  private lastClickTime: number = -1.0

  private get input(): InputPlugin {
    return this.scene.input
  }


  constructor(scene: GameSimulationScene) {
    this.scene = scene
    this.dynamicState = scene.dynamicState
    const simulation = scene.simulation;
    this.simulation = simulation
  }

  sendNotifyClientActiveRequest() {
    if (this.scene.isReplay) {
      return
    }

    this.scene.context.sendWebSocketMessage("NotifyClientActive", {})
  }

  update(deltaTime: number) {
    const mainCamera = this.scene.mainCamera

    this.scrollVelocity = Vector2.lerp(this.scrollVelocity, Vector2.zero, deltaTime * 5.0)

    if (!this.isDragging) {
      if (Vector2.magnitude(this.scrollVelocity) < 3) {
        this.scrollVelocity = Vector2.zero
      }

      const scrollMovement = Vector2.timesScalar(this.scrollVelocity, deltaTime)
      mainCamera.scrollX += scrollMovement.x
      mainCamera.scrollY += scrollMovement.y
    }

    this.zoomVelocity = lerp(this.zoomVelocity, 1.0, deltaTime * 6.0)

    if (!this.isZooming) {
      this.scene.zoomValue *= this.zoomVelocity
    }
  }

  setupInput() {
    const mainCamera = this.scene.mainCamera
    const sceneContext = this.scene.context
    const scene = this.scene

    this.cursorKeys = this.input.keyboard?.createCursorKeys()

    this.pinch = new Pinch(this.scene, {})

    this.pinch.on("drag1", (drag: any) => {
      const drag1Vector: { x: number, y: number } = drag.drag1Vector
      const scrollMovement = new Vector2(-drag1Vector.x / mainCamera.zoom, -drag1Vector.y / mainCamera.zoom)
      this.isDragging = true
      const dt = 1.0 / 60.0
      this.scrollVelocity = Vector2.timesScalar(scrollMovement, 1.0 / dt)
      mainCamera.scrollX += scrollMovement.x
      mainCamera.scrollY += scrollMovement.y
      scene.blurChatTextArea()
    }, this)

    this.pinch.on("drag1end", (drag: any) => {
      this.isDragging = false
    }, this)

    this.pinch.on("pinchend", (drag: any) => {
      this.isZooming = false
    }, this)

    this.pinch.on("pinch", (pinch: any) => {
      const cursor = new Vector2(pinch.centerX, pinch.centerY)

      const worldPointBeforeZoomChange = scene.getWorldPointUnderCanvasPoint(cursor)

      const scaleFactor: number = pinch.scaleFactor
      this.zoomVelocity = scaleFactor
      scene.zoomValue *= scaleFactor

      scene.clampCamera()

      const worldPointAfterZoomChange = scene.getWorldPointUnderCanvasPoint(cursor)

      const delta = Vector2.minus(worldPointAfterZoomChange, worldPointBeforeZoomChange)

      mainCamera.scrollX -= delta.x
      mainCamera.scrollY -= delta.y

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
      scene.clampCamera()
    }, this)

    if (this.input.mouse) {
      this.input.mouse.disableContextMenu()
    }

    const slashKey = this.input.keyboard?.addKey("FORWARD_SLASH")
    slashKey?.on("down", (event: any) => {
      scene.context.showHelpPanel()
    })

    const escapeKey = this.input.keyboard?.addKey("ESC")

    escapeKey?.on("down", (event: any) => {
      if (this.dynamicState.selectedEntityId != null) {
        scene.context.setSelectedEntityId(null)
        this.clearPendingInteractionTargetRequest()
      } else {
        scene.context.showMenuPanel()
      }
    })


    this.focusChatTextAreaKey = this.input.keyboard?.addKey("SHIFT")
    this.focusChatTextAreaKey?.on("down", (event: any) => {
      this.scene.focusChatTextArea()
    })

    if (this.cursorKeys) {
      this.cursorKeys.space.on("down", () => {
        scene.autoInteraction.autoInteract()
      })
    }

    const handleSelectionClick = (pointer: {x: number, y: number}) => {
      const forPerspectiveOverride = this.cursorKeys?.shift?.isDown || false

      const worldPoint = scene.getWorldPointUnderCanvasPoint(new Vector2(pointer.x, pointer.y))

      const simulationTime = this.simulation.getCurrentSimulationTime()

      const entitiesNearCenter = this.scene.fogOfWarVisibleEntities
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

      if (entitiesNearCenter.length !== 0) {
        const selectedEntityId = forPerspectiveOverride ? this.dynamicState.perspectiveEntity?.entityId : this.dynamicState.selectedEntityId

        const selectedIndex = entitiesNearCenter
          .findIndex(it => it.entity.entityId === selectedEntityId)

        const nextIndex = (selectedIndex === -1 || selectedIndex === (entitiesNearCenter.length - 1))
          ? 0
          : selectedIndex + 1

        if (forPerspectiveOverride) {
          sceneContext.setPerspectiveEntityIdOverride(entitiesNearCenter[nextIndex].entity.entityId)
        } else {
          sceneContext.setSelectedEntityId(entitiesNearCenter[nextIndex].entity.entityId)
        }
      } else {
        if (forPerspectiveOverride) {
          sceneContext.setPerspectiveEntityIdOverride(null)
        } else {
          sceneContext.setSelectedEntityId(null)
          this.clearPendingInteractionTargetRequest()
        }
      }
    }

    let pointerDownLocation = Vector2.zero
    let clickValid = false
    const clickThreshold = 3.0
    let isDown = false
    let downTime = getUnixTimeSeconds()
    let downCounter = 0
    let didHold = false

    this.input.on('pointerdown', (pointer: any) => {
      isDown = true
      clickValid = true
      pointerDownLocation = new Vector2(pointer.x, pointer.y)
      downTime = getUnixTimeSeconds()

      ++downCounter
      const downCounterSnapshot = downCounter
      didHold = false

      setTimeout(() => {
        if (isDown && downCounter === downCounterSnapshot) {
          didHold = true
          handleSelectionClick(pointer)
        }
      }, 400)
    }, this)

    this.input.on('pointerup', (pointer: any) => {
      if (didHold) {
        return
      }

      isDown = false
      const currentPointerLocation = new Vector2(pointer.x, pointer.y)

      const pointerDistanceFromDown = Vector2.distance(currentPointerLocation, pointerDownLocation)
      if (pointerDistanceFromDown > clickThreshold) {
        clickValid = false
      }

      if (clickValid) {
        this.scene.blurChatTextArea()

        const worldPoint = scene.getWorldPointUnderCanvasPoint(new Vector2(pointer.x, pointer.y))


        if (pointer.rightButtonReleased()) {
          this.lastClickTime = getUnixTimeSeconds()
          handleSelectionClick(pointer)
        } else {
          this.lastClickTime = -1
          this.clearPendingInteractionTargetRequest()

          const userControlledEntity = this.dynamicState.userControlledEntity

          if (userControlledEntity != null) {
            this.scene.sendMoveToPointRequest({
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

      const worldPointBeforeZoomChange = this.scene.getWorldPointUnderCanvasPoint(cursor)

      this.scene.zoomValue = this.scene.zoomValue * (1 - deltaY * 0.001)
      this.scene.clampCamera()

      const worldPointAfterZoomChange = this.scene.getWorldPointUnderCanvasPoint(cursor)

      const delta = Vector2.minus(worldPointAfterZoomChange, worldPointBeforeZoomChange)

      mainCamera.scrollX -= delta.x
      mainCamera.scrollY -= delta.y

      this.scene.clampCamera()
    })

    this.input.on("pointermove", (pointer: any) => {
      if (!pointer.isDown || !isDown) return

      const currentPointerLocation = new Vector2(pointer.x, pointer.y)
      const pointerDistanceFromDown = Vector2.distance(currentPointerLocation, pointerDownLocation)

      if (clickValid) {
        if (pointerDistanceFromDown > clickThreshold) {
          this.scene.blurChatTextArea()
          clickValid = false
        }
      } else if (!clickValid) {
        this.scene.blurChatTextArea()
      }

      this.scene.clampCamera()
    })
  }


  private clearPendingInteractionTargetRequest() {
    if (this.scene.isReplay) {
      return
    }

    this.scene.context.sendWebSocketMessage("ClearPendingInteractionTargetRequest", {})
  }
}