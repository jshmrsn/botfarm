package botfarm.game

import botfarm.common.*
import botfarm.engine.ktorplugins.AdminRequest
import botfarm.engine.simulation.*
import botfarm.game.agentintegration.AgentService
import botfarm.game.components.*
import botfarm.game.config.*
import botfarm.game.setup.GameScenario
import botfarm.game.setup.SpawnPlayersMode
import botfarm.game.setup.gameSystems
import botfarmshared.engine.apidata.EntityId
import botfarmshared.game.apidata.*
import botfarmshared.misc.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.math.sign

@Serializable
class SetShouldSpectateByDefaultRequest(
   val shouldSpectateByDefault: Boolean = false
)

@Serializable
class SetPerspectiveEntityIdOverrideRequest(
   val perspectiveEntityIdOverride: EntityId? = null
)

@Serializable
class MoveToPointRequest(
   val point: Vector2,
   val pendingUseEquippedToolItemRequest: UseEquippedToolItemRequest? = null,
   val pendingInteractionEntityId: EntityId? = null
)

@Serializable
class UseEquippedToolItemRequest(
   val expectedItemConfigKey: String
)

@Serializable
class EquipItemRequest(
   val expectedItemConfigKey: String,
   val stackIndex: Int
)

@Serializable
class UnequipItemRequest(
   val expectedItemConfigKey: String,
   val stackIndex: Int
)

@Serializable
class CraftItemRequest(
   val itemConfigKey: String
)

@Serializable
class ClearPendingInteractionTargetRequest()

@Serializable
class DropItemRequest(
   val itemConfigKey: String,
   val amountFromStack: Int? = null,
   val stackIndex: Int
)

@Serializable
class AddCharacterMessageRequest(
   val message: String
)

class GameSimulation(
   context: SimulationContext,
   data: SimulationData,
   val agentService: AgentService,
   val gameScenario: GameScenario
) : Simulation(
   context = context,
   systems = gameSystems,
   initialData = data
) {
   companion object {
      val activityStreamEntityId = EntityId("activity-stream")
   }

   val collisionMap: CollisionMap<CollisionFlag>

   val gameSimulationConfig: GameSimulationConfig

   val worldBounds: Vector2

   val debugInfoComponent: EntityComponent<DebugInfoComponentData>

   var enableDetailedInfo = false

   private var lastAutoPauseAiSpentDollars = 0.0

   private val perspectiveEntityContainersByCharacterEntityId = mutableMapOf<EntityId, EntityContainer>()
   val pendingAutoInteractCallbackByEntityId = mutableMapOf<EntityId, (ActionResultType) -> Unit>()


   init {
      this.gameSimulationConfig = this.getConfig<GameSimulationConfig>(GameSimulationConfig.defaultKey)
      this.worldBounds = this.gameSimulationConfig.worldBounds

      val cellWidth = this.gameSimulationConfig.cellWidth
      val cellHeight = this.gameSimulationConfig.cellHeight

      val rowCount = (this.worldBounds.y / cellHeight).toInt()
      val columnCount = (this.worldBounds.x / cellWidth).toInt()

      this.collisionMap = CollisionMap(
         rowCount = rowCount,
         columnCount = columnCount,
         cellHeight = cellHeight,
         cellWidth = cellWidth
      )

      this.debugInfoComponent = this.createEntity(
         entityId = EntityId("debug-info"),
         components = listOf(
            DebugInfoComponentData(
               collisionMapDebugInfo = CollisionMapDebugInfo(
                  rowCount = this.collisionMap.rowCount,
                  columnCount = this.collisionMap.columnCount,
                  cellSize = Vector2(cellWidth, cellHeight),
                  bounds = this.collisionMap.bounds
               )
            )
         )
      ).getComponent<DebugInfoComponentData>()

      this.createEntity(
         components = listOf(ActivityStreamComponentData()),
         entityId = GameSimulation.activityStreamEntityId
      )

      if (AdminRequest.serverHasAdminSecret && !this.context.wasCreatedByAdmin) {
         this.debugInfoComponent.modifyData {
            it.copy(
               aiPaused = true
            )
         }

         this.broadcastAlertAsGameMessage("Pausing AI because this simulation was not created by an admin")
      }
   }

   fun getPerspectiveEntityContainerForCharacterEntityId(characterEntityId: EntityId): EntityContainer? {
      return this.perspectiveEntityContainersByCharacterEntityId[characterEntityId]
   }

   override fun syncClientEntityContainer(
      client: Client,
      skipSendForSnapshot: Boolean
   ) {
      val userControlledEntities = this.getUserControlledEntities(
         userId = client.userId
      )

      val gameClientState = this.getGameClientStateComponent(client).data

      val perspectiveEntity = if (gameClientState.perspectiveEntityIdOverride != null) {
         this.getEntityOrNull(gameClientState.perspectiveEntityIdOverride)
      } else if (gameClientState.shouldSpectateByDefault) {
         null
      } else {
         userControlledEntities.firstOrNull()
      }

      val visibilityOfOnlyNoPositionEntities = { entityToCheck: Entity ->
         val hasPositionComponent = entityToCheck.getComponentOrNull<PositionComponentData>() != null

         !hasPositionComponent
      }

      val sourceEntityContainer: EntityContainer
      val hasVisibilityOfEntity: (Entity) -> Boolean

      val sourceEntityContainerForPerspectiveEntity = perspectiveEntity?.let {
         this.perspectiveEntityContainersByCharacterEntityId[it.entityId]
      }


      if (perspectiveEntity != null) {
         if (sourceEntityContainerForPerspectiveEntity != null) {
            hasVisibilityOfEntity = { true }
            sourceEntityContainer = sourceEntityContainerForPerspectiveEntity
         } else {
            hasVisibilityOfEntity = visibilityOfOnlyNoPositionEntities
            sourceEntityContainer = this.rootEntityContainer
         }
      } else if (gameClientState.shouldSpectateByDefault) {
         hasVisibilityOfEntity = { true }
         sourceEntityContainer = this.rootEntityContainer
      } else {
         hasVisibilityOfEntity = visibilityOfOnlyNoPositionEntities
         sourceEntityContainer = this.rootEntityContainer
      }

      try {
         client.entityContainer.sync(
            source = sourceEntityContainer,
            hasVisibilityOfEntity = hasVisibilityOfEntity,
            skipSendForSnapshot = skipSendForSnapshot
         )
      } catch (exception: Exception) {
         throw Exception("Exception while syncing client entityContainer (perspectiveEntity = ${perspectiveEntity?.entityId}, shouldSpectateByDefault = ${gameClientState.shouldSpectateByDefault}, sourceEntityContainerForPerspectiveEntity != null = ${sourceEntityContainerForPerspectiveEntity != null})", exception)
      }
   }

   fun getActivityStreamEntity() =
      this.getEntityOrNull(Companion.activityStreamEntityId) ?: throw Exception("activity-stream entity not found")

   fun getActivityStream(): List<ActivityStreamEntry> {
      val activityStreamEntity = this.getActivityStreamEntity()
      return activityStreamEntity.getComponent<ActivityStreamComponentData>().data.activityStream
   }

   fun addActivityStreamEntry(
      title: String? = null,
      message: String? = null,
      longMessage: String? = null,
      shouldReportToAi: Boolean = true,
      onlyShowForPerspectiveEntity: Boolean = false,

      agentReason: String? = null,
      agentUniqueActionId: String? = null,


      actionType: ActionType? = null,
      actionResultType: ActionResultType? = null,
      actionItemConfig: ItemConfig? = null,

      sourceEntityId: EntityId? = null,
      sourceLocation: Vector2? = null,

      targetItemConfig: ItemConfig? = null,
      targetEntityId: EntityId? = null,

      resultItemConfig: ItemConfig? = null,
      resultEntityId: EntityId? = null,

      shouldLimitObservation: Boolean = sourceLocation != null,
      onlySourceEntityCanObserve: Boolean = onlyShowForPerspectiveEntity,
      observedByEntityIdsOverride: List<EntityId>? = null,
      spawnedItems: List<SpawnedItemEntity>? = null
   ) {
      val activityStreamEntity = this.getActivityStreamEntity()
      val activityStreamComponent = activityStreamEntity.getComponent<ActivityStreamComponentData>()

      val observedByEntityIds: List<EntityId>? = observedByEntityIdsOverride ?: if (onlySourceEntityCanObserve) {
         if (sourceEntityId == null) {
            throw Exception("addActivityStreamEntry: sourceEntityId is null while onlySourceEntityCanObserve is true")
         }

         listOf(sourceEntityId)
      } else if (shouldLimitObservation) {
         if (sourceLocation == null) {
            throw Exception("addActivityStreamEntry: sourceLocation is null while shouldLimitObservation is true")
         }

         this.entities.mapNotNull {
            val characterComponent = it.getComponentOrNull<CharacterComponentData>()
            if (characterComponent != null) {
               val location = it.resolvePosition()
               val distance = location.distance(sourceLocation)

               if (distance <= characterComponent.data.observationRadius) {
                  it.entityId
               } else {
                  null
               }
            } else {
               null
            }
         }
      } else {
         null
      }

      activityStreamComponent.modifyData {
         it.copy(
            activityStream = it.activityStream + ActivityStreamEntry(
               observedByEntityIds = observedByEntityIds,
               time = this.simulationTime,
               title = title,
               message = message,
               longMessage = longMessage,
               shouldReportToAi = shouldReportToAi,
               onlyShowForPerspectiveEntity = onlyShowForPerspectiveEntity,

               sourceEntityId = sourceEntityId,
               sourceLocation = sourceLocation,

               targetEntityId = targetEntityId,
               targetItemConfigKey = targetItemConfig?.key,
               actionType = actionType,
               actionResultType = actionResultType,
               actionItemConfigKey = actionItemConfig?.key,

               resultItemConfigKey = resultItemConfig?.key,
               resultEntityId = resultEntityId,
               agentUniqueActionId = agentUniqueActionId,
               agentReason = agentReason,
               spawnedItems = spawnedItems
            )
         )
      }
   }

   fun getSignedEdgeDistanceFromEntity(
      location: Vector2,
      targetEntity: Entity
   ): Vector2 {
      val itemConfig = targetEntity.itemConfigOrNull
      val targetLocation = targetEntity.resolvePosition()

      val defaultTargetRadius = 15.0

      if (itemConfig != null) {
         val collisionConfig = itemConfig.collisionConfig

         if (collisionConfig != null) {
            val targetCollisionCenter = targetLocation + collisionConfig.collisionOffset

            val cellWidth = this.collisionMap.cellWidth
            val targetCollisionWidth = cellWidth * collisionConfig.width
            val cellHeight = this.collisionMap.cellHeight
            val targetCollisionHeight = cellHeight * collisionConfig.height

            val offsetFromCollisionCenter = location - targetCollisionCenter

            val halfTargetCollisionWidth = targetCollisionWidth * 0.5
            val halfTargetCollisionHeight = targetCollisionHeight * 0.5

            val xEdgeDistance = if (offsetFromCollisionCenter.x < -halfTargetCollisionWidth) {
               offsetFromCollisionCenter.x + halfTargetCollisionWidth
            } else if (offsetFromCollisionCenter.x > halfTargetCollisionWidth) {
               offsetFromCollisionCenter.x - halfTargetCollisionWidth
            } else {
               0.0
            }

            val yEdgeDistance = if (offsetFromCollisionCenter.y < -halfTargetCollisionHeight) {
               offsetFromCollisionCenter.y + halfTargetCollisionHeight
            } else if (offsetFromCollisionCenter.y > halfTargetCollisionHeight) {
               offsetFromCollisionCenter.y - halfTargetCollisionHeight
            } else {
               0.0
            }

            return Vector2(
               xEdgeDistance,
               yEdgeDistance
            )
         } else {
            return Vector2.uniform(
               location.distance(targetLocation) - defaultTargetRadius
            )
         }
      } else {
         return Vector2.uniform(
            location.distance(targetLocation) - defaultTargetRadius
         )
      }
   }

   fun getNearestEdgeDistanceFromEntity(
      location: Vector2,
      targetEntity: Entity
   ): Double {
      val signed = this.getSignedEdgeDistanceFromEntity(
         location = location,
         targetEntity = targetEntity
      )

      return Math.min(signed.x.absoluteValue, signed.y.absoluteValue)
   }

   override fun onStart() {
      this.updateCollisionDebugInfoIfNeeded()
   }

   private var collisionMapDebugInfoIsOutOfDate = false

   fun updateCollisionDebugInfoIfNeeded() {
      if (!this.collisionMapDebugInfoIsOutOfDate) {
         return
      }

      this.collisionMapDebugInfoIsOutOfDate = false

      val cells = mutableListOf<CollisionMapCellDebugInfo>()

      if (this.enableDetailedInfo) {
         for (row in (0..<this.collisionMap.rowCount)) {
            for (col in (0..<this.collisionMap.columnCount)) {
               val occupiedFlags = CollisionFlag.entries.filter {
                  !this.collisionMap.isCellOpen(row = row, col = col, flag = it)
               }

               val cellCenter = this.collisionMap.indexPairToCellCenter(IndexPair(row = row, col = col))

               cells.add(
                  CollisionMapCellDebugInfo(
                     center = cellCenter,
                     occupiedFlags = occupiedFlags,
                     row = row,
                     col = col
                  )
               )
            }
         }
      }

      this.debugInfoComponent.modifyData {
         it.copy(
            collisionMapDebugInfo = it.collisionMapDebugInfo.copy(
               cells = cells
            )
         )
      }
   }

   override fun onEntityCreated(entity: Entity) {
      val itemConfig = entity.itemConfigOrNull

      if (itemConfig != null) {
         val collisionConfig = itemConfig.collisionConfig

         if (collisionConfig != null) {
            val entityPosition = entity.resolvePosition() + collisionConfig.collisionOffset
            val anchorPair = this.collisionMap.pointToIndexPair(entityPosition)
            val anchorCellCenter = this.collisionMap.indexPairToCellCenter(anchorPair)

            val remainderSign = Vector2(
               (entityPosition.x - anchorCellCenter.x).sign,
               (entityPosition.y - anchorCellCenter.y).sign
            )

            val mirroredAdditionalCells = Vector2(
               (collisionConfig.width - 1) / 2.0,
               (collisionConfig.height - 1) / 2.0
            )

            val mirroredAdditionalCols = mirroredAdditionalCells.x.toInt()
            val mirroredAdditionalRows = mirroredAdditionalCells.y.toInt()

            val remainderAdditionalCols = (mirroredAdditionalCells.x - mirroredAdditionalCols + 0.001).roundToInt()
            val remainderAdditionalRows = (mirroredAdditionalCells.y - mirroredAdditionalRows + 0.001).roundToInt()

            val topLeftRow = if (remainderSign.y > 0) {
               anchorPair.row - mirroredAdditionalCells.y.toInt()
            } else {
               anchorPair.row - mirroredAdditionalCells.y.toInt() - remainderAdditionalRows
            }

            val topLeftCol = if (remainderSign.x > 0) {
               anchorPair.col - mirroredAdditionalCells.x.toInt()
            } else {
               anchorPair.col - mirroredAdditionalCells.x.toInt() - remainderAdditionalCols
            }

            this.collisionMap.addEntity(
               entityId = entity.entityId,
               topLeftCol = topLeftCol,
               topLeftRow = topLeftRow,
               width = collisionConfig.width,
               height = collisionConfig.height,
               flags = collisionConfig.flags
            )

            this.collisionMapDebugInfoIsOutOfDate = true
         }
      }
   }

   override fun onEntityDestroyed(entity: Entity) {
      val itemConfig = entity.itemConfigOrNull

      if (itemConfig != null) {
         val collisionConfig = itemConfig.collisionConfig

         if (collisionConfig != null) {
            this.collisionMap.clearEntity(entityId = entity.entityId)
            this.collisionMapDebugInfoIsOutOfDate = true
         }
      }

      this.perspectiveEntityContainersByCharacterEntityId.remove(entity.entityId)
   }

   fun addCharacterMessage(
      entity: Entity,
      message: String,
      agentReason: String? = null,
      agentActionUniqueId: String? = null
   ) {
      val characterComponent = entity.getComponentOrNull<CharacterComponentData>()

      if (characterComponent != null) {
         val currentTime = this.getCurrentSimulationTime()
         val location = entity.getComponent<PositionComponentData>().data.positionAnimation.resolve(currentTime)

         characterComponent.modifyData {
            it.copy(
               recentSpokenMessages = it.recentSpokenMessages.filter { spokenMessage ->
                  val age = currentTime - spokenMessage.sentSimulationTime
                  age < 30.0
               } + SpokenMessage(
                  messageId = buildShortRandomIdentifier(),
                  sentSimulationTime = currentTime,
                  message = message
               ))
         }

         this.addActivityStreamEntry(
            actionType = ActionType.Speak,
            actionResultType = ActionResultType.Success,
            message = message,
            sourceLocation = location,
            sourceEntityId = entity.entityId,
            agentReason = agentReason,
            agentUniqueActionId = agentActionUniqueId
         )
      }
   }

   private fun handleClearPendingInteractionRequest(client: Client, message: ClearPendingInteractionTargetRequest) {
      val entity = this.getUserControlledEntities(userId = client.userId).firstOrNull()

      if (entity == null) {
         return
      }

      val characterComponent = entity.getComponent<CharacterComponentData>()
      this.pendingAutoInteractCallbackByEntityId.clear()
      characterComponent.modifyData {
         it.copy(
            pendingInteractionTargetEntityId = null,
            pendingUseEquippedToolItemRequest = null
         )
      }
   }

   enum class CraftItemResult {
      ItemCannotBeCrafted,
      CannotAfford,
      Success
   }

   fun craftItem(
      entity: Entity,
      itemConfigKey: String
   ) = this.craftItem(
      entity = entity,
      itemConfig = this.getConfig(itemConfigKey)
   )

   fun craftItem(
      entity: Entity,
      itemConfig: ItemConfig
   ): CraftItemResult {
      val craftableConfig = itemConfig.craftableConfig

      if (craftableConfig == null) {
         return CraftItemResult.ItemCannotBeCrafted
      }

      val craftingCost = craftableConfig.craftingCost

      val didAfford = entity.takeInventoryItemCollection(craftingCost)

      if (!didAfford) {
         return CraftItemResult.CannotAfford
      }

      val characterComponentData = entity.getComponentOrNull<CharacterComponentData>()?.data

      val name = characterComponentData?.name ?: "?"
      val entityPosition = entity.resolvePosition()

      this.addActivityStreamEntry(
         sourceEntityId = entity.entityId,
         sourceLocation = entityPosition,
         actionType = ActionType.Craft,
         actionResultType = ActionResultType.Success,
         targetItemConfig = itemConfig
      )

      if (itemConfig.storableConfig != null) {
         entity.giveInventoryItem(
            itemConfig = itemConfig,
            amount = craftableConfig.craftingAmount
         )
      } else {
         this.spawnItems(
            itemConfig = itemConfig,
            baseLocation = entityPosition,
            quantity = RandomItemQuantity.amount(craftableConfig.craftingAmount),
            randomLocationScale = 30.0
         )
      }

      return CraftItemResult.Success
   }


   enum class EquipItemResult {
      ItemCannotBeEquipped,
      ItemNotInInventory,
      UnexpectedItemInStack,
      Success
   }

   fun equipItem(
      entity: Entity,
      expectedItemConfigKey: String,
      requestedStackIndex: Int? = null
   ): EquipItemResult {
      val inventoryComponent = entity.getComponent<InventoryComponentData>()
      val characterComponent = entity.getComponent<CharacterComponentData>()
      val name = characterComponent.data.name

      val itemConfigToEquip = this.getConfig<ItemConfig>(expectedItemConfigKey)

      if (itemConfigToEquip.equippableConfig == null) {
         return EquipItemResult.ItemCannotBeEquipped
      }

      val inventory = inventoryComponent.data.inventory
      val stackIndexToEquip = requestedStackIndex ?: inventory.itemStacks.indexOfFirst {
         it.itemConfigKey == expectedItemConfigKey
      }

      if (stackIndexToEquip < 0) {
         return EquipItemResult.ItemNotInInventory
      }

      val stackToEquip = inventory.itemStacks.getOrNull(stackIndexToEquip)

      if (stackToEquip == null || stackToEquip.itemConfigKey != expectedItemConfigKey) {
         return EquipItemResult.UnexpectedItemInStack
      }

      this.applyPerformedAction(
         entity = entity,
         actionType = ActionType.EquipItem,
         targetEntityId = null
      )

      inventoryComponent.modifyData {
         it.copy(
            inventory = it.inventory.copy(
               itemStacks = it.inventory.itemStacks.mapIndexed { stackIndex, itemStack ->
                  itemStack.copy(
                     isEquipped = if (stackIndex == stackIndexToEquip) {
                        true
                     } else if (itemStack.isEquipped) {
                        val itemConfigInStack = this.getConfig<ItemConfig>(itemStack.itemConfigKey)
                        itemConfigInStack.equippableConfig != null &&
                                itemConfigInStack.equippableConfig.equipmentSlot != itemConfigToEquip.equippableConfig.equipmentSlot
                     } else {
                        false
                     }
                  )
               }
            )
         )
      }

      this.addActivityStreamEntry(
         actionResultType = ActionResultType.Success,
         sourceLocation = entity.resolvePosition(),
         targetItemConfig = itemConfigToEquip,
         actionType = ActionType.EquipItem,
         sourceEntityId = entity.entityId
      )

      return EquipItemResult.Success
   }

   private fun syncPerspectiveEntityContainer(
      perspectiveEntity: Entity,
      perspectiveEntityContainer: EntityContainer
   ) {
      val perspectiveLocation = perspectiveEntity.resolvePosition()
      val observationRadius = perspectiveEntity.getComponent<CharacterComponentData>().data.observationRadius

      perspectiveEntityContainer.sync(
         source = this.rootEntityContainer,
         hasVisibilityOfEntity = { entityToCheck: Entity ->
            val hasPositionComponent = entityToCheck.getComponentOrNull<PositionComponentData>() != null

            if (!hasPositionComponent) {
               true
            } else {
               val distance = entityToCheck.resolvePosition().distance(perspectiveLocation)
               distance <= observationRadius
            }
         },
         notifySourceEntityIsVisible = { entity, sourceEntityIsVisible ->
            if (!sourceEntityIsVisible) {
               val positionComponent = entity.getComponent<PositionComponentData>()

               if (positionComponent.data.positionAnimation.keyFrames.size > 1) {
                  positionComponent.modifyData {
                     it.copy(
                        positionAnimation = Vector2Animation.static(entity.resolvePosition())
                     )
                  }
               }
            }
         },
         setEntityIsStale = { entity, isStale ->
            val fogOfWarComponent = entity.getComponentOrNull<FogOfWarComponentData>()

            if (fogOfWarComponent == null) {
               if (isStale) {
                  throw Exception("Attempted to set stale on entity without FogOfWarComponentData: ${entity.entityId}")
               }
            } else {
               if (fogOfWarComponent.data.isStale != isStale) {
                  if (isStale) {
                     entity.getComponent<PositionComponentData>().modifyData {
                        it.copy(
                           positionAnimation = Vector2Animation.static(entity.resolvePosition())
                        )
                     }
                  }

                  fogOfWarComponent.modifyData {
                     it.copy(
                        isStale = isStale
                     )
                  }
               }
            }
         }
      )
   }

   fun syncPerspectiveEntityContainers() {
      for (entry in this.perspectiveEntityContainersByCharacterEntityId) {
         val entityId = entry.key
         val perspectiveEntityContainer = entry.value
         val perspectiveEntity = this.getEntity(entityId)

         this.syncPerspectiveEntityContainer(
            perspectiveEntity = perspectiveEntity,
            perspectiveEntityContainer = perspectiveEntityContainer
         )
      }
   }

   override fun onTick(deltaTime: Double) {
      this.syncPerspectiveEntityContainers()

      this.updateCollisionDebugInfoIfNeeded()

      val autoPauseAiPerSpentDollars = this.gameScenario.autoPauseAiPerSpentDollars

      if (autoPauseAiPerSpentDollars != null) {
         var totalAiSpend = 0.0
         this.entities.forEach {
            val agentControlledComponent = it.getComponentOrNull<AgentControlledComponentData>()

            if (agentControlledComponent != null) {
               totalAiSpend += agentControlledComponent.data.costDollars
            }
         }

         val dollarsSpentSinceLastAutoPauseAi = totalAiSpend - this.lastAutoPauseAiSpentDollars
         if (dollarsSpentSinceLastAutoPauseAi > autoPauseAiPerSpentDollars) {
            this.lastAutoPauseAiSpentDollars = totalAiSpend
            this.debugInfoComponent.modifyData {
               it.copy(
                  aiPaused = true
               )
            }
            broadcastAlertAsGameMessage(
               "AI auto-paused for spend: Spend since last pause = $${
                  "%.2f".format(
                     dollarsSpentSinceLastAutoPauseAi
                  )
               }, total = $${"%.2f".format(dollarsSpentSinceLastAutoPauseAi)}"
            )
         }
      }
   }

   fun unequipItem(
      entity: Entity,
      expectedItemConfigKey: String,
      requestedStackIndex: Int?
   ): EquipItemResult {
      val inventoryComponent = entity.getComponent<InventoryComponentData>()

      val itemConfig = this.getConfig<ItemConfig>(expectedItemConfigKey)

      val inventory = inventoryComponent.data.inventory
      val stackIndexToUnequip = requestedStackIndex ?: inventory.itemStacks.indexOfFirst {
         it.itemConfigKey == expectedItemConfigKey && it.isEquipped
      }

      if (stackIndexToUnequip < 0) {
         return EquipItemResult.ItemNotInInventory
      }

      val stackToEquip = inventory.itemStacks.getOrNull(stackIndexToUnequip)

      if (stackToEquip == null || stackToEquip.itemConfigKey != expectedItemConfigKey) {
         return EquipItemResult.UnexpectedItemInStack
      }


      this.applyPerformedAction(
         entity = entity,
         actionType = ActionType.EquipItem,
         targetEntityId = null
      )

      inventoryComponent.modifyData {
         it.copy(
            inventory = it.inventory.copy(
               itemStacks = it.inventory.itemStacks.mapIndexed { index, itemStack ->
                  itemStack.copy(
                     isEquipped = itemStack.isEquipped && index != stackIndexToUnequip
                  )
               }
            )
         )
      }

      this.addActivityStreamEntry(
         sourceEntityId = entity.entityId,
         sourceLocation = entity.resolvePosition(),
         targetItemConfig = itemConfig,
         actionType = ActionType.UnequipItem,
         actionResultType = ActionResultType.Success
      )

      return EquipItemResult.Success
   }

   private fun handleCraftItemRequest(client: Client, message: CraftItemRequest) {
      val entity = this.getUserControlledEntities(userId = client.userId).firstOrNull()

      if (entity == null) {
         sendAlertMessage(client, "No controlled entity")
         return
      }

      val result = this.craftItem(
         entity = entity,
         itemConfigKey = message.itemConfigKey
      )

      if (result != CraftItemResult.Success) {
         this.sendAlertMessage(client, "Unable to craft ${message.itemConfigKey} " + result.name)
      }
   }

   private fun handleUseEquippedItemRequest(client: Client, message: UseEquippedToolItemRequest) {
      val entity = this.getUserControlledEntities(userId = client.userId).firstOrNull()

      if (entity == null) {
         sendAlertMessage(client, "No controlled entity")
         return
      }

      this.useEquippedToolItem(
         interactingEntity = entity,
         expectedItemConfigKey = message.expectedItemConfigKey
      )
   }

   private fun handleEquipItemRequest(client: Client, message: EquipItemRequest) {
      val entity = this.getUserControlledEntities(userId = client.userId).firstOrNull()

      if (entity == null) {
         sendAlertMessage(client, "No controlled entity")
         return
      }

      this.equipItem(
         entity = entity,
         expectedItemConfigKey = message.expectedItemConfigKey,
         requestedStackIndex = message.stackIndex
      )
   }

   private fun handleUnequipItemRequest(client: Client, message: UnequipItemRequest) {
      val entity = this.getUserControlledEntities(userId = client.userId).firstOrNull()

      if (entity == null) {
         sendAlertMessage(client, "No controlled entity")
         return
      }

      this.unequipItem(
         entity = entity,
         expectedItemConfigKey = message.expectedItemConfigKey,
         requestedStackIndex = message.stackIndex
      )
   }

   private fun handleMoveToPointRequest(client: Client, message: MoveToPointRequest) {
      val entity = this.getUserControlledEntities(userId = client.userId).firstOrNull()

      if (entity == null) {
         sendAlertMessage(client, "No controlled entity")
         return
      }

      val result = this.startEntityMovement(
         entity = entity,
         endPoint = message.point
      )

      val characterComponent = entity.getComponent<CharacterComponentData>()

      this.pendingAutoInteractCallbackByEntityId.remove(entity.entityId)

      if (result !is MoveToResult.Success) {
         characterComponent.modifyData {
            it.copy(
               pendingInteractionTargetEntityId = null,
               pendingUseEquippedToolItemRequest = null
            )
         }
      } else {
         characterComponent.modifyData {
            it.copy(
               pendingInteractionTargetEntityId = message.pendingInteractionEntityId,
               pendingUseEquippedToolItemRequest = message.pendingUseEquippedToolItemRequest,
               pendingInteractionActionType = null
            )
         }
      }
   }

   private fun handleDropItemRequest(client: Client, message: DropItemRequest) {
      val entity = this.getUserControlledEntities(userId = client.userId).firstOrNull()

      if (entity == null) {
         sendAlertMessage(client, "No controlled entity")
         return
      }

      val droppedItemEntity = this.dropItemStack(
         droppingEntity = entity,
         expectedItemConfigKey = message.itemConfigKey,
         amountToDropFromStack = message.amountFromStack,
         stackIndex = message.stackIndex
      )

      if (droppedItemEntity == null) {
         sendAlertMessage(client, "Unable to drop")
      }
   }

   fun dropItemStack(
      droppingEntity: Entity,
      expectedItemConfigKey: String,
      stackIndex: Int,
      amountToDropFromStack: Int? = null,
      agentReason: String? = null,
      agentActionUniqueId: String? = null
   ): Entity? {
      val itemConfig = this.getConfig<ItemConfig>(expectedItemConfigKey)

      if (itemConfig.storableConfig == null || !itemConfig.storableConfig.canBeDropped) {
         throw Exception("Item cannot be dropped")
      }

      val inventoryComponent = droppingEntity.getComponent<InventoryComponentData>()
      val inventory = inventoryComponent.data.inventory
      val stack = inventory.itemStacks.getOrNull(stackIndex)

      if (stack == null || stack.itemConfigKey != expectedItemConfigKey) {
         return null
      }

      val resolvedAmountToDrop: Int
      if (amountToDropFromStack != null) {
         if (amountToDropFromStack <= stack.amount) {
            resolvedAmountToDrop = amountToDropFromStack
         } else {
            return null
         }
      } else {
         resolvedAmountToDrop = stack.amount
      }

      val remainingAmount = stack.amount - resolvedAmountToDrop

      if (remainingAmount <= 0) {
         inventoryComponent.modifyData {
            it.copy(
               inventory = it.inventory.copy(
                  itemStacks = it.inventory.itemStacks.removed(
                     index = stackIndex
                  )
               )
            )
         }
      } else {
         inventoryComponent.modifyData {
            it.copy(
               inventory = it.inventory.copy(
                  itemStacks = it.inventory.itemStacks.replaced(
                     index = stackIndex,
                     value = stack.copy(
                        amount = remainingAmount
                     )
                  )
               )
            )
         }
      }

      this.applyPerformedAction(
         entity = droppingEntity,
         actionType = ActionType.DropItem
      )

      val spawnedEntity = this.spawnItem(
         itemConfigKey = stack.itemConfigKey,
         amount = resolvedAmountToDrop,
         location = droppingEntity.resolvePosition() + Vector2.randomSignedXY(50.0)
      )

      this.addActivityStreamEntry(
         sourceLocation = droppingEntity.resolvePosition(),
         sourceEntityId = droppingEntity.entityId,
         targetItemConfig = itemConfig,
         actionType = ActionType.DropItem,
         actionResultType = ActionResultType.Success,
         agentReason = agentReason,
         agentUniqueActionId = agentActionUniqueId
      )

      return spawnedEntity
   }

   enum class PickUpItemResult {
      Success,
      TooFar
   }

   fun applyPerformedAction(
      entity: Entity,
      actionType: ActionType,
      targetEntityId: EntityId? = null,
      duration: Double = 0.5
   ) {
      val location = entity.resolvePosition()

      entity.getComponentOrNull<CharacterComponentData>()?.modifyData {
         it.copy(
            performedAction = PerformedAction(
               startedAtSimulationTime = this.getCurrentSimulationTime(),
               actionIndex = (it.performedAction?.actionIndex ?: 0) + 1,
               actionType = actionType,
               performedAtLocation = location,
               targetEntityId = targetEntityId,
               duration = duration
            )
         )
      }
   }

   sealed class UseEquippedItemResult {
      class Success(val equippedToolItemConfig: ItemConfig) : UseEquippedItemResult()
      object UnexpectedEquippedItem : UseEquippedItemResult()
      object NoActionForEquippedTool : UseEquippedItemResult()
      object NoToolItemEquipped : UseEquippedItemResult()
      object Busy : UseEquippedItemResult()
      object Dead : UseEquippedItemResult()
      object Obstructed : UseEquippedItemResult()
   }

   fun useEquippedToolItem(
      interactingEntity: Entity,
      expectedItemConfigKey: String?
   ): UseEquippedItemResult {
      val equippedToolItemConfigAndStackIndex = interactingEntity.getEquippedItemConfigAndStackIndex(EquipmentSlot.Tool)

      if (equippedToolItemConfigAndStackIndex == null) {
         return UseEquippedItemResult.NoToolItemEquipped
      }

      if (interactingEntity.isDead) {
         return UseEquippedItemResult.Dead
      }

      if (!interactingEntity.isAvailableToPerformAction) {
         return UseEquippedItemResult.Busy
      }

      val equippedToolItemConfig = equippedToolItemConfigAndStackIndex.second

      if (expectedItemConfigKey != null &&
         equippedToolItemConfig.key != expectedItemConfigKey
      ) {
         return UseEquippedItemResult.UnexpectedEquippedItem
      }

      val characterComponent = interactingEntity.getComponent<CharacterComponentData>()
      val characterComponentData = characterComponent.data

      val spawnItemOnUseConfig = equippedToolItemConfig.spawnItemOnUseConfig
      if (spawnItemOnUseConfig != null) {
         val entityLocation = interactingEntity.resolvePosition()


         val characterIndexPair = this.collisionMap.pointToIndexPair(entityLocation)
         val topLeftCorner = characterIndexPair.copy(
            characterIndexPair.row - 1,
            characterIndexPair.col - 1
         )

         fun isAreaOpen() = this.collisionMap.isAreaOpen(
            topLeftCorner = topLeftCorner,
            width = 3,
            height = 3,
            flag = CollisionFlag.Placement
         )

         if (!isAreaOpen()) {
            return UseEquippedItemResult.Obstructed
         } else {
            this.applyPerformedAction(
               entity = interactingEntity,
               actionType = ActionType.UseEquippedTool,
               targetEntityId = null
            )

            this.queueCallbackAfterSimulationTimeDelay(
               simulationTimeDelay = 0.45,
               isValid = {
                  !interactingEntity.isDead && isAreaOpen()
               }
            ) {
               val spawnItemConfig = this.getConfig<ItemConfig>(spawnItemOnUseConfig.spawnItemConfigKey)

               val spawnedItems = this.spawnItems(
                  itemConfigKey = spawnItemOnUseConfig.spawnItemConfigKey,
                  quantity = spawnItemOnUseConfig.quantity,
                  baseLocation = entityLocation,
                  randomLocationScale = spawnItemOnUseConfig.randomDistanceScale
               )

               this.addActivityStreamEntry(
                  actionType = ActionType.UseEquippedTool,
                  actionResultType = ActionResultType.Success,

                  targetItemConfig = equippedToolItemConfig,

                  sourceLocation = entityLocation,
                  sourceEntityId = interactingEntity.entityId,

                  spawnedItems = spawnedItems.map {
                     SpawnedItemEntity(
                        amount = it.getComponent<ItemComponentData>().data.amount,
                        itemConfigKey = it.getComponent<ItemComponentData>().data.itemConfigKey,
                        entityId = it.entityId
                     )
                  }
               )
            }

            return UseEquippedItemResult.Success(
               equippedToolItemConfig = equippedToolItemConfig
            )
         }
      }

      return UseEquippedItemResult.NoActionForEquippedTool
   }

   enum class InteractWithEntityUsingEquippedItemResult {
      Success,
      NoToolItemEquipped,
      NoActionAvailable,
      TooFar,
      TargetEntityIsDead,
      InteractingEntityIsDead,
      InteractingEntityBusy
   }

   fun interactWithEntityUsingEquippedItem(
      interactingEntity: Entity,
      targetEntity: Entity
   ): InteractWithEntityUsingEquippedItemResult {
      val characterComponent = interactingEntity.getComponent<CharacterComponentData>()
      val characterComponentData = characterComponent.data

      val equippedToolItemConfigAndStackIndex = interactingEntity.getEquippedItemConfigAndStackIndex(EquipmentSlot.Tool)

      if (equippedToolItemConfigAndStackIndex == null) {
         return InteractWithEntityUsingEquippedItemResult.NoToolItemEquipped
      }

      val equippedStackIndex = equippedToolItemConfigAndStackIndex.first
      val equippedToolItemConfig = equippedToolItemConfigAndStackIndex.second

      val targetEntityIsDead = targetEntity.isDead

      if (targetEntityIsDead) {
         return InteractWithEntityUsingEquippedItemResult.TargetEntityIsDead
      }

      val interactingEntityIsKilled = interactingEntity.isDead

      if (interactingEntityIsKilled) {
         return InteractWithEntityUsingEquippedItemResult.InteractingEntityIsDead
      }

      if (!interactingEntity.isAvailableToPerformAction) {
         return InteractWithEntityUsingEquippedItemResult.InteractingEntityBusy
      }

      val targetItemComponent = targetEntity.getComponent<ItemComponentData>()
      val targetItemConfig = this.getConfig<ItemConfig>(targetItemComponent.data.itemConfigKey)

      val maxDistance = this.collisionMap.cellWidth

      val interactingEntityPosition = interactingEntity.resolvePosition()

      val nearestEdgeDistance = this.getNearestEdgeDistanceFromEntity(
         location = interactingEntityPosition,
         targetEntity = targetEntity
      )

      if (nearestEdgeDistance > maxDistance) {
         return InteractWithEntityUsingEquippedItemResult.TooFar
      }

      if (targetItemConfig.damageableConfig?.damageableByEquippedToolItemConfigKey != null &&
         targetItemConfig.damageableConfig.damageableByEquippedToolItemConfigKey == equippedToolItemConfig.key
      ) {
         val killableComponent = targetEntity.getComponent<DamageableComponentData>()

         this.applyPerformedAction(
            entity = interactingEntity,
            actionType = ActionType.UseToolToDamageEntity,
            targetEntityId = targetEntity.entityId,
            duration = 1.0
         )

         this.queueCallbackAfterSimulationTimeDelay(
            simulationTimeDelay = 0.45,
            isValid = { targetEntity.exists && killableComponent.data.killedAtTime == null }
         ) {
            val applyDamageResult = this.applyDamageToEntity(
               targetEntity = targetEntity,
               damage = 1000
            )

            if (applyDamageResult is ApplyDamageResult.Killed) {
               val spawnedItems = if (applyDamageResult.spawnedItemEntitiesOnKill.isNotEmpty()) {
                  applyDamageResult.spawnedItemEntitiesOnKill.map { spawnedEntity ->
                     val spawnedItemComponent = spawnedEntity.getComponent<ItemComponentData>()
                     val itemConfig = this.getConfig<ItemConfig>(spawnedItemComponent.data.itemConfigKey)
                     val amount = spawnedItemComponent.data.amount

                     SpawnedItemEntity(
                        itemConfigKey = itemConfig.key,
                        amount = amount,
                        entityId = spawnedEntity.entityId
                     )
                  }
               } else {
                  null
               }

               this.addActivityStreamEntry(
                  sourceEntityId = interactingEntity.entityId,
                  sourceLocation = interactingEntityPosition,

                  targetEntityId = targetEntity.entityId,
                  targetItemConfig = targetItemConfig,

                  actionItemConfig = equippedToolItemConfig,
                  actionType = ActionType.UseToolToKillEntity,
                  actionResultType = ActionResultType.Success,
                  spawnedItems = spawnedItems
               )
            } else {
               this.addActivityStreamEntry(
                  sourceEntityId = interactingEntity.entityId,
                  sourceLocation = interactingEntityPosition,

                  targetEntityId = targetEntity.entityId,
                  targetItemConfig = targetItemConfig,

                  actionItemConfig = equippedToolItemConfig,
                  actionType = ActionType.UseToolToDamageEntity,
                  actionResultType = ActionResultType.Success
               )
            }
         }

         return InteractWithEntityUsingEquippedItemResult.Success
      }

      if (targetItemConfig.growerConfig != null) {
         val growerComponent = targetEntity.getComponent<GrowerComponentData>()

         if (targetItemConfig.growerConfig.canReceiveGrowableItemConfigKeys.contains(equippedToolItemConfig.key) &&
            growerComponent.data.activeGrowth == null
         ) {
            this.applyPerformedAction(
               entity = interactingEntity,
               actionType = ActionType.PlaceGrowableInGrower,
               targetEntityId = null
            )

            this.queueCallbackAfterSimulationTimeDelay(
               simulationTimeDelay = 0.45,
               isValid = {
                  targetEntity.exists &&
                          growerComponent.data.activeGrowth == null &&
                          !interactingEntity.isDead &&
                          interactingEntity.getEquippedItemConfig(EquipmentSlot.Tool) == equippedToolItemConfig
               }
            ) {
               val didTake = interactingEntity.takeInventoryItemFromStack(
                  itemConfig = equippedToolItemConfig,
                  stackIndex = equippedStackIndex,
                  amountToTake = 1
               )

               if (didTake) {
                  this.addActivityStreamEntry(
                     sourceLocation = interactingEntity.resolvePosition(),
                     sourceEntityId = interactingEntity.entityId,

                     actionType = ActionType.PlaceGrowableInGrower,
                     actionResultType = ActionResultType.Success,

                     actionItemConfig = equippedToolItemConfig,

                     targetItemConfig = targetItemConfig,
                     targetEntityId = targetEntity.entityId
                  )

                  growerComponent.modifyData {
                     it.copy(
                        activeGrowth = ActiveGrowth(
                           startTime = this.getCurrentSimulationTime(),
                           itemConfigKey = equippedToolItemConfig.key
                        )
                     )
                  }
               }
            }

            return InteractWithEntityUsingEquippedItemResult.Success
         }
      }

      return InteractWithEntityUsingEquippedItemResult.NoActionAvailable
   }

   sealed interface ApplyDamageResult {
      data object AlreadyDead : ApplyDamageResult
      class Killed(
         val spawnedItemEntitiesOnKill: List<Entity> = listOf(),
         val droppedInventoryItemEntities: List<Entity> = listOf()
      ) : ApplyDamageResult

      class Damaged : ApplyDamageResult
   }

   fun applyDamageToEntity(
      targetEntity: Entity,
      damage: Int
   ): ApplyDamageResult {
      if (damage < 0) {
         throw Exception("Damage cannot be negative")
      }

      val killableComponent = targetEntity.getComponent<DamageableComponentData>()

      if (killableComponent.data.killedAtTime != null) {
         return ApplyDamageResult.AlreadyDead
      }

      val newHp = Math.max(0, killableComponent.data.hp - damage)

      val shouldKill = newHp == 0

      killableComponent.modifyData {
         it.copy(
            hp = newHp,
            killedAtTime = if (shouldKill) {
               this.getCurrentSimulationTime()
            } else {
               null
            }
         )
      }

      if (shouldKill) {
         this.collisionMap.clearEntity(targetEntity.entityId)
         this.collisionMapDebugInfoIsOutOfDate = true

         val spawnedItemEntitiesOnKill = mutableListOf<Entity>()
         val droppedInventoryItemEntities = mutableListOf<Entity>()

         val itemConfig = targetEntity.itemConfigOrNull

         if (itemConfig?.spawnItemOnKillConfig != null) {
            spawnedItemEntitiesOnKill.addAll(
               this.spawnItems(
                  itemConfigKey = itemConfig.spawnItemOnKillConfig.spawnItemConfigKey,
                  baseLocation = targetEntity.resolvePosition(),
                  randomLocationScale = 50.0,
                  quantity = itemConfig.spawnItemOnKillConfig.quantity
               )
            )
         }

         val inventoryComponent = targetEntity.getComponentOrNull<InventoryComponentData>()

         if (inventoryComponent != null) {
            val inventory = inventoryComponent.data.inventory

            var stackIndex = 0
            for (itemStack in inventory.itemStacks) {
               val inventoryItemConfig = this.getConfig<ItemConfig>(itemStack.itemConfigKey)

               if (inventoryItemConfig.storableConfig != null) {
                  val droppedItemEntity = this.dropItemStack(
                     droppingEntity = targetEntity,
                     expectedItemConfigKey = itemStack.itemConfigKey,
                     stackIndex = stackIndex
                  )

                  if (droppedItemEntity == null) {
                     throw Exception("Unexpected failure to drop item: " + inventoryItemConfig.key + ", " + stackIndex)
                  }

                  droppedInventoryItemEntities.add(droppedItemEntity)

                  // jshmrsn: Don't increment stack index to account for this stack being removed via dropping
               } else {
                  ++stackIndex
               }
            }
         }

         return ApplyDamageResult.Killed(
            spawnedItemEntitiesOnKill = spawnedItemEntitiesOnKill,
            droppedInventoryItemEntities = droppedInventoryItemEntities
         )
      } else {
         return ApplyDamageResult.Damaged()
      }
   }

   fun pickUpItem(
      pickingUpEntity: Entity,
      targetEntity: Entity
   ): PickUpItemResult {
      val targetItemComponent = targetEntity.getComponent<ItemComponentData>()
      val targetItemConfig = this.getConfig<ItemConfig>(targetItemComponent.data.itemConfigKey)

      val maxDistance = 0.5 * this.collisionMap.diagonalLength

      val pickingUpEntityPosition = pickingUpEntity.resolvePosition()

      val nearestEdgeDistance = this.getNearestEdgeDistanceFromEntity(
         location = pickingUpEntityPosition,
         targetEntity = targetEntity
      )

      if (nearestEdgeDistance > maxDistance) {
         return PickUpItemResult.TooFar
      }

      if (targetItemConfig.storableConfig == null) {
         throw Exception("Item can't be picked up: " + targetItemConfig.key)
      }

      pickingUpEntity.giveInventoryItem(
         itemConfigKey = targetItemConfig.key,
         amount = targetItemComponent.data.amount
      )

      targetEntity.destroy()

      this.applyPerformedAction(
         entity = pickingUpEntity,
         actionType = ActionType.PickUpItem,
         targetEntityId = null
      )

      val entityPosition = pickingUpEntity.resolvePosition()

      this.addActivityStreamEntry(
         sourceLocation = entityPosition,
         sourceEntityId = pickingUpEntity.entityId,

         actionType = ActionType.PickUpItem,
         actionResultType = ActionResultType.Success,

         targetItemConfig = targetItemConfig,
         targetEntityId = targetEntity.entityId
      )

      return PickUpItemResult.Success
   }

   sealed class MoveToResult {
      class Success(val movementId: String) : MoveToResult()
      data object PathNotFound : MoveToResult()
      data object NoPositionComponent : MoveToResult()
      data object Busy : MoveToResult()
   }


   fun startEntityMovement(
      entity: Entity,
      endPoint: Vector2,
      retryCount: Int = 0,
      maxRetryCount: Int = 5
   ): MoveToResult {
      if (!entity.isAvailableToPerformAction) {
         return MoveToResult.Busy
      }

      val observationRadius = entity.getComponent<CharacterComponentData>().data.observationRadius

      // joshr: Avoid leaking information about entities in fog-of-war areas
      val endPointClampedToObservationRadius =
         entity.resolvePosition().moveTowards(endPoint, maxDistance = observationRadius)

      val clampedEndPoint = this.collisionMap.clampPoint(endPointClampedToObservationRadius)

      val positionComponent = entity.getComponentOrNull<PositionComponentData>()

      if (positionComponent == null) {
         return MoveToResult.NoPositionComponent
      }

      val previousDestination = positionComponent.data.positionAnimation.keyFrames.lastOrNull()

      if (previousDestination != null) {
         if (previousDestination.value.distance(clampedEndPoint) < 0.01) {
            // jshmrsn: Avoid creating buffer delays when entity isn't actually going to change destination
            // This improves responsiveness of e.g. the auto-interaction system
            return MoveToResult.Success(
               movementId = positionComponent.data.movementId
            )
         }
      }

      val bufferTime = this.latencyBufferTime
      val currentSimulationTime = this.getCurrentSimulationTime()

      val moveFromTime = currentSimulationTime + bufferTime

      val startPoint = this.collisionMap.clampPoint(positionComponent.data.positionAnimation.resolve(moveFromTime))

      val adjustedEndPoint = clampedEndPoint.lerp(
         target = startPoint,
         percent = retryCount / maxRetryCount.toDouble()
      )

      val pathIndexPairs = this.findPath(
         startPoint = startPoint,
         endPoint = adjustedEndPoint
      )

      if (pathIndexPairs.isEmpty()) {
         return if (retryCount >= maxRetryCount) {
            MoveToResult.PathNotFound
         } else {
            this.startEntityMovement(
               entity = entity,
               endPoint = clampedEndPoint,
               retryCount = retryCount + 1,
               maxRetryCount = maxRetryCount
            )
         }
      }

      // jshmrsn: endPoint will be inside the destination cell, so we can safely move the entity directly
      // to the end point at the end of the path. This allows for e.g. walking to precise item pickup locations.
      val pathPoints =
         pathIndexPairs.map { this.collisionMap.indexPairToCellCenter(it) }
//            .subList(0, pathIndexPairs.size - 1) +
//                 clampedEndPoint

      val bufferKeyFrames = mutableListOf<Vector2KeyFrame>()

      bufferKeyFrames.add(
         Vector2KeyFrame(
            value = positionComponent.data.positionAnimation.resolve(currentSimulationTime),
            time = currentSimulationTime
         )
      )

      bufferKeyFrames.addAll(positionComponent.data.positionAnimation.keyFrames.filter { oldKeyFrame ->
         oldKeyFrame.time > currentSimulationTime && oldKeyFrame.time < moveFromTime
      })

      bufferKeyFrames.add(
         Vector2KeyFrame(
            value = startPoint,
            time = moveFromTime
         )
      )


      val speed = 120.0

      var accumulatedTime = moveFromTime

      val keyFrames = pathPoints.mapIndexed { index, pathPoint ->
         val previousPoint = if (index > 0) {
            pathPoints[index - 1]
         } else {
            startPoint
         }

         val distance = previousPoint.distance(pathPoint)
         val timeForDistance = distance / speed
         accumulatedTime += timeForDistance

         Vector2KeyFrame(
            value = pathPoint,
            time = accumulatedTime
         )
      }

      val movementId = buildShortRandomIdentifier()

      positionComponent.modifyData {
         it.copy(
            positionAnimation = Vector2Animation(
               keyFrames = bufferKeyFrames + keyFrames
            ),
            movementId = movementId
         )
      }

      return MoveToResult.Success(
         movementId = movementId
      )
   }

   fun spawnItems(
      itemConfig: ItemConfig,
      quantity: RandomItemQuantity,
      baseLocation: Vector2,
      randomLocationScale: Double = 0.0,
      randomLocationExponent: Double = 1.0,
   ): List<Entity> {
      val stackAmounts = quantity.resolveStackAmountsForItemConfig(itemConfig)

      return stackAmounts.map { amount ->
         this.spawnItem(
            itemConfig = itemConfig,
            amount = amount,
            location = baseLocation + Vector2.randomSignedXY(
               randomLocationScale,
               randomLocationScale,
               exponent = randomLocationExponent
            )
         )
      }
   }

   fun spawnItems(
      itemConfigKey: String,
      baseLocation: Vector2,
      randomLocationScale: Double = 0.0,
      randomLocationExponent: Double = 1.0,
      quantity: RandomItemQuantity
   ): List<Entity> {
      val itemConfig = this.getConfig<ItemConfig>(itemConfigKey)

      return this.spawnItems(
         itemConfig = itemConfig,
         quantity = quantity,
         baseLocation = baseLocation,
         randomLocationScale = randomLocationScale,
         randomLocationExponent = randomLocationExponent
      )
   }

   fun spawnItem(
      itemConfigKey: String,
      location: Vector2,
      amount: Int = 1
   ): Entity {
      val itemConfig = this.getConfig<ItemConfig>(itemConfigKey)

      return this.spawnItem(
         itemConfig = itemConfig,
         location = location,
         amount = amount
      )
   }

   fun spawnItem(
      itemConfig: ItemConfig,
      location: Vector2,
      amount: Int = 1
   ): Entity {
      val collisionConfig = itemConfig.collisionConfig
      val resolvedLocation = if (collisionConfig != null) {
         val locationWithOffset = location - collisionConfig.collisionOffset
         val desiredTopLeftCell = this.collisionMap.pointToIndexPair(locationWithOffset).let {
            IndexPair(
               row = it.row - (collisionConfig.height * 0.5).toInt(),
               col = it.col - (collisionConfig.width * 0.5).toInt()
            )
         }

         val openTopLeftCell = this.collisionMap.findOpenTopLeftCellForShapeOrFallback(
            startIndexPair = desiredTopLeftCell,
            fitShapeWidth = collisionConfig.width,
            fitShapeHeight = collisionConfig.height,
            flag = CollisionFlag.Placement
         )

         val openBottomRightCell = openTopLeftCell.let {
            IndexPair(
               row = it.row + collisionConfig.height - 1,
               col = it.col + collisionConfig.width - 1
            )
         }

         val topLeft = this.collisionMap.indexPairToCellTopLeftCorner(openTopLeftCell)
         val bottomRight = this.collisionMap.indexPairToCellBottomRightCorner(openBottomRightCell)
         val center = topLeft.lerp(bottomRight, 0.5)

         itemConfig.collisionConfig.collisionOffset + Vector2(
            x = center.x,
            y = center.y
         )
      } else {
         val desiredCell = this.collisionMap.pointToIndexPair(location)

         val openCell = this.collisionMap.findOpenTopLeftCellForShapeOrFallback(
            startIndexPair = desiredCell,
            fitShapeWidth = 1,
            fitShapeHeight = 1,
            flag = CollisionFlag.Placement
         )
         // jshmrsn: Consider maintaining within-cell offset
         this.collisionMap.indexPairToCellCenter(openCell)
      }

      val components = mutableListOf(
         ItemComponentData(
            itemConfigKey = itemConfig.key,
            amount = amount
         ),
         PositionComponentData(
            positionAnimation = Vector2Animation.static(resolvedLocation)
         ),
         FogOfWarComponentData()
      )

      if (itemConfig.damageableConfig != null) {
         components.add(
            DamageableComponentData(
               hp = itemConfig.damageableConfig.maxHp
            )
         )
      }

      if (itemConfig.growerConfig != null) {
         components.add(GrowerComponentData())
      }

      return this.createEntity(components)
   }

   fun buildRandomCharacterBodySelections(
      bodyType: String? = null,
      skinColor: String? = null,
      hairColor: String? = null,
      noHairChance: Double? = null
   ): CharacterBodySelections {
      val characterBodySelectionsConfig =
         this.getConfig<CharacterBodySelectionsConfig>("character-body-selections-config")

      return CharacterBodySelections(
         bodyType = bodyType ?: characterBodySelectionsConfig.bodyTypes.random(),
         skinColor = skinColor ?: characterBodySelectionsConfig.skinColors.random(),
         head = characterBodySelectionsConfig.heads.random(),
         body = characterBodySelectionsConfig.bodies.random(),
         nose = characterBodySelectionsConfig.noses.random(),
         eyes = characterBodySelectionsConfig.eyes.random().let {
            CompositeAnimationSelection(
               key = it.key,
               variant = it.includedVariants.random()
            )
         },
         wrinkles = characterBodySelectionsConfig.wrinkles.randomWithNullChance(1.0),
         hair = characterBodySelectionsConfig.hairs
            .filter { hairColor == null || it.includedVariants.contains(hairColor) }
            .randomWithNullChance(nullChance = noHairChance ?: 0.05)?.let {
               CompositeAnimationSelection(
                  key = it.key,
                  variant = hairColor ?: it.includedVariants.random()
               )
            }
      )
   }

   fun spawnAgentControlledCharacter(
      corePersonality: String = "Friendly",
      initialMemories: List<String> = listOf(),
      agentType: String,
      name: String = "Agent",
      age: Int = 25,
      bodySelections: CharacterBodySelections = this.buildRandomCharacterBodySelections(),
      location: Vector2
   ): Entity {
      return this.spawnCharacter(
         name = name,
         age = age,
         bodySelections = bodySelections,
         location = location,
         additionalComponents = listOf(
            AgentControlledComponentData(
               agentId = AgentId(buildShortRandomIdentifier()),
               corePersonality = corePersonality,
               initialMemories = initialMemories,
               agentType = agentType
            )
         )
      )
   }

   fun spawnCharacter(
      name: String = "Character",
      age: Int = 25,
      bodySelections: CharacterBodySelections = this.buildRandomCharacterBodySelections(),
      location: Vector2,
      additionalComponents: List<EntityComponentData> = listOf()
   ): Entity {
      val desiredCell = this.collisionMap.pointToIndexPair(location)

      val openCell = this.collisionMap.findOpenTopLeftCellForShapeOrFallback(
         startIndexPair = desiredCell,
         fitShapeWidth = 1,
         fitShapeHeight = 1,
         flag = CollisionFlag.Walking
      )

      val resolvedLocation = this.collisionMap.indexPairToCellCenter(openCell)

      val createdEntity = this.createEntity(
         additionalComponents + listOf(
            CharacterComponentData(
               name = name,
               age = age,
               bodySelections = bodySelections,
               observationRadius = 750.0
            ),
            DamageableComponentData(
               hp = 100
            ),
            InventoryComponentData(),
            PositionComponentData(
               positionAnimation = Vector2Animation.static(resolvedLocation)
            ),
            FogOfWarComponentData()
         )
      )

      val perspectiveEntityContainer = EntityContainer(
         simulation = this
      )

      this.perspectiveEntityContainersByCharacterEntityId[createdEntity.entityId] = perspectiveEntityContainer

      this.syncPerspectiveEntityContainer(
         perspectiveEntity = createdEntity,
         perspectiveEntityContainer = perspectiveEntityContainer
      )

      return createdEntity
   }

   fun getNearestEntities(
      searchLocation: Vector2,
      maxDistance: Double? = null,
      filter: (Entity) -> Boolean
   ): List<Entity> {
      return this.entities
         .mapNotNull { entity ->
            val positionComponent = entity.getComponentOrNull<PositionComponentData>()

            if (positionComponent != null) {
               val position = entity.resolvePosition()
               val distance = position.distance(searchLocation)

               if ((maxDistance == null || distance < maxDistance) && filter(entity)) {
                  Pair(entity, distance)
               } else {
                  null
               }
            } else {
               null
            }
         }
         .sortedBy { it.second }
         .map { it.first }
   }

   fun getNearestEntity(
      searchLocation: Vector2,
      maxDistance: Double? = null,
      filter: (Entity) -> Boolean
   ): Entity? {
      return this.getNearestEntities(
         searchLocation = searchLocation,
         maxDistance = maxDistance,
         filter = filter
      ).firstOrNull()
   }

   override fun handleClientMessage(
      client: Client,
      messageType: String,
      messageData: JsonObject,
      isAdminRequest: Boolean
   ) {
      val userControlledEntity = this.getUserControlledEntities(userId = client.userId).firstOrNull()
      val playerPosition = userControlledEntity?.resolvePosition() ?: Vector2.zero
      fun requireAdmin() {
         if (!isAdminRequest) {
            sendAlertMessage(client, "Admin access required for command: " + messageType)
            throw Exception("Admin required: " + messageType)
         }
      }

      when (messageType) {
         "SpawnRequest" -> {
            if (userControlledEntity != null) {
               this.sendAlertMessage(client, "Already spawned")
            } else {
               this.spawnPlayerCharacterForUserIfNeeded(client.userId)
            }
         }

         "DespawnRequest" -> {
            if (userControlledEntity != null) {
               userControlledEntity.destroy()
            }
         }

         "MoveToPointRequest" -> {
            client.notifyInteractionReceived()
            val request = Json.decodeFromJsonElement<MoveToPointRequest>(messageData)
            this.handleMoveToPointRequest(client, request)
         }

         "SetShouldSpectateByDefaultRequest" -> {
            client.notifyInteractionReceived()
            val request = Json.decodeFromJsonElement<SetShouldSpectateByDefaultRequest>(messageData)
            this.getGameClientStateComponent(client).modifyData {
               it.copy(
                  shouldSpectateByDefault = request.shouldSpectateByDefault
               )
            }
         }

         "SetPerspectiveEntityIdOverrideRequest" -> {
            client.notifyInteractionReceived()
            val request = Json.decodeFromJsonElement<SetPerspectiveEntityIdOverrideRequest>(messageData)
            this.getGameClientStateComponent(client).modifyData {
               it.copy(
                  perspectiveEntityIdOverride = request.perspectiveEntityIdOverride
               )
            }
         }

         "NotifyClientActive" -> {
            client.notifyInteractionReceived()
         }

         "UseEquippedToolItemRequest" -> {
            client.notifyInteractionReceived()
            val request = Json.decodeFromJsonElement<UseEquippedToolItemRequest>(messageData)
            this.handleUseEquippedItemRequest(client, request)
         }

         "EquipItemRequest" -> {
            client.notifyInteractionReceived()
            val request = Json.decodeFromJsonElement<EquipItemRequest>(messageData)
            this.handleEquipItemRequest(client, request)
         }

         "UnequipItemRequest" -> {
            client.notifyInteractionReceived()
            val request = Json.decodeFromJsonElement<UnequipItemRequest>(messageData)
            this.handleUnequipItemRequest(client, request)
         }

         "CraftItemRequest" -> {
            client.notifyInteractionReceived()
            val request = Json.decodeFromJsonElement<CraftItemRequest>(messageData)
            this.handleCraftItemRequest(client, request)
         }

         "ReRollRequest" -> {
            client.notifyInteractionReceived()

            val entity = this.getUserControlledEntities(userId = client.userId).firstOrNull()

            if (entity != null) {
               val characterComponent = entity.getComponent<CharacterComponentData>()

               characterComponent.modifyData {
                  it.copy(
                     bodySelections = this.buildRandomCharacterBodySelections()
                  )
               }
            }
         }


         "ClearPendingInteractionTargetRequest" -> {
            client.notifyInteractionReceived()
            val request = Json.decodeFromJsonElement<ClearPendingInteractionTargetRequest>(messageData)
            this.handleClearPendingInteractionRequest(client, request)
         }

         "DropItemRequest" -> {
            client.notifyInteractionReceived()
            val request = Json.decodeFromJsonElement<DropItemRequest>(messageData)
            this.handleDropItemRequest(client, request)
         }

         "PauseAiRequest" -> {
            client.notifyInteractionReceived()
            this.debugInfoComponent.modifyData {
               it.copy(
                  aiPaused = true
               )
            }
         }

         "ResumeAiRequest" -> {
            requireAdmin()
            client.notifyInteractionReceived()
            this.debugInfoComponent.modifyData {
               it.copy(
                  aiPaused = false
               )
            }
         }

         "AddCharacterMessageRequest" -> {
            client.notifyInteractionReceived()
            this.handleAddCharacterMessageRequest(
               messageData = messageData,
               client = client,
               playerPosition = playerPosition,
               userControlledEntity = userControlledEntity,
               isAdminRequest = isAdminRequest
            )
         }

         else -> throw Exception("Unhandled client message type: $messageType")
      }
   }

   private fun handleAddCharacterMessageRequest(
      messageData: JsonObject,
      client: Client,
      playerPosition: Vector2,
      userControlledEntity: Entity?,
      isAdminRequest: Boolean
   ) {
      val request = Json.decodeFromJsonElement<AddCharacterMessageRequest>(messageData)

      if (request.message.startsWith("/")) {
         this.handleServerCommandRequest(
            message = request,
            client = client,
            playerPosition = playerPosition,
            userControlledEntity = userControlledEntity,
            isAdminRequest = isAdminRequest
         )
      } else if (userControlledEntity != null) {
         this.addCharacterMessage(userControlledEntity, request.message)
      }
   }

   private fun handleServerCommandRequest(
      message: AddCharacterMessageRequest,
      client: Client,
      playerPosition: Vector2,
      userControlledEntity: Entity?,
      isAdminRequest: Boolean
   ) {
      val nearestOtherCharacterEntity = this.getNearestEntity(
         searchLocation = playerPosition,
         filter = { entity ->
            entity != userControlledEntity && entity.getComponentOrNull<CharacterComponentData>() != null
         }
      )

      val components = message.message.split(" ")
      val command = components.first().replace("/", "")

      fun requireAdmin() {
         if (!isAdminRequest) {
            sendAlertMessage(client, "Admin access required for command: " + command)
            throw Exception("Admin required: " + command)
         }
      }

      when (command) {
         "name" -> {
            val entity = this.getUserControlledEntities(userId = client.userId).firstOrNull()
            val name = components.getOrNull(1)

            if (entity != null && name != null) {
               val characterComponent = entity.getComponent<CharacterComponentData>()
               //this.broadcastAlertAsGameMessage("${characterComponent.data.name} changed name to $name")

               characterComponent.modifyData {
                  it.copy(
                     name = name
                  )
               }
            }
         }

         "debug" -> {
            requireAdmin()
            this.enableDetailedInfo = !this.enableDetailedInfo
            this.collisionMapDebugInfoIsOutOfDate = true
         }

         "reroll" -> {
            val entity = this.getUserControlledEntities(userId = client.userId).firstOrNull()
            val gender = components.getOrNull(1)
            val skinColor = components.getOrNull(2)
            val hairColor = components.getOrNull(3)

            if (entity != null) {
               val characterComponent = entity.getComponent<CharacterComponentData>()

               characterComponent.modifyData {
                  it.copy(
                     bodySelections = this.buildRandomCharacterBodySelections(
                        bodyType = gender,
                        skinColor = skinColor,
                        hairColor = hairColor,
                        noHairChance = if (hairColor != null) 0.0 else null
                     )
                  )
               }
            }
         }

         "spawn-item" -> {
            requireAdmin()

            val configKey = components.getOrNull(1) ?: "stone"
            val amount = components.getOrNull(2)?.toInt() ?: 1
            val stackCount = components.getOrNull(3)?.toInt()

            val itemQuantity = if (stackCount != null) {
               RandomItemQuantity.stacksOfAmount(
                  stackCount = RandomConfig.fixed(stackCount),
                  amountPerStack = RandomConfig.fixed(amount)
               )
            } else {
               RandomItemQuantity.amount(amount)
            }

            this.spawnItems(
               itemConfigKey = configKey,
               quantity = itemQuantity,
               baseLocation = playerPosition,
               randomLocationScale = 150.0
            )
         }

         "take-item" -> {
            requireAdmin()

            val configKey = components.getOrNull(1) ?: "stone"
            val amountPerStack = components.getOrNull(2)?.toInt() ?: 1
            val stacks = components.getOrNull(3)?.toInt() ?: 1

            if (userControlledEntity != null) {
               for (i in 0..(stacks - 1)) {
                  val remaining = userControlledEntity.getInventoryItemTotalAmount(
                     itemConfigKey = configKey
                  )

                  userControlledEntity.takeInventoryItem(
                     itemConfigKey = configKey,
                     amount = Math.min(amountPerStack, remaining)
                  )
               }
            }
         }

         "give-item" -> {
            requireAdmin()

            val configKey = components.getOrNull(1) ?: "stone"
            val amountPerStack = components.getOrNull(2)?.toInt() ?: 1
            val stacks = components.getOrNull(3)?.toInt() ?: 1

            if (userControlledEntity != null) {
               for (i in 0..(stacks - 1)) {
                  userControlledEntity.giveInventoryItem(
                     itemConfigKey = configKey,
                     amount = amountPerStack
                  )
               }
            }
         }

         "give-item-near" -> {
            requireAdmin()

            val configKey = components.getOrNull(1) ?: "stone"
            val amountPerStack = components.getOrNull(2)?.toInt() ?: 1
            val stacks = components.getOrNull(3)?.toInt() ?: 1

            if (nearestOtherCharacterEntity != null) {
               for (i in 0..(stacks - 1)) {
                  nearestOtherCharacterEntity.giveInventoryItem(
                     itemConfigKey = configKey,
                     amount = amountPerStack
                  )
               }
            }
         }

         "save-replay" -> {
            requireAdmin()
            this.saveReplay()
         }

         "spawn-agent" -> {
            requireAdmin()

            val agentType = components.getOrNull(1) ?: AgentControlledComponentData.defaultAgentType
            val name = components.getOrNull(2)

            val spawnLocation = playerPosition + Vector2.randomSignedXY(150.0)

            this.spawnAgentControlledCharacter(
               name = name ?: "Agent",
               corePersonality = "Friendly. Enjoys conversation. Enjoys walking around randomly.",
               initialMemories = listOf(
                  "I want to make progress.",
                  "I should be nice to new people in case we can become friends, but if they mistreat me I should stop doing what they tell me to do."
               ),
               agentType = agentType,
               bodySelections = this.buildRandomCharacterBodySelections(),
               age = 25,
               location = spawnLocation
            )
         }

         else -> {
            sendAlertMessage(client, "Unknown server command: ${message.message}")
         }
      }
   }

   fun findPath(
      startPoint: Vector2,
      endPoint: Vector2
   ): List<IndexPair> {
      return this.collisionMap.findPath(
         startPoint = startPoint,
         endPoint = endPoint,
         flag = CollisionFlag.Walking
      )
   }

   override fun broadcastAlertAsGameMessage(message: String) {
      val lines = message.lines()

      if (lines.size > 1) {
         this.addActivityStreamEntry(
            title = "System Alert",
            message = lines.first(),
            longMessage = message,
            shouldReportToAi = false
         )
      } else {
         this.addActivityStreamEntry(
            title = "System Alert",
            message = message,
            shouldReportToAi = false
         )
      }
   }

   fun spawnPlayerCharacterForUserIfNeeded(userId: UserId) {
      val existingControlledEntity = this.entities.find {
         val userControlled = it.getComponentOrNull<UserControlledComponentData>()

         if (userControlled != null) {
            userControlled.data.userId == userId
         } else {
            false
         }
      }

      if (existingControlledEntity != null) {
         println("Controlled entity already exists for client: " + userId)
         return
      }

      this.spawnCharacter(
         name = "Player",
         age = 30,
         bodySelections = this.buildRandomCharacterBodySelections(),
         location = Vector2(
            2500.0 + (this.entities.size % 8) * 50.0,
            2500.0
         ),
         additionalComponents = listOf(
            UserControlledComponentData(
               userId = userId
            )
         )
      )
   }

   fun buildGameClientStateEntityId(client: Client): EntityId {
      return EntityId("game-client-state:" + client.clientId.value)
   }

   fun getGameClientStateComponent(client: Client) =
      this.getEntity(
         entityId = this.buildGameClientStateEntityId(client)
      ).getComponent<GameClientStateComponentData>()

   override fun handleNewClient(client: Client) {
      val gameClientStateEntityId = this.buildGameClientStateEntityId(client)

      if (this.getEntityOrNull(gameClientStateEntityId) == null) {
         this.createEntity(
            entityId = gameClientStateEntityId,
            components = listOf(GameClientStateComponentData())
         )
      }

      val isCreator = this.context.createdByUserSecret == client.userSecret

      val shouldSpawnPlayerEntity = when (this.gameScenario.autoSpawnPlayersEntityMode) {
         SpawnPlayersMode.All -> true
         SpawnPlayersMode.NonCreator -> !isCreator
         SpawnPlayersMode.None -> false
      }

      if (shouldSpawnPlayerEntity) {
         this.spawnPlayerCharacterForUserIfNeeded(client.userId)
      }
   }

   fun getUserControlledEntities(userId: UserId) = this.entities.filter {
      val userControlledComponent = it.getComponentOrNull<UserControlledComponentData>()

      if (userControlledComponent != null) {
         userControlledComponent.data.userId == userId
      } else {
         false
      }
   }

   fun getCraftingRecipeInfos(
      crafterEntity: Entity
   ): List<CraftingRecipeInfo> {
      val itemConfigs = this.configs
         .mapNotNull { it as? ItemConfig }

      return itemConfigs.mapNotNull {
         if (it.craftableConfig != null) {
            CraftingRecipeInfo(
               itemConfigKey = it.key,
               itemName = it.name,
               description = it.description,
               cost = it.craftableConfig.craftingCost,
               amount = it.craftableConfig.craftingAmount,
               canCurrentlyAfford = crafterEntity.canAfford(it.craftableConfig.craftingCost)
            )
         } else {
            null
         }
      }
   }

   fun autoInteractWithEntity(
      entity: Entity,
      targetEntity: Entity,
      expectedActionType: ActionType,
      callback: (ActionResultType) -> Unit
   ) {
      val movementResult = this.startEntityMovement(
         entity = entity,
         endPoint = targetEntity.resolvePosition()
      )

      if (movementResult !is MoveToResult.Success) {
         callback(ActionResultType.FailedToMoveForAction)
      } else {
         val characterComponent = entity.getComponent<CharacterComponentData>()

         this.pendingAutoInteractCallbackByEntityId[entity.entityId] = callback

         characterComponent.modifyData {
            it.copy(
               pendingInteractionTargetEntityId = targetEntity.entityId,
               pendingInteractionActionType = expectedActionType
            )
         }
      }
   }
}