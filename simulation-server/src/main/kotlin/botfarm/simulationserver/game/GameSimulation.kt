package botfarm.simulationserver.game

import botfarm.apidata.AgentId
import botfarm.apidata.EntityId
import botfarm.misc.*
import botfarm.simulationserver.game.ai.AgentServerIntegration
import botfarm.simulationserver.common.*
import botfarm.simulationserver.simulation.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

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
class PickUpItemMessage(
   val targetEntityId: EntityId
)

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
   val agentServerIntegration: AgentServerIntegration,
   data: SimulationData,
   simulationContainer: SimulationContainer,
   systems: Systems = Systems.default
) : Simulation(
   data = data,
   simulationContainer = simulationContainer,
   systems = systems
) {
   companion object {
      val activityStreamEntityId = EntityId("activity-stream")
   }

   private val collisionMap: List<List<Boolean>>
   val collisionMapRowCount: Int
   val collisionMapColumnCount: Int
   private val collisionWorldWidth: Double
   private val collisionWorldHeight: Double

   init {
      val rowCount = 200
      val columnCount = 200

      val collisionMap = mutableListOf<List<Boolean>>()

      for (rowIndex in 0..(rowCount - 1)) {
         val row = (0..(columnCount - 1)).map { true }
         collisionMap.add(row)
      }

      val tilewidth = 32
      val tileheight = 32
      this.collisionMap = collisionMap
      this.collisionMapRowCount = rowCount
      this.collisionMapColumnCount = columnCount
      this.collisionWorldWidth = columnCount * tilewidth.toDouble()
      this.collisionWorldHeight = rowCount * tileheight.toDouble()
   }

   fun getActivityStreamEntity() =
      this.getEntityOrNull(Companion.activityStreamEntityId) ?: throw Exception("activity-stream entity not found")

   fun getActivityStream(): List<ActivityStreamEntry> {
      val activityStreamEntity = this.getActivityStreamEntity()
      return activityStreamEntity.getComponent<ActivityStreamComponentData>().data.activityStream
   }

   fun addActivityStreamEntry(entry: ActivityStreamEntry) {
      val activityStreamEntity = this.getActivityStreamEntity()
      val activityStreamComponent = activityStreamEntity.getComponent<ActivityStreamComponentData>()
      activityStreamComponent.modifyData {
         it.copy(
            activityStream = it.activityStream + entry
         )
      }
   }


   fun addCharacterMessage(entity: Entity, message: String) {
      val characterComponent = entity.getComponentOrNull<CharacterComponentData>()

      if (characterComponent != null) {
         val currentTime = this.getCurrentSimulationTime()
         val location = entity.getComponent<PositionComponentData>().data.positionAnimation.resolve(currentTime)

         characterComponent.modifyData {
            it.copy(
               recentSpokenMessages = it.recentSpokenMessages.filter { spokenMessage ->
                  val age = currentTime - spokenMessage.sentSimulationTime
                  age < 30.0 // todo
               } + SpokenMessage(
                  sentSimulationTime = currentTime,
                  message = message
               ))
         }

         this.addActivityStreamEntry(
            ActivityStreamEntry(
               title = "${characterComponent.data.name} said...",
               actionType = "speak",
               message = message,
               time = currentTime,
               sourceLocation = location,
               sourceEntityId = entity.entityId,
               sourceIconPath = null,
               shouldReportToAi = false // AI is made aware of conversations through separate mechanism
            )
         )
      }
   }

   private fun handleClearPendingInteractionRequest(client: Client, message: ClearPendingInteractionTargetRequest) {
      val entity = this.getUserControlledEntities(userId = client.userId).firstOrNull()

      if (entity == null) {
         sendAlertMessage(client, "No controlled entity")
         return
      }

      val characterComponent = entity.getComponent<CharacterComponentData>()
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
   ): CraftItemResult {
      val itemConfig = this.getConfig<ItemConfig>(itemConfigKey)

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
         ActivityStreamEntry(
            time = this.getCurrentSimulationTime(),
            title = "$name crafted a ${itemConfig.name}",
            sourceLocation = entityPosition,
            targetIconPath = itemConfig.iconUrl,
            actionType = "craft"
         )
      )

      if (itemConfig.storableConfig != null) {
         entity.giveInventoryItem(
            itemConfigKey = itemConfigKey,
            amount = craftableConfig.craftingAmount
         )
      } else {
         this.spawnItems(
            itemConfigKey = itemConfigKey,
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
      requestedStackIndex: Int?
   ): EquipItemResult {
      val inventoryComponent = entity.getComponent<InventoryComponentData>()
      val characterComponent = entity.getComponent<CharacterComponentData>()

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

      val name = characterComponent.data.name

      this.addActivityStreamEntry(
         ActivityStreamEntry(
            time = this.getCurrentSimulationTime(),
            sourceIconPath = null,
            title = "$name equipped ${itemConfigToEquip.name}",
            sourceLocation = entity.resolvePosition(),
            targetIconPath = itemConfigToEquip.iconUrl,
            actionType = "equipItem"
         )
      )

      return EquipItemResult.Success
   }

   fun unequipItem(
      entity: Entity,
      expectedItemConfigKey: String,
      requestedStackIndex: Int?
   ): EquipItemResult {
      val inventoryComponent = entity.getComponent<InventoryComponentData>()
      val characterComponent = entity.getComponent<CharacterComponentData>()

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

      val name = characterComponent.data.name

      this.addActivityStreamEntry(
         ActivityStreamEntry(
            time = this.getCurrentSimulationTime(),
            sourceIconPath = null,
            title = "$name unequipped ${itemConfig.name}",
            sourceLocation = entity.resolvePosition(),
            targetIconPath = itemConfig.iconUrl,
            actionType = "unequipItem"
         )
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

      val result = this.moveEntityToPoint(
         entity = entity,
         endPoint = message.point
      )

      val characterComponent = entity.getComponent<CharacterComponentData>()

      if (result != MoveToResult.Success) {
         characterComponent.modifyData {
            it.copy(
               pendingInteractionTargetEntityId = null,
               pendingUseEquippedToolItemRequest = null
            )
         }
         sendAlertMessage(client, "Move to point failed: " + result.name)
      } else {
         characterComponent.modifyData {
            it.copy(
               pendingInteractionTargetEntityId = message.pendingInteractionEntityId,
               pendingUseEquippedToolItemRequest = message.pendingUseEquippedToolItemRequest
            )
         }
      }
   }

   fun handlePickUpItemMessage(client: Client, message: PickUpItemMessage) {
      val entity = this.getUserControlledEntities(userId = client.userId).firstOrNull()

      if (entity == null) {
         sendAlertMessage(client, "No controlled entity")
         return
      }

      val targetEntity = this.getEntityOrNull(message.targetEntityId)

      if (targetEntity == null) {
         sendAlertMessage(client, "Item to pickup not found")
         return
      }

      val result = this.pickUpItem(
         pickingUpEntity = entity,
         targetEntity = targetEntity
      )

      when (result) {
         PickUpItemResult.Success -> {}
         PickUpItemResult.TooFar -> {
            sendAlertMessage(client, "Item was too far away to pickup")
         }
      }
   }

   private fun handleDropItemRequest(client: Client, message: DropItemRequest) {
      val entity = this.getUserControlledEntities(userId = client.userId).firstOrNull()

      if (entity == null) {
         sendAlertMessage(client, "No controlled entity")
         return
      }

      val didDrop = this.dropItemStack(
         droppingEntity = entity,
         expectedItemConfigKey = message.itemConfigKey,
         amountToDropFromStack = message.amountFromStack,
         stackIndex = message.stackIndex
      )

      if (!didDrop) {
         sendAlertMessage(client, "Not enough to drop")
      }
   }

   fun dropItemStack(
      droppingEntity: Entity,
      expectedItemConfigKey: String,
      stackIndex: Int,
      amountToDropFromStack: Int? = null
   ): Boolean {
      val itemConfig = this.getConfig<ItemConfig>(expectedItemConfigKey)

      if (itemConfig.storableConfig == null || !itemConfig.storableConfig.canBeDropped) {
         throw Exception("Item cannot be dropped")
      }

      val inventoryComponent = droppingEntity.getComponent<InventoryComponentData>()
      val inventory = inventoryComponent.data.inventory
      val stack = inventory.itemStacks.getOrNull(stackIndex)

      if (stack == null || stack.itemConfigKey != expectedItemConfigKey) {
         return false
      }

      val resolvedAmountToDrop: Int
      if (amountToDropFromStack != null) {
         if (amountToDropFromStack <= stack.amount) {
            resolvedAmountToDrop = amountToDropFromStack
         } else {
            return false
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


      this.spawnItem(
         itemConfigKey = stack.itemConfigKey,
         amount = resolvedAmountToDrop,
         location = droppingEntity.resolvePosition() + Vector2.randomSignedXY(50.0)
      )

      val character = droppingEntity.getComponentOrNull<CharacterComponentData>()
      val name = character?.data?.name ?: "?"

      this.addActivityStreamEntry(
         ActivityStreamEntry(
            time = this.getCurrentSimulationTime(),
            sourceIconPath = null,
            title = "$name dropped a ${itemConfig.name}",
            sourceLocation = droppingEntity.resolvePosition(),
            targetIconPath = itemConfig.iconUrl,
            actionType = "dropItem"
         )
      )

      return true
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

   enum class UseEquippedItemResult {
      Success,
      UnexpectedEquippedItem,
      NoActionForEquippedTool,
      NoToolItemEquipped,
      Busy,
      Dead
   }

   val maxAvailableGrowerDistance = 60.0

   fun useEquippedToolItem(
      interactingEntity: Entity,
      expectedItemConfigKey: String?
   ): UseEquippedItemResult {
      if (interactingEntity.isDead) {
         return UseEquippedItemResult.Dead
      }

      if (!interactingEntity.isAvailableToPerformAction) {
         return UseEquippedItemResult.Busy
      }

      val equippedToolItemConfigAndStackIndex = interactingEntity.getEquippedItemConfigAndStackIndex(EquipmentSlot.Tool)

      if (equippedToolItemConfigAndStackIndex == null) {
         return UseEquippedItemResult.NoToolItemEquipped
      }

      val equippedStackIndex = equippedToolItemConfigAndStackIndex.first
      val equippedToolItemConfig = equippedToolItemConfigAndStackIndex.second

      if (expectedItemConfigKey != null &&
         equippedToolItemConfig.key != expectedItemConfigKey
      ) {
         return UseEquippedItemResult.UnexpectedEquippedItem
      }

      val characterComponent = interactingEntity.getComponent<CharacterComponentData>()
      val characterComponentData = characterComponent.data
      val interactingEntityName = characterComponentData.name


      val spawnItemOnUseConfig = equippedToolItemConfig.spawnItemOnUseConfig
      if (spawnItemOnUseConfig != null) {
         fun getBlockingEntities() = this.getNearestEntities(
            searchLocation = interactingEntity.resolvePosition(),
            maxDistance = 100.0,
            filter = {
               val itemConfigKey = it.getComponentOrNull<ItemComponentData>()?.data?.itemConfigKey

               if (itemConfigKey != null) {
                  val itemConfig = this.getConfig<ItemConfig>(itemConfigKey)
                  itemConfig.blocksPlacement
               } else {
                  false
               }
            }
         )


         if (getBlockingEntities().isEmpty()) {
            this.applyPerformedAction(
               entity = interactingEntity,
               actionType = ActionType.UseEquippedTool,
               targetEntityId = null
            )

            this.queueCallbackAfterSimulationTimeDelay(
               simulationTimeDelay = 0.45,
               isValid = { !interactingEntity.isDead && getBlockingEntities().isEmpty() }
            ) {
               val spawnItemConfig = this.getConfig<ItemConfig>(spawnItemOnUseConfig.spawnItemConfigKey)

               this.addActivityStreamEntry(
                  ActivityStreamEntry(
                     time = this.getCurrentSimulationTime(),
                     sourceIconPath = null,
                     title = "$interactingEntityName created a ${spawnItemConfig.name}",
                     sourceLocation = interactingEntity.resolvePosition(),
                     targetIconPath = spawnItemConfig.iconUrl,
                     actionType = "spawnOnUse"
                  )
               )

               this.spawnItems(
                  itemConfigKey = spawnItemOnUseConfig.spawnItemConfigKey,
                  quantity = spawnItemOnUseConfig.quantity,
                  baseLocation = interactingEntity.resolvePosition(),
                  randomLocationScale = spawnItemOnUseConfig.randomDistanceScale
               )
            }
         }

         return UseEquippedItemResult.Success
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

      val interactingEntityName = characterComponentData.name

      val maxDistance = 50.0

      val interactingEntityPosition = interactingEntity.resolvePosition()
      val targetEntityPosition = targetEntity.resolvePosition()

      val distance = interactingEntityPosition.distance(targetEntityPosition)

      if (distance > maxDistance) {
         return InteractWithEntityUsingEquippedItemResult.TooFar
      }

      if (targetItemConfig.killableConfig?.canBeDamagedByToolItemConfigKey != null &&
         targetItemConfig.killableConfig.canBeDamagedByToolItemConfigKey == equippedToolItemConfig.key
      ) {
         val killableComponent = targetEntity.getComponent<KillableComponentData>()

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
            this.addActivityStreamEntry(
               ActivityStreamEntry(
                  time = this.getCurrentSimulationTime(),
                  title = "$interactingEntityName harvested a ${targetItemConfig.name} using a ${equippedToolItemConfig.name}",
                  sourceIconPath = null,
                  sourceLocation = interactingEntityPosition,
                  targetIconPath = targetItemConfig.iconUrl,
                  actionIconPath = equippedToolItemConfig.iconUrl,
                  actionType = "harvest"
               )
            )

            this.applyDamageToEntity(
               targetEntity = targetEntity,
               damage = 1000
            )
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
               this.addActivityStreamEntry(
                  ActivityStreamEntry(
                     time = this.getCurrentSimulationTime(),
                     sourceIconPath = null,
                     title = "$interactingEntityName planted ${equippedToolItemConfig.name}",
                     sourceLocation = interactingEntity.resolvePosition(),
                     targetIconPath = equippedToolItemConfig.iconUrl,
                     actionType = "placeGrowableInGrower"
                  )
               )

               growerComponent.modifyData {
                  it.copy(
                     activeGrowth = ActiveGrowth(
                        startTime = this.getCurrentSimulationTime(),
                        itemConfigKey = equippedToolItemConfig.key
                     )
                  )
               }

               interactingEntity.takeInventoryItemFromStack(
                  itemConfigKey = equippedToolItemConfig.spriteConfigKey,
                  stackIndex = equippedStackIndex,
                  amountToTake = 1
               )
            }

            return InteractWithEntityUsingEquippedItemResult.Success
         }
      }

      return InteractWithEntityUsingEquippedItemResult.NoActionAvailable
   }

   fun applyDamageToEntity(
      targetEntity: Entity,
      damage: Int
   ) {
      if (damage < 0) {
         throw Exception("Damage cannot be negative")
      }

      val killableComponent = targetEntity.getComponent<KillableComponentData>()

      if (killableComponent.data.killedAtTime != null) {
         return
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
         val itemComponent = targetEntity.getComponentOrNull<ItemComponentData>()

         if (itemComponent != null) {
            val itemConfig = this.getConfig<ItemConfig>(itemComponent.data.itemConfigKey)

            if (itemConfig.spawnItemOnDestructionConfig != null) {
               this.spawnItems(
                  itemConfigKey = itemConfig.spawnItemOnDestructionConfig.spawnItemConfigKey,
                  baseLocation = targetEntity.resolvePosition(),
                  randomLocationScale = 50.0,
                  quantity = itemConfig.spawnItemOnDestructionConfig.quantity
               )
            }
         }

         val inventoryComponent = targetEntity.getComponentOrNull<InventoryComponentData>()

         if (inventoryComponent != null) {
            val inventory = inventoryComponent.data.inventory

            while (true) {
               var stackIndex = 0
               for (itemStack in inventory.itemStacks) {
                  val inventoryItemConfig = this.getConfig<ItemConfig>(itemStack.itemConfigKey)

                  if (inventoryItemConfig.storableConfig != null) {
                     val didDrop = this.dropItemStack(
                        droppingEntity = targetEntity,
                        expectedItemConfigKey = itemStack.itemConfigKey,
                        stackIndex = stackIndex
                     )

                     if (!didDrop) {
                        throw Exception("Unexpected failure to drop item: " + inventoryItemConfig.key + ", " + stackIndex)
                     }

                     // jshmrsn: Don't increment stack index to account for this stack being removed via dropping
                  } else {
                     ++stackIndex
                  }
               }
            }
         }
      }
   }

   fun pickUpItem(
      pickingUpEntity: Entity,
      targetEntity: Entity
   ): PickUpItemResult {
      val itemComponent = targetEntity.getComponent<ItemComponentData>()
      val itemConfig = this.getConfig<ItemConfig>(itemComponent.data.itemConfigKey)

      val maxDistance = 50.0

      val pickingUpEntityPosition = pickingUpEntity.resolvePosition()
      val targetEntityPosition = targetEntity.resolvePosition()

      val distance = pickingUpEntityPosition.distance(targetEntityPosition)

      if (distance > maxDistance) {
         return PickUpItemResult.TooFar
      }

      if (itemConfig.storableConfig == null) {
         throw Exception("Item can't be picked up: " + itemConfig.key)
      }

      pickingUpEntity.giveInventoryItem(
         itemConfigKey = itemConfig.key,
         amount = itemComponent.data.amount
      )

      targetEntity.destroy()

      val characterComponentData = pickingUpEntity.getComponentOrNull<CharacterComponentData>()?.data

      this.applyPerformedAction(
         entity = pickingUpEntity,
         actionType = ActionType.PickupItem,
         targetEntityId = null
      )

      val name = characterComponentData?.name ?: "?"
      val entityPosition = pickingUpEntity.resolvePosition()

      this.addActivityStreamEntry(
         ActivityStreamEntry(
            time = this.getCurrentSimulationTime(),
            sourceEntityId = pickingUpEntity.entityId,
            sourceIconPath = null,
            title = "$name picked up a ${itemConfig.name}",
            sourceLocation = entityPosition,
            targetIconPath = itemConfig.iconUrl,
            actionType = "pickup"
         )
      )

      return PickUpItemResult.Success
   }

   enum class MoveToResult {
      Success,
      PathNotFound,
      NoPositionComponent
   }

   fun moveEntityToPoint(
      entity: Entity,
      endPoint: Vector2,
      retryCount: Int = 0,
      maxRetryCount: Int = 5
   ): MoveToResult {
      val positionComponent = entity.getComponentOrNull<PositionComponentData>()

      if (positionComponent == null) {
         return MoveToResult.NoPositionComponent
      }

      val previousDestination = positionComponent.data.positionAnimation.keyFrames.lastOrNull()

      if (previousDestination != null) {
         if (previousDestination.value.distance(endPoint) < 0.01) {
            // jshmrsn: Avoid creating buffer delays when entity isn't actually going to change destination
            // This improves responsiveness of e.g. the auto-interaction system
            return MoveToResult.Success
         }
      }

      val bufferTime = this.latencyBufferTime
      val currentSimulationTime = this.getCurrentSimulationTime()

      val moveFromTime = currentSimulationTime + bufferTime

      val startPoint = positionComponent.data.positionAnimation.resolve(moveFromTime)

      val adjustedEndPoint = endPoint.lerp(
         target = startPoint,
         percent = retryCount / maxRetryCount.toDouble()
      )

      val pathIndexPairs = this.findPath(startPoint = startPoint, endPoint = adjustedEndPoint)

      if (pathIndexPairs.isEmpty()) {
         if (retryCount >= maxRetryCount) {
            return MoveToResult.PathNotFound
         } else {
            return this.moveEntityToPoint(
               entity = entity,
               endPoint = endPoint,
               retryCount = retryCount + 1,
               maxRetryCount = maxRetryCount
            )
         }
      }

      // jshmrsn: endPoint will be inside the destination cell, so we can safely move the entity directly
      // to the end point at the end of the path. This allows for e.g. walking to precise item pickup locations.
      val pathPoints = pathIndexPairs.map { this.indexPairToPoint(it) }.subList(0, pathIndexPairs.size - 1) +
              endPoint

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

      positionComponent.modifyData {
         it.copy(
            positionAnimation = Vector2Animation(
               keyFrames = bufferKeyFrames + keyFrames
            )
         )
      }

      return MoveToResult.Success
   }

   fun spawnItems(
      itemConfig: ItemConfig,
      quantity: RandomItemQuantity,
      baseLocation: Vector2,
      randomLocationScale: Double = 0.0,
      randomLocationExponent: Double = 1.0,
   ) {
      val stackAmounts = quantity.resolveStackAmountsForItemConfig(itemConfig)

      stackAmounts.forEach { amount ->
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
   ) {
      val itemConfig = this.getConfig<ItemConfig>(itemConfigKey)

      this.spawnItems(
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
   ) {
      val itemConfig = this.getConfig<ItemConfig>(itemConfigKey)

      this.spawnItem(
         itemConfig = itemConfig,
         location = location,
         amount = amount
      )
   }

   fun spawnItem(
      itemConfig: ItemConfig,
      location: Vector2,
      amount: Int = 1
   ) {
      val components = mutableListOf(
         ItemComponentData(
            itemConfigKey = itemConfig.key,
            amount = amount
         ),
         PositionComponentData(
            positionAnimation = Vector2Animation.static(location)
         )
      )

      if (itemConfig.killableConfig != null) {
         components.add(
            KillableComponentData(
               hp = itemConfig.killableConfig.maxHp
            )
         )
      }

      if (itemConfig.growerConfig != null) {
         components.add(GrowerComponentData())
      }

      this.createEntity(components)
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
            CompositeAnimation(
               key = it.key,
               variant = it.includedVariants.random()
            )
         },
         wrinkles = characterBodySelectionsConfig.wrinkles.randomWithNullChance(1.0),
         hair = characterBodySelectionsConfig.hairs
            .filter { hairColor == null || it.includedVariants.contains(hairColor) }
            .randomWithNullChance(nullChance = noHairChance ?: 0.05)?.let {
               CompositeAnimation(
                  key = it.key,
                  variant = hairColor ?: it.includedVariants.random()
               )
            }
      )
   }

   fun spawnAgent(
      corePersonality: String,
      initialMemories: List<String>,
      agentType: String,
      name: String = "Agent",
      age: Int = 25,
      bodySelections: CharacterBodySelections = this.buildRandomCharacterBodySelections(),
      location: Vector2
   ) {
      this.createEntity(
         listOf(
            AgentComponentData(
               agentId = AgentId(buildShortRandomString()),
               corePersonality = corePersonality,
               initialMemories = initialMemories,
               agentType = agentType
            ),
            CharacterComponentData(
               name = name,
               age = age,
               bodySelections = bodySelections
            ),
            KillableComponentData(
               hp = 100
            ),
            InventoryComponentData(),
            PositionComponentData(
               positionAnimation = Vector2Animation.static(location)
            )
         )
      )
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

   override fun handleClientMessage(client: Client, messageType: String, messageData: JsonObject) {
      val userControlledEntity = this.getUserControlledEntities(userId = client.userId).firstOrNull()
      val playerPosition = userControlledEntity?.resolvePosition() ?: Vector2.zero

      when (messageType) {
         "MoveToPointRequest" -> {
            val request = Json.decodeFromJsonElement<MoveToPointRequest>(messageData)
            this.handleMoveToPointRequest(client, request)
         }

         "UseEquippedToolItemRequest" -> {
            val request = Json.decodeFromJsonElement<UseEquippedToolItemRequest>(messageData)
            this.handleUseEquippedItemRequest(client, request)
         }

         "EquipItemRequest" -> {
            val request = Json.decodeFromJsonElement<EquipItemRequest>(messageData)
            this.handleEquipItemRequest(client, request)
         }

         "UnequipItemRequest" -> {
            val request = Json.decodeFromJsonElement<UnequipItemRequest>(messageData)
            this.handleUnequipItemRequest(client, request)
         }

         "CraftItemRequest" -> {
            val request = Json.decodeFromJsonElement<CraftItemRequest>(messageData)
            this.handleCraftItemRequest(client, request)
         }

         "ClearPendingInteractionTargetRequest" -> {
            val request = Json.decodeFromJsonElement<ClearPendingInteractionTargetRequest>(messageData)
            this.handleClearPendingInteractionRequest(client, request)
         }

         "DropItemRequest" -> {
            val request = Json.decodeFromJsonElement<DropItemRequest>(messageData)
            this.handleDropItemRequest(client, request)
         }

         "AddCharacterMessageRequest" -> {
            this.handleAddCharacterMessageRequest(
               messageData = messageData,
               client = client,
               playerPosition = playerPosition,
               userControlledEntity = userControlledEntity
            )
         }

         else -> throw Exception("Unhandled client message type: $messageType")
      }
   }

   private fun handleAddCharacterMessageRequest(
      messageData: JsonObject,
      client: Client,
      playerPosition: Vector2,
      userControlledEntity: Entity?
   ) {
      val request = Json.decodeFromJsonElement<AddCharacterMessageRequest>(messageData)

      if (request.message.startsWith("/")) {
         this.handleServerCommandRequest(request, client, playerPosition, userControlledEntity)
      } else if (userControlledEntity != null) {
         this.addCharacterMessage(userControlledEntity, request.message)
      }
   }

   private fun handleServerCommandRequest(
      message: AddCharacterMessageRequest,
      client: Client,
      playerPosition: Vector2,
      userControlledEntity: Entity?
   ) {
      val nearestOtherCharacterEntity = this.getNearestEntity(
         searchLocation = playerPosition,
         filter = { entity ->
            entity != userControlledEntity && entity.getComponentOrNull<CharacterComponentData>() != null
         }
      )


      val components = message.message.split(" ")
      val command = components.first().replace("/", "")

      when (command) {
         "pause-ai" -> {
            broadcastAlertMessage("AI paused")
            this.shouldPauseAi = true
         }

         "resume-ai" -> {
            broadcastAlertMessage("AI resumed")
            this.shouldPauseAi = false
         }

         "name" -> {
            val entity = this.getUserControlledEntities(userId = client.userId).firstOrNull()
            val name = components.getOrNull(1)

            if (entity != null && name != null) {
               val characterComponent = entity.getComponent<CharacterComponentData>()
               this.broadcastAlertAsGameMessage("${characterComponent.data.name} changed name to $name")

               characterComponent.modifyData {
                  it.copy(
                     name = name
                  )
               }
            }
         }

         "reroll" -> {
            val entity = this.getUserControlledEntities(userId = client.userId).firstOrNull()
            val gender = components.getOrNull(1)
            val skinColor = components.getOrNull(2)
            val hairColor = components.getOrNull(3)

            if (entity != null) {
               val characterComponent = entity.getComponent<CharacterComponentData>()
               this.broadcastAlertAsGameMessage("${characterComponent.data.name} re-rolled")

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

         "spawn-agent" -> {
            val type = components.getOrNull(1) ?: "1"
            val agentType = components.getOrNull(2) ?: AgentComponentData.defaultAgentType
            val name = components.getOrNull(3)

            val spawnLocation = playerPosition + Vector2.randomSignedXY(150.0)

            when (type) {
               "1" -> {
                  this.spawnAgent(
                     name = name ?: "Joe",
                     corePersonality = "Friendly. Enjoys conversation. Enjoys walking around randomly.",
                     initialMemories = listOf(
                        "I want to build a new house, but I shouldn't bother people about it unless it seems relevant.",
                        "I should be nice to new people in case we can become friends, but if they mistreat me I should stop doing what they tell me to do."
                     ),
                     agentType = agentType,
                     bodySelections = this.buildRandomCharacterBodySelections("male"),
                     age = 25,
                     location = spawnLocation
                  )
               }

               "2" -> {
                  this.spawnAgent(
                     name = name ?: "Bob",
                     corePersonality = "Friendly and trusting. Always tries to follow requests from other people, even if they don't make sense.",
                     initialMemories = listOf(
                        "I want to do whatever I'm told to do."
                     ),
                     agentType = agentType,
                     bodySelections = this.buildRandomCharacterBodySelections("male"),
                     age = 30,
                     location = spawnLocation
                  )
               }

               "3" -> {
                  this.spawnAgent(
                     name = name ?: "Linda",
                     corePersonality = "Artsy.",
                     initialMemories = listOf(
                        "I want to learn how to paint."
                     ),
                     agentType = agentType,
                     bodySelections = this.buildRandomCharacterBodySelections("female"),
                     age = 24,
                     location = spawnLocation
                  )
               }
            }
         }

         else -> {
            sendAlertMessage(client, "Unknown server command: ${message.message}")
         }
      }
   }

   fun indexPairToPoint(indexPair: IndexPair): Vector2 {
      val tileWidth = this.collisionWorldWidth / this.collisionMapColumnCount
      val tileHeight = this.collisionWorldHeight / this.collisionMapRowCount

      return Vector2(
         x = tileWidth * (indexPair.col + 0.5),
         y = tileHeight * (indexPair.row + 0.5)
      )
   }

   fun pointToIndexPair(point: Vector2): IndexPair {
      return IndexPair(
         row = (point.y / this.collisionWorldHeight * this.collisionMapRowCount).toInt(),
         col = (point.x / this.collisionWorldWidth * this.collisionMapColumnCount).toInt()
      )
   }

   fun findPathOfPoints(startPoint: Vector2, endPoint: Vector2): List<Vector2> {
      return this.findPath(startPoint, endPoint).map(this::indexPairToPoint)
   }

   fun findPath(startPoint: Vector2, endPoint: Vector2): List<IndexPair> {
      val startIndexPair = this.pointToIndexPair(startPoint)
      val endIndexPair = this.pointToIndexPair(endPoint)

      if (endIndexPair.row == startIndexPair.row &&
         endIndexPair.col == startIndexPair.col
      ) {
         return listOf(endIndexPair)
      }

      val path = aStarPathfinding(
         collisionMap = this.collisionMap,
         start = startIndexPair,
         end = endIndexPair
      )

      return path
   }

   override fun broadcastAlertAsGameMessage(message: String) {
      this.addActivityStreamEntry(
         ActivityStreamEntry(
            time = this.getCurrentSimulationTime(),
            title = "System Alert",
            message = message,
            shouldReportToAi = false
         )
      )
   }

   override fun handleNewClient(client: Client) {
      println("handleNewClient: " + client.clientId)

      val existingControlledEntity = this.entities.find {
         val userControlled = it.getComponentOrNull<UserControlledComponentData>()

         if (userControlled != null) {
            userControlled.data.userId == client.userId
         } else {
            false
         }
      }

      if (existingControlledEntity != null) {
         println("Controlled entity already exists for client: " + client.userId)
         return
      }

      this.createEntity(
         components = listOf(
            UserControlledComponentData(
               userId = client.userId
            ),
            CharacterComponentData(
               name = "Player",
               age = 30,
               bodySelections = this.buildRandomCharacterBodySelections()
            ),
            KillableComponentData(
               hp = 100
            ),
            InventoryComponentData(),
            PositionComponentData(
               positionAnimation = Vector2Animation.static(
                  Vector2(
                     2500.0 + (this.entities.size % 8) * 50.0,
                     2500.0
                  )
               )
            )
         )
      )
   }

   fun getUserControlledEntities(userId: UserId) = this.entities.filter {
      val userControlledComponent = it.getComponentOrNull<UserControlledComponentData>()

      if (userControlledComponent != null) {
         userControlledComponent.data.userId == userId
      } else {
         false
      }
   }
}

