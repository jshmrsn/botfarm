package botfarmagent.game.agents.codeexecution

import botfarmagent.game.agents.codeexecution.jsdata.*
import botfarmshared.engine.apidata.EntityId
import botfarmshared.game.apidata.*
import botfarmshared.misc.Vector2
import botfarmshared.misc.getCurrentUnixTimeSeconds
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.Value

class JsConversionContext(
   val bindings: Value
) {
   val convertJsArray = this.bindings.getMember("convertJsArray")
}


class AgentJavaScriptApi(
   val agent: CodeExecutionAgent
) {
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
      val inventory = this.agent.mostRecentSyncInput.selfInfo.inventoryInfo
      var total = 0
      inventory.itemStacks.forEach {
         if (it.itemConfigKey == itemTypeId) {
            total += it.amount
         }
      }
      return total
   }

   fun buildJsCraftingRecipe(
      craftingRecipe: CraftingRecipe,
      jsConversionContext: JsConversionContext? = null
   ): JsCraftingRecipe {
      var canCurrentlyAfford = true

      craftingRecipe.cost.entries.forEach {
         val currentAmount = this.getTotalInventoryAmountForItemTypeId(itemTypeId = it.itemConfigKey)
         if (currentAmount < it.amount) {
            canCurrentlyAfford = false
         }
      }

      return JsCraftingRecipe(
         api = this,
         craftingRecipe = craftingRecipe,
         canCurrentlyAfford = canCurrentlyAfford,
         jsConversionContext = jsConversionContext
      )
   }

   @HostAccess.Export
   fun getAllCraftingRecipes(): JsArray<JsCraftingRecipe> {
      this.endIfRequested()

      return this.agent.mostRecentSyncInput.gameSimulationInfo.craftingRecipes.map {
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
      val inventory = this.agent.mostRecentSyncInput.selfInfo.inventoryInfo

      return inventory.itemStacks.mapIndexed { stackIndex, itemStackInfo ->
         JsInventoryItemStackInfo(
            api = this,
            stackIndex = stackIndex,
            itemStackInfo = itemStackInfo
         )
      }.toJs()
   }

   @HostAccess.Export
   fun getCurrentNearbyEntities(): JsArray<JsEntity> {
      this.endIfRequested()
      return this.agent.mostRecentSyncInput.newObservations.entitiesById.values.map { entityInfo ->
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

      println("Added action: ($actionUniqueId)\n${actionJsonString}")

      this.agent.addPendingOutput(
         AgentSyncOutput(
            actions = listOf(action),
            agentStatus = "waiting-for-action",
            statusStartUnixTime = getCurrentUnixTimeSeconds()
         )
      )
      
      run {
         var waitingCounter = 0
         while (true) {
            ++waitingCounter
            this.sleep(250)

            val actionHasStarted = this.agent.receivedActionStartedIds.contains(actionUniqueId)

            if (!actionHasStarted) {
               println("Action has not yet started, waiting... $actionUniqueId ($waitingCounter)")
               continue
            }

            val actionResult = this.agent.receivedActionResultById[actionUniqueId]

            if (actionResult != null) {
               this.agent.addPendingOutput(
                  AgentSyncOutput(
                     agentStatus = "action-done",
                     statusStartUnixTime = getCurrentUnixTimeSeconds()
                  )
               )

               return actionResult
            } else {
               println("Action has not yet completed, waiting... $actionUniqueId ($waitingCounter)")
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
      this.agent.recordThought(thought)
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

