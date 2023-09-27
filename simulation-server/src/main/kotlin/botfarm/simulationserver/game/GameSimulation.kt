package botfarm.simulationserver.game

import botfarm.misc.*
import botfarm.simulationserver.game.ai.AgentServerIntegration
import botfarm.simulationserver.common.*
import botfarm.simulationserver.simulation.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.random.Random

@Serializable
class MoveToPointMessage(
   val point: Vector2,
   val pendingInteractionEntityId: String? = null
)

@Serializable
class EquipItemMessage(
   val itemConfigKey: String?
)

@Serializable
class CraftItemMessage(
   val itemConfigKey: String
)

@Serializable
class ClearPendingInteractionTargetMessage()


@Serializable
class PickUpItemMessage(
   val targetEntityId: String
)

@Serializable
class DropItemMessage(
   val itemConfigKey: String,
   val amount: Int? = null
)

@Serializable
class AddCharacterMessageMessage(
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

      val tilewidth = 32 // tilemap.tilewidth
      val tileheight = 32 // tilemap.tilewidth
      this.collisionMap = collisionMap
      this.collisionMapRowCount = rowCount
      this.collisionMapColumnCount = columnCount
      this.collisionWorldWidth = columnCount * tilewidth.toDouble()
      this.collisionWorldHeight = rowCount * tileheight.toDouble()
   }

   fun getActivityStreamEntity() =
      this.getEntityOrNull("activity-stream") ?: throw Exception("activity-stream entity not found")

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

   fun addCharacterMessage(client: Client, message: AddCharacterMessageMessage) {
      val entity = this.getUserControlledEntities(userId = client.userId).firstOrNull()

      if (entity == null) {
         sendAlertMessage(client, "No controlled entity")
         return
      }

      this.addCharacterMessage(
         entity = entity,
         message = message.message
      )
   }

   private fun handleClearPendingInteractionMessage(client: Client, message: ClearPendingInteractionTargetMessage) {
      val entity = this.getUserControlledEntities(userId = client.userId).firstOrNull()

      if (entity == null) {
         sendAlertMessage(client, "No controlled entity")
         return
      }

      val characterComponent = entity.getComponent<CharacterComponentData>()
      characterComponent.modifyData {
         it.copy(
            pendingInteractionTargetEntityId = null
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

      val craftingCost = itemConfig.craftingCost

      if (craftingCost == null) {
         return CraftItemResult.ItemCannotBeCrafted
      }

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

      if (itemConfig.canBePickedUp) {
         entity.giveInventoryItem(
            itemConfigKey = itemConfigKey,
            amount = itemConfig.craftingAmount
         )
      } else {
         this.spawnItems(
            itemConfigKey = itemConfigKey,
            baseLocation = entityPosition,
            minAmountPerStack = itemConfig.craftingAmount,
            maxAmountPerStack = itemConfig.craftingAmount,
            randomLocationScale = 30.0
         )
      }

      return CraftItemResult.Success
   }


   enum class EquipItemResult {
      ItemCannotBeEquipped,
      ItemNotInInventory,
      Success
   }

   fun equipItem(
      entity: Entity,
      itemConfigKey: String?
   ): EquipItemResult {
      val characterComponent = entity.getComponent<CharacterComponentData>()

      if (itemConfigKey != null) {
         val itemConfig = this.getConfig<ItemConfig>(itemConfigKey)

         if (!itemConfig.canBeEquipped) {
            return EquipItemResult.ItemCannotBeEquipped
         }

         val amount = entity.getInventoryItemTotalAmount(itemConfigKey)

         if (amount <= 0) {
            return EquipItemResult.ItemNotInInventory
         }


         val name = characterComponent.data.name


         this.addActivityStreamEntry(
            ActivityStreamEntry(
               time = this.getCurrentSimulationTime(),
               sourceIconPath = null,
               title = "$name equipped a ${itemConfig.name}",
               sourceLocation = entity.resolvePosition(),
               targetIconPath = itemConfig.iconUrl,
               actionType = "equipItem"
            )
         )
      }

      characterComponent.modifyData {
         it.copy(
            equippedItemConfigKey = itemConfigKey
         )
      }


      return EquipItemResult.Success
   }

   private fun handleCraftItemMessage(client: Client, message: CraftItemMessage) {
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

   private fun handleEquipItemMessage(client: Client, message: EquipItemMessage) {
      val entity = this.getUserControlledEntities(userId = client.userId).firstOrNull()

      if (entity == null) {
         sendAlertMessage(client, "No controlled entity")
         return
      }

      this.equipItem(
         entity = entity,
         itemConfigKey = message.itemConfigKey
      )
   }

   private fun handleMoveToPointMessage(client: Client, message: MoveToPointMessage) {
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
      characterComponent.modifyData {
         it.copy(
            pendingInteractionTargetEntityId = null
         )
      }

      if (result != MoveToResult.Success) {
         sendAlertMessage(client, "Move to point failed: " + result.name)
      } else {
         if (message.pendingInteractionEntityId != null) {
            characterComponent.modifyData {
               it.copy(
                  pendingInteractionTargetEntityId = message.pendingInteractionEntityId
               )
            }
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

   private fun handleDropItemMessage(client: Client, message: DropItemMessage) {
      val entity = this.getUserControlledEntities(userId = client.userId).firstOrNull()

      if (entity == null) {
         sendAlertMessage(client, "No controlled entity")
         return
      }

      val didDrop = this.dropItem(
         droppingEntity = entity,
         itemConfigKey = message.itemConfigKey,
         amount = message.amount
      )

      if (!didDrop) {
         sendAlertMessage(client, "Not enough to drop")
      }
   }

   fun dropItem(
      droppingEntity: Entity,
      itemConfigKey: String,
      amount: Int?
   ): Boolean {
      val itemConfig = this.getConfig<ItemConfig>(itemConfigKey)

      if (!itemConfig.canBeDropped) {
         throw Exception("Item cannot be dropped")
      }

      val currentAmount = droppingEntity.getInventoryItemTotalAmount(
         itemConfigKey = itemConfigKey
      )

      if (currentAmount <= 0) {
         return false
      }

      val resolvedRequestedAmount = if (itemConfig.maxStackSize != null) {
         if (amount != null) {
            Math.min(amount, itemConfig.maxStackSize)
         } else {
            itemConfig.maxStackSize
         }
      } else {
         amount ?: currentAmount
      }

      val resolvedAmount = Math.min(
         resolvedRequestedAmount,
         currentAmount
      )

      val didTake = droppingEntity.takeInventoryItem(
         itemConfigKey = itemConfigKey,
         amount = resolvedAmount
      )

      if (!didTake) {
         return false
      }

      this.spawnItem(
         itemConfigKey = itemConfigKey,
         amount = resolvedAmount,
         location = droppingEntity.resolvePosition() + Vector2.randomSignedXY(50.0)
      )


      val amountAfterDrop = droppingEntity.getInventoryItemTotalAmount(
         itemConfigKey = itemConfigKey
      )

      val character = droppingEntity.getComponentOrNull<CharacterComponentData>()

      if (amountAfterDrop <= 0 &&
         character != null &&
         character.data.equippedItemConfigKey == itemConfigKey
      ) {
         this.equipItem(entity = droppingEntity, itemConfigKey = null)
      }

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

   enum class InteractWithEquippedItemResult {
      Success,
      NoItemEquipped,
      EquippedItemCannotDamageTarget,
      TooFar
   }

   fun interactWithEquippedItem(
      interactingEntity: Entity,
      targetEntity: Entity
   ): InteractWithEquippedItemResult {
      val characterComponentData = interactingEntity.getComponent<CharacterComponentData>().data

      val equippedItemId = characterComponentData.equippedItemConfigKey
      val equippedItemConfig = equippedItemId?.let { this.getConfig<ItemConfig>(it) }

      if (equippedItemId == null) {
         return InteractWithEquippedItemResult.NoItemEquipped
      }

      val itemComponent = targetEntity.getComponent<ItemComponentData>()
      val itemConfig = this.getConfig<ItemConfig>(itemComponent.data.itemConfigKey)

      val maxDistance = 50.0

      val interactingEntityPosition = interactingEntity.resolvePosition()
      val targetEntityPosition = targetEntity.resolvePosition()

      val distance = interactingEntityPosition.distance(targetEntityPosition)

      if (distance > maxDistance) {
         return InteractWithEquippedItemResult.TooFar
      }

      if (itemConfig.canBeDamagedByItem != equippedItemId) {
         return InteractWithEquippedItemResult.EquippedItemCannotDamageTarget
      }

      val name = characterComponentData.name


      if (equippedItemConfig != null) {
         this.addActivityStreamEntry(
            ActivityStreamEntry(
               time = this.getCurrentSimulationTime(),
               title = "$name harvested a ${itemConfig.name} using a ${equippedItemConfig.name}",
               sourceIconPath = null,
               sourceLocation = interactingEntityPosition,
               targetIconPath = itemConfig.iconUrl,
               actionIconPath = equippedItemConfig.iconUrl,
               actionType = "harvest"
            )
         )
      } else {
         this.addActivityStreamEntry(
            ActivityStreamEntry(
               time = this.getCurrentSimulationTime(),
               sourceIconPath = null,
               title = "$name harvested a ${itemConfig.name}",
               sourceLocation = interactingEntityPosition,
               targetIconPath = itemConfig.iconUrl,
               actionType = "harvest"
            )
         )
      }


      this.applyDamageToEntity(
         targetEntity = targetEntity,
         damage = 1000
      )

      return InteractWithEquippedItemResult.Success
   }

   fun applyDamageToEntity(
      targetEntity: Entity,
      damage: Int
   ) {
      if (damage < 0) {
         throw Exception("Damage cannot be negative")
      }

      val itemComponent = targetEntity.getComponent<ItemComponentData>()
      val itemConfig = this.getConfig<ItemConfig>(itemComponent.data.itemConfigKey)

      itemComponent.modifyData {
         it.copy(
            hp = Math.max(0, it.hp - damage)
         )
      }

      if (itemComponent.data.hp == 0) {
         if (itemConfig.spawnItemOnDestruction != null) {
            this.spawnItems(
               itemConfigKey = itemConfig.spawnItemOnDestruction,
               baseLocation = targetEntity.resolvePosition(),
               randomLocationScale = 50.0,
               minAmountPerStack = itemConfig.spawnMinAmountPerStack,
               maxAmountPerStack = itemConfig.spawnMaxAmountPerStack,
               minStacks = itemConfig.spawnMinStacks,
               maxStacks = itemConfig.spawnMaxStacks
            )
         }

         targetEntity.destroy()
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

      if (!itemConfig.canBePickedUp) {
         throw Exception("Item can't be picked up: " + itemConfig.key)
      }

      pickingUpEntity.giveInventoryItem(
         itemConfigKey = itemConfig.key,
         amount = itemComponent.data.amount
      )

      targetEntity.destroy()

      val characterComponentData = pickingUpEntity.getComponentOrNull<CharacterComponentData>()?.data

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
      itemConfigKey: String,
      baseLocation: Vector2,
      randomLocationScale: Double = 0.0,
      randomLocationExponent: Double = 1.0,
      minStacks: Int = 1,
      maxStacks: Int = 1,
      minAmountPerStack: Int = 1,
      maxAmountPerStack: Int = 1
   ) {
      val stackCount = Random.nextInt(minStacks, maxStacks + 1)

      for (i in 0..<stackCount) {
         val amount = Random.nextInt(minAmountPerStack, maxAmountPerStack + 1)

         this.spawnItem(
            itemConfigKey = itemConfigKey,
            amount = amount,
            location = baseLocation + Vector2.randomSignedXY(
               randomLocationScale,
               randomLocationScale,
               exponent = randomLocationExponent
            )
         )
      }
   }

   fun spawnItem(
      itemConfigKey: String,
      location: Vector2,
      amount: Int = 1
   ) {
      val itemConfig = this.getConfig<ItemConfig>(itemConfigKey)

      this.createEntity(
         listOf(
            ItemComponentData(
               hp = itemConfig.maxHp,
               itemConfigKey = itemConfig.key,
               amount = amount
            ),
            PositionComponentData(
               positionAnimation = Vector2Animation.static(location)
            )
         )
      )
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
         wrinkles = characterBodySelectionsConfig.wrinkles.randomWithNullChance(0.75),
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

   override fun handleClientMessage(client: Client, messageType: String, messageData: JsonObject) {
      val playerControlledEntity = this.getUserControlledEntities(userId = client.userId).firstOrNull()

      val playerPosition = playerControlledEntity?.resolvePosition() ?: Vector2.zero

      fun getRandomLocationRelativeToPlayer(scale: Double = 100.0): Vector2 {
         return playerPosition + Vector2.randomSignedXY(scale, scale)
      }

      val nearestOtherCharacterEntity = this.entities
         .mapNotNull { otherEntity ->
            val character = otherEntity.getComponentOrNull<CharacterComponentData>()

            if (character != null && otherEntity.entityId != playerControlledEntity?.entityId) {
               val position = otherEntity.resolvePosition()

               val distance = position.distance(playerPosition)

               Pair(otherEntity, distance)
            } else {
               null
            }
         }
         .sortedBy { it.second }
         .map { it.first }
         .firstOrNull()

      if (messageType == "MoveToPointMessage") {
         val message = Json.decodeFromJsonElement<MoveToPointMessage>(messageData)
         this.handleMoveToPointMessage(client, message)
      } else if (messageType == "EquipItemMessage") {
         val message = Json.decodeFromJsonElement<EquipItemMessage>(messageData)
         this.handleEquipItemMessage(client, message)
      } else if (messageType == "CraftItemMessage") {
         val message = Json.decodeFromJsonElement<CraftItemMessage>(messageData)
         this.handleCraftItemMessage(client, message)
      } else if (messageType == "ClearPendingInteractionTargetMessage") {
         val message = Json.decodeFromJsonElement<ClearPendingInteractionTargetMessage>(messageData)
         this.handleClearPendingInteractionMessage(client, message)
      } else if (messageType == "PickUpItemMessage") {
         val message = Json.decodeFromJsonElement<PickUpItemMessage>(messageData)
         this.handlePickUpItemMessage(client, message)
      } else if (messageType == "DropItemMessage") {
         val message = Json.decodeFromJsonElement<DropItemMessage>(messageData)
         this.handleDropItemMessage(client, message)
      } else if (messageType == "AddCharacterMessageMessage") {
         val message = Json.decodeFromJsonElement<AddCharacterMessageMessage>(messageData)

         if (message.message.startsWith("/")) {
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
                  val count = components.getOrNull(3)?.toInt() ?: 1

                  for (i in 0..(count - 1)) {
                     this.spawnItem(
                        itemConfigKey = configKey,
                        amount = amount,
                        location = getRandomLocationRelativeToPlayer(scale = 150.0)
                     )
                  }
               }

               "take-item" -> {
                  val configKey = components.getOrNull(1) ?: "stone"
                  val amountPerStack = components.getOrNull(2)?.toInt() ?: 1
                  val stacks = components.getOrNull(3)?.toInt() ?: 1

                  if (playerControlledEntity != null) {
                     for (i in 0..(stacks - 1)) {
                        val remaining = playerControlledEntity.getInventoryItemTotalAmount(
                           itemConfigKey = configKey
                        )

                        playerControlledEntity.takeInventoryItem(
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

                  if (playerControlledEntity != null) {
                     for (i in 0..(stacks - 1)) {
                        playerControlledEntity.giveInventoryItem(
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

                  val spawnLocation = getRandomLocationRelativeToPlayer(scale = 150.0)

                  when (type) {
                     "1" -> {
                        this.createEntity(
                           listOf(
                              AgentComponentData(
                                 agentId = buildShortRandomString(),
                                 corePersonality = "Friendly. Enjoys conversation. Enjoys walking around randomly.",
                                 initialMemories = listOf(
                                    "I want to build a new house, but I shouldn't bother people about it unless it seems relevant.",
                                    "I should be nice to new people in case we can become friends, but if they mistreat me I should stop doing what they tell me to do."
                                 ),
                                 agentType = agentType
                              ),
                              CharacterComponentData(
                                 name = name ?: "Joe",
                                 gender = "male",
                                 age = 25,
                                 bodySelections = this.buildRandomCharacterBodySelections("male")
                              ),
                              InventoryComponentData(),
                              PositionComponentData(
                                 positionAnimation = Vector2Animation.static(spawnLocation)
                              )
                           )
                        )
                     }

                     "2" -> {
                        this.createEntity(
                           listOf(
                              AgentComponentData(
                                 agentId = buildShortRandomString(),
                                 corePersonality = "Friendly and trusting. Always tries to follow requests from other people, even if they don't make sense.",
                                 initialMemories = listOf(
                                    "I want to do whatever I'm told to do."
                                 ),
                                 agentType = agentType
                              ),
                              CharacterComponentData(
                                 name = name ?: "Bob",
                                 gender = "male",
                                 age = 30,
                                 bodySelections = this.buildRandomCharacterBodySelections("male")
                              ),
                              InventoryComponentData(),
                              PositionComponentData(
                                 positionAnimation = Vector2Animation.static(spawnLocation)
                              )
                           )
                        )
                     }

                     "3" -> {
                        this.createEntity(
                           listOf(
                              AgentComponentData(
                                 agentId = buildShortRandomString(),
                                 corePersonality = "Artsy.",
                                 initialMemories = listOf(
                                    "I want to learn how to paint."
                                 ),
                                 agentType = agentType
                              ),
                              CharacterComponentData(
                                 name = name ?: "Linda",
                                 gender = "female",
                                 age = 24,
                                 bodySelections = this.buildRandomCharacterBodySelections("female")
                              ),
                              InventoryComponentData(),
                              PositionComponentData(
                                 positionAnimation = Vector2Animation.static(spawnLocation)
                              )
                           )
                        )
                     }

                     "4" -> {
                        this.createEntity(
                           listOf(
                              AgentComponentData(
                                 agentId = buildShortRandomString(),
                                 corePersonality = "Friendly. Enjoys conversation. Enjoys walking around randomly.",
                                 initialMemories = listOf(
                                    "I want to build a new house",
                                    "I should be nice to new people in case we can become friends, but if they mistreat me I should stop doing what they tell me to do."
                                 ),
                                 agentType = agentType
                              ),
                              CharacterComponentData(
                                 name = name ?: "Janet",
                                 gender = "female",
                                 age = 25,
                                 bodySelections = this.buildRandomCharacterBodySelections(
                                    bodyType = "female",
                                    skinColor = "amber"
                                 )
                              ),
                              InventoryComponentData(),
                              PositionComponentData(
                                 positionAnimation = Vector2Animation.static(spawnLocation)
                              )
                           )
                        )
                     }
                  }
               }

               else -> {
                  sendAlertMessage(client, "Unknown server command: ${message.message}")
               }
            }
         } else {
            this.addCharacterMessage(client, message)
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
               gender = "male",
               age = 30,
               bodySelections = this.buildRandomCharacterBodySelections()
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

   fun getUserControlledEntities(userId: String) = this.entities.filter {
      val userControlledComponent = it.getComponentOrNull<UserControlledComponentData>()

      if (userControlledComponent != null) {
         userControlledComponent.data.userId == userId
      } else {
         false
      }
   }
}

