import {PositionComponentData, resolveEntityPositionForCurrentTime} from "../../common/PositionComponentData";
import {Entity} from "../../engine/simulation/Entity";
import {RenderContext} from "../../engine/RenderContext";
import {ActionTypes, CharacterComponentData, InventoryComponentData} from "../simulation/CharacterComponentData";
import {AgentControlledComponentData} from "../simulation/AgentControlledComponentData";
import {lerp, Vector2} from "../../misc/Vector2";
import {UserControlledComponentData} from "../simulation/userControlledComponentData";
import {Vector2Animation} from "../../misc/Vector2Animation";
import {EquipmentSlots, ItemComponent, ItemConfig} from "../simulation/ItemComponentData";
import {SpriteConfig} from "../../common/common";
import {getNearestEntitiesFromList} from "../../common/utils";
import {CompositeAnimationSelection} from "../simulation/CompositeAnimationSelection";
import Phaser from "phaser";
import {AutoInteractActionType, GameSimulationScene} from "./GameSimulationScene";
import Camera = Phaser.Cameras.Scene2D.Camera;
import FilterMode = Phaser.Textures.FilterMode;

export function getAnimationDirectionForRelativeLocation(delta: Vector2): string | null {
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

export function renderCharacter(
  scene: GameSimulationScene,
  mainCamera: Camera,
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
  if (entity === perspectiveEntity) {
    positionComponentData.positionAnimation.keyFrames.forEach((keyFrame, index) => {
      if (keyFrame.time > simulationTime) {
        const spriteKey = entity.entityId + "-position-animation-" + index

        renderContext.renderSprite(spriteKey, {
          textureName: "circle",
          layer: scene.navigationPathLayer,
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
      } else if (agentControlledComponentData.agentIntegrationStatus === "paused") {
        statusSuffix += "⏸️"
      } else if (agentControlledComponentData.agentIntegrationStatus === "agent-connection-error") {
        statusSuffix += "️⚠️Connection"
      } else if (agentControlledComponentData.agentIntegrationStatus === "exception") {
        statusSuffix += "🚫Error"
      }
    }

    if (agentControlledComponentData.agentStatus != null) {
      if (agentControlledComponentData.agentStatus === "Running") {
        statusSuffix += "💭"
      } else if (agentControlledComponentData.agentStatus === "UpdatingMemory") {
        statusSuffix += "📝"
      } else if (agentControlledComponentData.agentStatus === "RateLimited") {
        statusSuffix += "📈"
      } else if (agentControlledComponentData.agentStatus === "Error") {
        statusSuffix += "❌"
      } else if (agentControlledComponentData.agentStatus === "Idle") {
      }
    }
  }

  if (statusSuffix !== "") {
    statusSuffix = " " + statusSuffix
  }

  const userControlledComponent = entity.getComponentDataOrNull<UserControlledComponentData>("UserControlledComponentData")

  if (fogOfWarAlpha > 0.01) {
    const perspectiveColor = "#F3D16A"
    const botColor = "#BCEBD2"
    const playerColor = "white"

    const agentSuffix = agentControlledComponentData != null ? "\n<Agent>" : ""

    renderContext.renderText("character-name:" + entity.entityId, {
      depth: scene.calculateDepthForPosition(position),
      layer: scene.characterNameLayer,
      text: (emoji != null ? (emoji + " ") : "") + characterComponentData.name + statusSuffix + agentSuffix,
      strokeThickness: 3,
      fontSize: 20,
      useUiCamera: false,
      position: Vector2.plus(position, new Vector2(0, 15)),
      origin: new Vector2(0.5, 0),
      scale: Vector2.timesScalar(Vector2.one, 1.0 / mainCamera.zoom),
      align: "center",
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

      let spokenMessageText = message.message

      const maxBubbleWidth = 250
      const minBubbleWidth = 70
      const bubblePaddingY = 6
      const bubblePaddingX = 6

      const textScale = 1.0

      const chatBubbleDepth = scene.calculateDepthForPosition(position)

      const content = renderContext.renderText("character-spoken-message-text:" + entity.entityId + spokenMessageText + ":" + messageIndex, {
        layer: scene.chatBubbleLayer,
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

      const bubble = renderContext.renderGraphics("character-spoken-message-bubble:" + entity.entityId + ":" + spokenMessageText + ":" + messageIndex, scene.chatBubbleLayer, bubble => {
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

    if (agentControlledComponentData != null && scene.dynamicState.selectedEntityId === entity.entityId) {
      const observationCircleScale = agentControlledComponentData.observationDistance * 2.0 / 500.0

      renderContext.renderSprite("agent-observation-distance-circle:" + entity.entityId, {
        layer: scene.observationRingsLayer,
        textureName: "ring",
        position: position,
        scale: new Vector2(observationCircleScale, observationCircleScale),
        alpha: 0.3 * fogOfWarAlpha,
        depth: 0
      })
    }

    const characterDepth = scene.calculateDepthForPosition(position)

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
          const equippedItemConfig = scene.simulation.getConfig<ItemConfig>(itemStack.itemConfigKey, "ItemConfig")

          if (equippedItemConfig.equippableConfig &&
            equippedItemConfig.equippableConfig.equipmentSlot === EquipmentSlots.Tool) {
            equippedToolItemConfig = equippedItemConfig
          }

          if (equippedItemConfig.equippableConfig != null &&
            equippedItemConfig.equippableConfig.equippedCompositeAnimation == null) {
            const spriteConfig = scene.getConfig<SpriteConfig>(equippedItemConfig.spriteConfigKey, "SpriteConfig")

            const holdOffset = movementAnimationDirection === "left"
              ? new Vector2(-10, -5)
              : movementAnimationDirection === "right"
                ? new Vector2(-10, -5)
                : movementAnimationDirection === "up"
                  ? new Vector2(10, -5)
                  : new Vector2(-10, -5)

            renderContext.renderSprite("equipped-item:" + entity.entityId + ":" + itemStack.itemConfigKey, {
              layer: scene.mainLayer,
              textureName: equippedItemConfig.spriteConfigKey,
              position: Vector2.plus(position, holdOffset),
              scale: Vector2.timesScalar(spriteConfig.baseScale, 0.65),
              alpha: fogOfWarAlpha,
              depth: scene.calculateDepthForPosition(position) + 1
            })
          }
        }
      }
    }

    const calculatedAutoInteraction = scene.autoInteraction.calculatedAutoInteraction;

    if (equippedToolItemConfig != null &&
      equippedToolItemConfig.spawnItemOnUseConfig &&
      userControlledComponent &&
      userControlledComponent.userId === scene.dynamicState.userId &&
      calculatedAutoInteraction?.type === AutoInteractActionType.UseEquippedTool) {
      const spawnItemConfigKey = equippedToolItemConfig.spawnItemOnUseConfig.spawnItemConfigKey
      const spawnItemConfig = scene.getConfig<ItemConfig>(spawnItemConfigKey, "ItemConfig")

      const spawnItemSpriteConfig = scene.getConfig<SpriteConfig>(spawnItemConfig.spriteConfigKey, "SpriteConfig")

      // const nearestEntities = getNearestEntitiesFromList(
      //   scene.fogOfWarVisibleEntities,
      //   position,
      //   100.0, // TODO: has to match value in useEquippedToolItem in simulation server Kotlin
      //   entityToCheck => {
      //     const entityToCheckItemConfigKey = ItemComponent.getDataOrNull(entityToCheck)?.itemConfigKey
      //     if (entityToCheckItemConfigKey == null) {
      //       return false
      //     }
      //     const itemConfigToCheck = scene.getConfig<ItemConfig>(entityToCheckItemConfigKey, "ItemConfig")
      //
      //     return itemConfigToCheck.blocksPlacement
      //   }
      // )

      const isValid = true //nearestEntities.length === 0

      renderContext.renderSprite("spawn_item_on_use_preview_" + entity.entityId + "_" + spawnItemSpriteConfig.key, {
        layer: scene.mainLayer,
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
        ? scene.simulation.getEntityOrNull(performedAction.targetEntityId)
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
      } else if (performedAction.actionType === ActionTypes.PickUpItem) {
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
      const sheetDefinition = scene.assetLoader.sheetDefinitionsByKey[compositeAnimation.key]

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
              layer: scene.mainLayer,
              depth: characterDepth + layer.zPos * 0.0001,
              textureName: textureKey,
              position: positionWithOffset,
              animation: animationConfig,
              scale: scale,
              filterMode: scene.zoomValue > 1.25 ? FilterMode.NEAREST : FilterMode.LINEAR,
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
      layer: scene.mainLayer,
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
      let hasPants = false
      let hasShirt = false

      for (let itemStack of inventoryComponent.inventory.itemStacks) {
        if (itemStack.isEquipped) {
          const equippedItemConfig = scene.simulation.getConfig<ItemConfig>(itemStack.itemConfigKey, "ItemConfig")

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
