package botfarmagent.game.agents.scripted

import botfarmshared.engine.apidata.EntityId
import botfarmshared.game.apidata.*
import botfarmshared.misc.Vector2
import org.graalvm.polyglot.HostAccess

class AgentJavaScriptApi(
   val agent: ScriptedAgent
) {
   @HostAccess.Export
   fun speak(message: String) {
      this.addActions(
         Actions(
            speak = message
         )
      )
      this.sleep(1000)
   }

   @HostAccess.Export
   fun makeVector2(x: Double, y: Double): JsVector2 {
      return JsVector2(Vector2(x, y))
   }

   fun buildJsInventoryItemStackInfo(
      itemStackInfo: ItemStackInfo,
      stackIndex: Int
   ): JsInventoryItemStackInfo {
      return JsInventoryItemStackInfo(
         api = this,
         stackIndex = stackIndex,
         itemStackInfo = itemStackInfo
      )
   }

   @HostAccess.Export
   fun getTotalInventoryAmountForItemTypeId(itemTypeId: String): Int {
      val inventory = this.agent.mostRecentInputs.selfInfo.inventoryInfo
      var total = 0
      inventory.itemStacks.forEach {
         if (it.itemConfigKey == itemTypeId) {
            total += it.amount
         }
      }
      return total
   }

   fun buildJsCraftingRecipe(
      craftingRecipe: CraftingRecipe
   ): JsCraftingRecipe {
      var canCurrentlyAfford = true

      craftingRecipe.cost.entries.forEach {
         val currentAmount = this.getTotalInventoryAmountForItemTypeId(itemTypeId = it.itemConfigKey)
         if (it.amount < currentAmount) {
            canCurrentlyAfford = false
         }
      }

      return JsCraftingRecipe(
         api = this,
         craftingRecipe = craftingRecipe,
         canCurrentlyAfford = canCurrentlyAfford
      )
   }

   @HostAccess.Export
   fun getAllCraftingRecipes(): JsArray<JsCraftingRecipe> {
      return this.agent.mostRecentInputs.craftingRecipes.map {
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
      val inventory = this.agent.mostRecentInputs.selfInfo.inventoryInfo

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
      return this.agent.mostRecentInputs.newObservations.entitiesById.values.map { entityInfo ->
         this.buildJsEntity(entityInfo)
      }.toList().toJs()
   }

   @HostAccess.Export
   fun sleep(millis: Long) {
      println("Sleeping $millis")
      Thread.sleep(millis)
      println("Done sleeping $millis")
   }

   fun addActions(actions: Actions) {
      this.agent.addPendingResult(
         AgentStepResult(
            actions = actions
         )
      )
   }

   @HostAccess.Export
   fun pickUpItem(entityId: String) {
      this.addActions(
         Actions(
            actionOnEntity = ActionOnEntity(
               actionId = "pickupItem",
               reason = "JavaScript",
               targetEntityId = EntityId(entityId)
            )
         )
      )
      this.sleep(1000)
   }


   @HostAccess.Export
   fun interactWithEntity(entityId: String) {
      this.addActions(
         Actions(
            actionOnEntity = ActionOnEntity(
               actionId = "interact",
               reason = "JavaScript",
               targetEntityId = EntityId(entityId)
            )
         )
      )
      this.sleep(1000)
   }

   @HostAccess.Export
   fun craftItem(
      itemConfigKey: String,
      reason: String? = null
   ) {
      this.addActions(
         Actions(
            craftItemAction = CraftItemAction(
               reason = reason ?: "JavaScript",
               itemConfigKey = itemConfigKey
            )
         )
      )
      this.sleep(1000)
   }

   @HostAccess.Export
   fun equipItem(
      itemConfigKey: String,
      stackIndex: Int?,
      reason: String? = null
   ) {
      this.addActions(
         Actions(
            actionOnInventoryItem = ActionOnInventoryItem(
               actionId = "equipItem",
               reason = reason ?: "JavaScript",
               itemConfigKey = itemConfigKey,
               stackIndex = stackIndex
            )
         )
      )
      this.sleep(1000)
   }

   @HostAccess.Export
   fun useEquippedItem(
      reason: String?
   ) {
      this.addActions(
         Actions(
            useEquippedToolItem = UseEquippedToolItem(
               reason = reason ?: "JavaScript"
            )
         )
      )
      this.sleep(1000)
   }

   @HostAccess.Export
   fun dropItem(
      itemConfigKey: String,
      stackIndex: Int?,
      amount: Int?,
      reason: String? = null
   ) {
      this.addActions(
         Actions(
            actionOnInventoryItem = ActionOnInventoryItem(
               actionId = "dropItem",
               reason = reason ?: "JavaScript",
               itemConfigKey = itemConfigKey,
               stackIndex = stackIndex,
               amount = amount
            )
         )
      )
      this.sleep(1000)
   }

   @HostAccess.Export
   fun walkTo(
      destination: JsVector2
   ) {
      this.addActions(
         Actions(
            walk = WalkAction(
               reason = "JavaScript",
               location = listOf(destination.value.x, destination.value.y)
            )
         )
      )
      this.sleep(1000)
//      val state = this.agentAPI.state
//      val simulation = this.agentAPI.simulation
//      val entity = this.agentAPI.entity
//      val endPoint = Vector2(2000.0, 2000.0) + Vector2.randomSignedXY(1000.0)
//
//
//      println("a: " + valueA)
//      println("b: " + valueB)
//      this.agentAPI.walk(
//         endPoint = endPoint,
//         reason = "AI"
//      )
//
//      while (true) {
//         val shouldBreak = synchronized(simulation) {
//            val positionComponent = entity.getComponent<PositionComponentData>()
//            val keyFrames = positionComponent.data.positionAnimation.keyFrames
//
//            val isDoneMoving = keyFrames.isEmpty() ||
//                    simulation.getCurrentSimulationTime() > keyFrames.last().time
//
//            if (isDoneMoving) {
//               true
//            } else {
//               false
//            }
//         }
//
//         if (shouldBreak) {
//            break
//         } else {
//            Thread.sleep(100)
//         }
//      }
   }
}

