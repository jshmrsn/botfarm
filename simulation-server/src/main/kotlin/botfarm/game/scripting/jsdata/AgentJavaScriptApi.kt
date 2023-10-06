package botfarm.game.scripting.jsdata

import botfarm.common.PositionComponentData
import botfarm.common.resolvePosition
import botfarm.game.agentintegration.AgentIntegration
import botfarm.game.agentintegration.buildEntityInfoForAgent
import botfarm.game.scripting.UnwindScriptThreadThrowable
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
   val agentIntegration: AgentIntegration
) {
   val simulation = this.agentIntegration.simulation
   val entity = this.agentIntegration.entity
   val inventoryComponent = this.entity.getComponent<InventoryComponentData>()
   val characterComponent = this.entity.getComponent<CharacterComponentData>()
   val agentControlledComponent = this.entity.getComponent<AgentControlledComponentData>()

   var shouldEndScript = false

   private var javaScriptThread: Thread? = null

   fun notifyJavaScriptThreadStarted() {
      this.javaScriptThread = Thread.currentThread()
   }

   private fun validateInJavaScriptThread() {
      if (this.javaScriptThread == null) {
         throw Exception("AgentJavaScriptApi.validateInJavaScriptThread: javaScriptThread hasn't been set")
      } else if (Thread.currentThread() != this.javaScriptThread) {
         throw Exception("AgentJavaScriptApi.validateInJavaScriptThread: Current thread is not javaScriptThread")
      }
   }

   fun endIfRequested() {
      this.validateInJavaScriptThread()

      if (this.shouldEndScript) {
         throw UnwindScriptThreadThrowable()
      }
   }

   @HostAccess.Export
   fun speak(message: String) {
      this.endIfRequested()
      this.addPendingActionAndWaitForResult(
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

      val craftingRecipeInfos = this.waitForSimulationRequestResult {
         this.simulation.getCraftingRecipeInfos(
            crafterEntity = this.entity
         )
      }

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

      return this.waitForSimulationRequestResult {
         val inventory = this.inventoryComponent.data.inventory

         inventory.itemStacks.mapIndexed { stackIndex, itemStack ->
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
      return this.waitForSimulationRequestResult {
         val entityInfo = buildEntityInfoForAgent(this.entity, this.simulation.simulationTime)
         this.buildJsEntity(entityInfo)
      }
   }

   @HostAccess.Export
   fun getCurrentNearbyEntities(): JsArray<JsEntity> {
      return this.waitForSimulationRequestResult {
         val selfPosition = this.entity.resolvePosition()
         val observationDistance = this.agentControlledComponent.data.observationDistance

         val observedEntities = this.simulation.entities.filter {
            if (it.getComponentOrNull<PositionComponentData>() != null &&
               it.entityId != this.entity.entityId
            ) {
               val distance = it.resolvePosition().distance(selfPosition)
               distance <= observationDistance
            } else {
               false
            }
         }

         observedEntities.map { observedEntity ->
            val entityInfo = buildEntityInfoForAgent(observedEntity, this.simulation.simulationTime)
            this.buildJsEntity(entityInfo)
         }.toList().toJs()
      }
   }

   @HostAccess.Export
   fun sleep(millis: Long) {
      this.endIfRequested()
      Thread.sleep(millis)
      this.endIfRequested()
   }

   private fun <T : Any> waitForSimulationRequestResultOrNull(
      sleepIntervalMs: Int = 25,
      task: () -> T?
   ): T? {
      var resultVar: T? = null

      this.waitForSimulationRequest(
         sleepIntervalMs = sleepIntervalMs,
         task = {
            resultVar = task()
         }
      )

      return resultVar
   }

   private fun waitForSimulationRequest(
      sleepIntervalMs: Int = 25,
      task: () -> Unit
   ) {
      this.endIfRequested()

      var didFinishTask = false
      var resultExceptionVar: Exception? = null

      this.simulation.addRequestFromBackgroundThread(
         task = {
            task()
            didFinishTask = true
         },
         handleException = {
            println("AgentJavaScriptApi.waitForSimulationRequest: Got exception: " + it.stackTraceToString())
            resultExceptionVar = it
         }
      )
      println("AgentJavaScriptApi.waitForSimulationRequest: Returned from addRequestFromBackgroundThread")

      while (true) {
         val resultException = resultExceptionVar

         if (resultException != null) {
            println("AgentJavaScriptApi.waitForSimulationRequest: Re-throwing task exception")
            throw Exception("Exception from waitForSimulationRequest", resultException)
         } else if (didFinishTask) {
            return
         } else {
            this.sleep(sleepIntervalMs.toLong())
         }
      }
   }

   private fun <T : Any> waitForSimulationRequestResult(
      sleepIntervalMs: Int = 25,
      task: () -> T
   ): T {
      var resultVar: T? = null

      this.waitForSimulationRequest(
         sleepIntervalMs = sleepIntervalMs,
         task = {
            resultVar = task()
         }
      )

      return resultVar ?: throw Exception("resultVar is null (should not be possible?)")
   }

   private fun addPendingActionAndWaitForResult(action: Action): ActionResult {
      this.endIfRequested()

      val actionUniqueId = action.actionUniqueId

      val json = Json {
         prettyPrint = true
      }

      val actionJsonString = json.encodeToString(action)

      println("AgentJavaScriptApi: Added action: ($actionUniqueId)\n${actionJsonString}")

      this.waitForSimulationRequest {
         this.agentIntegration.addPendingAction(action)
      }

      run {
         var waitingCounter = 0
         while (true) {
            ++waitingCounter
            this.sleep(200)

            val actionHasStarted = this.waitForSimulationRequestResult {
               this.agentIntegration.hasStartedAction(actionUniqueId)
            }

            if (!actionHasStarted) {
               println("AgentJavaScriptApi: Action has not yet started, waiting... $actionUniqueId ($waitingCounter)")
            } else {
               val actionResult = this.waitForSimulationRequestResultOrNull {
                  this.agentIntegration.getActionResult(actionUniqueId)
               }

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

   @HostAccess.Export
   fun pickUpItem(entityId: String) {
      this.pickUpItem(
         entityId = entityId,
         reason = null
      )
   }

   @HostAccess.Export
   fun pickUpItem(entityId: String, reason: String?) {
      this.addPendingActionAndWaitForResult(
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
      this.addPendingActionAndWaitForResult(
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
      this.addPendingActionAndWaitForResult(
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
      this.addPendingActionAndWaitForResult(
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
      this.addPendingActionAndWaitForResult(
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
      this.addPendingActionAndWaitForResult(
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
      this.addPendingActionAndWaitForResult(
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
      this.addPendingActionAndWaitForResult(
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
      this.addPendingActionAndWaitForResult(
         Action(
            facialExpressionEmoji = emoji
         )
      )
   }
}

