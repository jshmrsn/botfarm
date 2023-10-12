import {
  PositionComponent,
  resolveEntityPositionForCurrentTime,
  resolveEntityPositionForTime,
  resolvePositionForTime
} from "../../common/PositionComponentData";
import {Vector2} from "../../misc/Vector2";
import {AutoInteractAction, AutoInteractActionType, GameSimulationScene} from "./GameSimulationScene";
import {DynamicState} from "../ui/DynamicState";
import {CharacterComponent, CharacterComponentData, InventoryComponentData} from "../simulation/CharacterComponentData";
import {
  EquipmentSlots,
  GrowerComponent,
  ItemComponentData,
  ItemConfig,
  KillableComponent
} from "../simulation/ItemComponentData";
import {UserControlledComponent} from "../simulation/userControlledComponentData";
import {GameSimulation} from "../simulation/GameSimulation";
import {Entity} from "../../engine/simulation/Entity";


export class AutoInteraction {
  private _calculatedAutoInteraction: AutoInteractAction | null = null

  get calculatedAutoInteraction(): AutoInteractAction | null {
    return this._calculatedAutoInteraction;
  }

  readonly scene: GameSimulationScene
  readonly dynamicState: DynamicState
  readonly simulation: GameSimulation


  constructor(scene: GameSimulationScene) {
    this.scene = scene
    this.dynamicState = scene.dynamicState
    this.simulation = scene.simulation
  }


  clearPendingInteractionTargetRequest() {
    if (this.scene.isReplay) {
      return
    }

    this.scene.context.sendWebSocketMessage("ClearPendingInteractionTargetRequest", {})
  }

  autoInteract() {
    if (this.scene.isReplay) {
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

    const simulationTime = this.simulation.getCurrentSimulationTime()

    const actionType = autoInteractAction.type
    const targetEntity = autoInteractAction.targetEntity

    if (actionType === AutoInteractActionType.Clear) {
      this.scene.context.setSelectedEntityId(null)
      this.clearPendingInteractionTargetRequest()
    } else if (actionType === AutoInteractActionType.SelectEntity) {
      if (targetEntity != null) {
        this.scene.context.setSelectedEntityId(targetEntity.entityId)
        this.clearPendingInteractionTargetRequest()
      }
    } else if (actionType === AutoInteractActionType.UseEquippedTool) {
      this.scene.sendMoveToPointRequest({
        point: playerPosition,
        pendingUseEquippedToolItemRequest: {
          expectedItemConfigKey: autoInteractAction.equippedToolItemConfig!.key
        }
      })
    } else if (actionType === AutoInteractActionType.StopMoving) {
      const playerPositionAfterLatencyBuffer = resolveEntityPositionForTime(userControlledEntity, simulationTime + 0.3)

      this.scene.sendMoveToPointRequest({
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
        const desiredDistance = Math.min(distance, 14.0)

        const desiredLocation = Vector2.plus(nearestEntityPosition, Vector2.timesScalar(Vector2.normalize(delta), desiredDistance))

        this.scene.sendMoveToPointRequest({
          point: desiredLocation,
          pendingInteractionEntityId: targetEntity.entityId
        })
      }
    }
  }

  update() {
    const autoInteraction = this.calculateAutoInteractAction()

    const previousAutoInteraction = this._calculatedAutoInteraction
    this._calculatedAutoInteraction = autoInteraction

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
          itemConfig: this.simulation.getConfig<ItemConfig>(itemStack.itemConfigKey, "ItemConfig")
        }
      })
      .find(it => it.itemConfig.equippableConfig && it.itemConfig.equippableConfig.equipmentSlot === EquipmentSlots.Tool)?.itemConfig

    const playerPositionComponentData = PositionComponent.getData(userControlledEntity)
    const playerPositionAnimation = playerPositionComponentData.positionAnimation

    const playerPosition = resolveEntityPositionForCurrentTime(userControlledEntity)
    const simulationTime = this.simulation.getCurrentSimulationTime()

    const isPlayerMoving = playerPositionAnimation.keyFrames.length !== 0 &&
      simulationTime <= playerPositionAnimation.keyFrames[playerPositionAnimation.keyFrames.length - 1].time


    const selectedEntityId = this.dynamicState.selectedEntityId;
    const selectedEntity = selectedEntityId != null
      ? this.scene.fogOfWarVisibleEntitiesById[selectedEntityId]
      : null

    const pendingInteractionTargetEntityId = CharacterComponent.getData(userControlledEntity).pendingInteractionTargetEntityId
    const pendingInteractionTargetEntity = pendingInteractionTargetEntityId
      ? this.scene.fogOfWarVisibleEntitiesById[pendingInteractionTargetEntityId]
      : null

    if (equippedToolItemConfig != null &&
      equippedToolItemConfig.spawnItemOnUseConfig != null &&
      selectedEntity == null) {
      if (isPlayerMoving) {
        return {
          type: AutoInteractActionType.StopMoving,
          targetEntity: null,
          actionTitle: "Stop"
        }
      } else {
        return {
          type: AutoInteractActionType.UseEquippedTool,
          targetEntity: null,
          actionTitle: "Use Tool",
          equippedToolItemConfig: equippedToolItemConfig
        }
      }
    }

    let nearestDistance = 10000.0
    let nearestInteraction: AutoInteractAction | null = null

    const maxDistance = 300


    const candidateEntities: Entity[] = pendingInteractionTargetEntity
      ? [pendingInteractionTargetEntity]
      : selectedEntity
        ? [selectedEntity]
        : this.scene.fogOfWarVisibleEntities

    for (const targetEntity of candidateEntities) {
      const positionComponent = PositionComponent.getOrNull(targetEntity)
      const userControlledComponent = UserControlledComponent.getOrNull(targetEntity)
      const killableComponent = KillableComponent.getOrNull(targetEntity)

      if (positionComponent != null &&
        (userControlledComponent == null || userControlledComponent.data.userId !== this.scene.userId) &&
        (killableComponent == null || killableComponent.data.killedAtTime == null)) {
        const position = resolvePositionForTime(positionComponent, simulationTime)
        const distance = Vector2.distance(playerPosition, position)

        if (distance <= maxDistance || targetEntity === selectedEntity) {
          const targetItemComponentData = targetEntity.getComponentDataOrNull<ItemComponentData>("ItemComponentData")

          const targetItemConfig = targetItemComponentData ? this.simulation.getConfig<ItemConfig>(targetItemComponentData.itemConfigKey, "ItemConfig") : null
          const targetGrowerConfig = targetItemConfig?.growerConfig

          const targetGrowerComponentData = GrowerComponent.getDataOrNull(targetEntity)

          let interaction: AutoInteractAction | null = null
          if (targetItemConfig != null) {
            if (targetItemConfig.storableConfig != null) {
              interaction = {
                type: AutoInteractActionType.Pickup,
                targetEntity: targetEntity,
                actionTitle: "Pick-up"
              }
            } else if (targetItemConfig.damageableConfig && targetItemConfig.damageableConfig.damageableByEquippedToolItemConfigKey != null &&
              equippedToolItemConfig != null &&
              equippedToolItemConfig.key === targetItemConfig.damageableConfig.damageableByEquippedToolItemConfigKey) {

              interaction = {
                type: AutoInteractActionType.UseToolToDamageEntity,
                targetEntity: targetEntity,
                actionTitle: "Harvest"
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
                actionTitle: "Plant"
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

    if (nearestInteraction != null) {
      return nearestInteraction
    } else {
      return null
    }
  }
}