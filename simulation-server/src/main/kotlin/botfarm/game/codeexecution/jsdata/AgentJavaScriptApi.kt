package botfarm.game.codeexecution.jsdata

import botfarm.common.PositionComponentData
import botfarm.common.resolvePosition
import botfarm.game.agentintegration.AgentSyncState
import botfarm.game.agentintegration.buildEntityInfoForAgent
import botfarm.game.codeexecution.UnwindScriptThreadThrowable
import botfarm.game.components.AgentControlledComponentData
import botfarm.game.components.CharacterComponentData
import botfarm.game.components.InventoryComponentData
import botfarm.game.config.ItemConfig
import botfarmshared.engine.apidata.EntityId
import botfarmshared.game.apidata.*
import botfarmshared.misc.Vector2
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.graalvm.polyglot.HostAccess

class AgentJavaScriptApi(
   val agentSyncState: AgentSyncState
) {
   val simulation = this.agentSyncState.simulation
   val entity = this.agentSyncState.entity
   val inventoryComponent = this.entity.getComponent<InventoryComponentData>()
   val characterComponent = this.entity.getComponent<CharacterComponentData>()
   val agentControlledComponent = this.entity.getComponent<AgentControlledComponentData>()

   var shouldEndScript = false

   fun endIfRequested() {
      if (this.shouldEndScript) {
         throw UnwindScriptThreadThrowable()
      }
   }

   @HostAccess.Export
   fun speak(message: String) {
      this.endIfRequested()
      this.performActionAndWaitForResult(
         Action(
            speak = message
         )
      )
      this.sleep(1000)
   }

   @HostAccess.Export
   fun makeVector2(x: Double, y: Double): JsVector2 {
      this.endIfRequested()
      return JsVector2(Vector2(x, y))
   }

   fun buildJsInventoryItemStackInfo(
      itemStackInfo: ItemStackInfo,
      stackIndex: Int
   ): JsInventoryItemStackInfo {
      this.endIfRequested()
      return JsInventoryItemStackInfo(
         api = this,
         stackIndex = stackIndex,
         itemStackInfo = itemStackInfo
      )
   }

   @HostAccess.Export
   fun getTotalInventoryAmountForItemTypeId(itemTypeId: String): Int {
      this.endIfRequested()
      val inventory = this.inventoryComponent.data.inventory
      var total = 0
      inventory.itemStacks.forEach {
         if (it.itemConfigKey == itemTypeId) {
            total += it.amount
         }
      }
      return total
   }

   fun buildJsCraftingRecipe(
      craftingRecipeInfo: CraftingRecipeInfo,
      jsConversionContext: JsConversionContext? = null
   ): JsCraftingRecipe {
      return JsCraftingRecipe(
         api = this,
         craftingRecipeInfo = craftingRecipeInfo,
         jsConversionContext = jsConversionContext
      )
   }

   @HostAccess.Export
   fun getAllCraftingRecipes(): JsArray<JsCraftingRecipe> {
      this.endIfRequested()

      val craftingRecipeInfos = this.agentSyncState.simulation.getCraftingRecipeInfos(
         crafterEntity = this.entity
      )

      return craftingRecipeInfos.map {
         this.buildJsCraftingRecipe(it)
      }.toJs()
   }

   fun buildJsEntity(entityInfo: EntityInfo): JsEntity {
      return JsEntity(
         api = this,
         entityInfo = entityInfo
      )
   }

   @HostAccess.Export
   fun getCurrentInventoryItemStacks(): JsArray<JsInventoryItemStackInfo> {
      this.endIfRequested()

      synchronized(this.simulation) {
         this.endIfRequested()

         val inventory = this.inventoryComponent.data.inventory

         return inventory.itemStacks.mapIndexed { stackIndex, itemStack ->
            val itemConfig = this.simulation.getConfig<ItemConfig>(itemStack.itemConfigKey)

            JsInventoryItemStackInfo(
               api = this,
               stackIndex = stackIndex,
               itemStackInfo = itemStack.buildInfo(
                  itemConfig = itemConfig
               )
            )
         }.toJs()
      }
   }

   @HostAccess.Export
   fun getSelfEntity(): JsEntity {
      val entityInfo = buildEntityInfoForAgent(this.entity, this.simulation.simulationTime)
      return this.buildJsEntity(entityInfo)
   }

   @HostAccess.Export
   fun getCurrentNearbyEntities(): JsArray<JsEntity> {
      this.endIfRequested()
      val selfPosition = this.entity.resolvePosition()
      val observationDistance = this.agentControlledComponent.data.observationDistance

      val observedEntities = this.simulation.entities.filter {
         if (it.getComponentOrNull<PositionComponentData>() != null &&
            it.entityId != this.entity.entityId) {
            val distance = it.resolvePosition().distance(selfPosition)
            distance <= observationDistance
         } else {
            false
         }
      }

      return observedEntities.map { observedEntity ->
         val entityInfo = buildEntityInfoForAgent(observedEntity, this.simulation.simulationTime)
         this.buildJsEntity(entityInfo)
      }.toList().toJs()
   }

   @HostAccess.Export
   fun sleep(millis: Long) {
      this.endIfRequested()
      Thread.sleep(millis)
      this.endIfRequested()
   }

   fun performActionAndWaitForResult(action: Action): ActionResult {
      this.endIfRequested()

      val actionUniqueId = action.actionUniqueId

      val json = Json {
         prettyPrint = true
      }

      val actionJsonString = json.encodeToString(action)

      println("AgentJavaScriptApi: Added action: ($actionUniqueId)\n${actionJsonString}")

      synchronized(simulation) {
         this.agentSyncState.pendingActions.add(action)
      }

      run {
         var waitingCounter = 0
         while (true) {
            ++waitingCounter
            this.sleep(200)

            synchronized(this.simulation) {
               val actionHasStarted = this.agentSyncState.startedActionUniqueIds.contains(actionUniqueId)

               if (!actionHasStarted) {
                  println("AgentJavaScriptApi: Action has not yet started, waiting... $actionUniqueId ($waitingCounter)")
               } else {
                  val actionResult = this.agentSyncState.actionResultsByActionUniqueId[actionUniqueId]

                  if (actionResult != null) {
                     println("AgentJavaScriptApi: Got action result $actionUniqueId ($waitingCounter)")
                     return actionResult
                  } else {
                     println("AgentJavaScriptApi: Action has not yet completed, waiting... $actionUniqueId ($waitingCounter)")
                  }
               }
            }
         }
      }
   }

   @HostAccess.Export
   fun pickUpItem(entityId: String) {
      this.pickUpItem(
         entityId = entityId,
         reason = null
      )
   }

   @HostAccess.Export
   fun pickUpItem(entityId: String, reason: String?) {
      this.performActionAndWaitForResult(
         Action(
            reason = reason,
            pickUpEntity = ActionOnEntity(
               targetEntityId = EntityId(entityId)
            )
         )
      )
   }


   @HostAccess.Export
   fun interactWithEntity(entityId: String) {
      this.interactWithEntity(
         entityId = entityId,
         reason = null
      )
   }

   @HostAccess.Export
   fun interactWithEntity(entityId: String, reason: String?) {
      this.performActionAndWaitForResult(
         Action(
            reason = reason,
            useEquippedToolItemOnEntity = ActionOnEntity(
               targetEntityId = EntityId(entityId)
            )
         )
      )
   }

   @HostAccess.Export
   fun craftItem(
      itemConfigKey: String
   ) {
      this.craftItem(itemConfigKey, null)
   }

   @HostAccess.Export
   fun craftItem(
      itemConfigKey: String,
      reason: String?
   ) {
      this.performActionAndWaitForResult(
         Action(
            reason = reason,
            craftItem = CraftItemAction(
               itemConfigKey = itemConfigKey
            )
         )
      )
   }

   @HostAccess.Export
   fun equipItem(
      itemConfigKey: String,
      stackIndex: Int?
   ) {
      this.equipItem(
         itemConfigKey = itemConfigKey,
         stackIndex = stackIndex,
         reason = null
      )
   }

   @HostAccess.Export
   fun equipItem(
      itemConfigKey: String,
      stackIndex: Int?,
      reason: String?
   ) {
      this.performActionAndWaitForResult(
         Action(
            reason = reason,
            equipInventoryItem = ActionOnInventoryItem(
               itemConfigKey = itemConfigKey,
               stackIndex = stackIndex
            )
         )
      )
   }

   @HostAccess.Export
   fun useEquippedToolItem() {
      this.useEquippedToolItem(
         reason = null
      )
   }

   @HostAccess.Export
   fun useEquippedToolItem(
      reason: String?
   ) {
      this.performActionAndWaitForResult(
         Action(
            useEquippedToolItem = UseEquippedToolItem(
               reason = reason
            )
         )
      )
   }

   @HostAccess.Export
   fun dropItem(
      itemConfigKey: String,
      stackIndex: Int?,
      amount: Int?
   ) {
      this.dropItem(
         itemConfigKey = itemConfigKey,
         stackIndex = stackIndex,
         amount = amount,
         reason = null
      )
   }

   @HostAccess.Export
   fun dropItem(
      itemConfigKey: String,
      stackIndex: Int?,
      amount: Int?,
      reason: String?
   ) {
      this.performActionAndWaitForResult(
         Action(
            reason = reason,
            dropInventoryItem = ActionOnInventoryItem(
               itemConfigKey = itemConfigKey,
               stackIndex = stackIndex,
               amount = amount
            )
         )
      )
   }

   @HostAccess.Export
   fun recordThought(
      thought: String
   ) {
      this.performActionAndWaitForResult(
         Action(
         recordThought = thought
      )
      )
   }

   @HostAccess.Export
   fun walkTo(
      destination: JsVector2
   ) {
      this.walkTo(destination, null)
   }

   @HostAccess.Export
   fun walkTo(
      destination: JsVector2,
      reason: String?
   ) {
      this.performActionAndWaitForResult(
         Action(
            reason = reason,
            walk = WalkAction(
               location = destination.asVector2
            )
         )
      )
   }

   @HostAccess.Export
   fun setFacialExpressionEmoji(
      emoji: String
   ) {
      this.performActionAndWaitForResult(
         Action(
            facialExpressionEmoji = emoji
         )
      )
   }
}

