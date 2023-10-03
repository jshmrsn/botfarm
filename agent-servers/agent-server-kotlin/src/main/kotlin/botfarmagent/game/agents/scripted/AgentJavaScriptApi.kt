package botfarmagent.game.agents.scripted

import botfarmshared.engine.apidata.EntityId
import botfarmshared.game.apidata.*
import botfarmshared.misc.Vector2
import org.graalvm.polyglot.HostAccess

class JsVector2(
   val value: Vector2
) {
   @HostAccess.Export
   @JvmField
   val x = this.value.x

   @HostAccess.Export
   @JvmField
   val y = this.value.y

   override fun toString(): String {
      return "JsVector2(${this.x}, ${this.y})"
   }

   @HostAccess.Export
   fun getMagnitude(): Double {
      return this.value.magnitude()
   }

   @HostAccess.Export
   fun distanceTo(other: JsVector2): Double {
      return this.value.distance(other.value)
   }

   @HostAccess.Export
   fun plus(other: JsVector2): JsVector2 {
      return JsVector2(this.value + other.value)
   }

   @HostAccess.Export
   fun minus(other: JsVector2): JsVector2 {
      return JsVector2(this.value - other.value)
   }
}

class JsItemOnGroundComponent(
   val api: AgentJavaScriptApi,
   val entityInfo: EntityInfo,
   @HostAccess.Export @JvmField val description: String,
   @HostAccess.Export @JvmField val name: String,
   @HostAccess.Export @JvmField val itemTypeId: String,
   @HostAccess.Export @JvmField val canBePickedUp: Boolean,
   @HostAccess.Export @JvmField val amount: Int
) {
   @HostAccess.Export
   fun pickup() {
      println("PICKUP CALLEd")
      this.api.pickUpItem(this.entityInfo.entityId.value)
   }
}

class JsItemInfo(
   @HostAccess.Export @JvmField val name: String,
   @HostAccess.Export @JvmField val description: String,
   @HostAccess.Export @JvmField val itemTypeId: String
) {
   constructor(itemInfo: ItemInfo) : this(
      name = itemInfo.name,
      description = itemInfo.description,
      itemTypeId = itemInfo.itemConfigKey
   )
}

class JsCharacterComponent(
   val api: AgentJavaScriptApi,
   val entityInfo: EntityInfo,

   @HostAccess.Export @JvmField val name: String,
   @HostAccess.Export @JvmField val gender: String,
   @HostAccess.Export @JvmField val skinColor: String,
   @HostAccess.Export @JvmField val age: Int,
   @HostAccess.Export @JvmField val description: String,
   @HostAccess.Export @JvmField val equippedItemInfo: ItemInfo? = null,
   @HostAccess.Export @JvmField val hairColor: String? = null,
   @HostAccess.Export @JvmField val hairStyle: String? = null
)

class JsInventoryItem()

class JsEntity(
   @HostAccess.Export @JvmField val location: JsVector2,
   @HostAccess.Export @JvmField val entityId: String,
   @HostAccess.Export @JvmField val itemOnGround: JsItemOnGroundComponent?,
   @HostAccess.Export @JvmField val character: JsCharacterComponent?
)

fun <T> List<T>.toJs(): JsArray<T> {
   return JsArray(this)
}

fun Vector2.toJs(): JsVector2 {
   return JsVector2(this)
}

class JsArray<T>(
   val values: List<T>
) {
   @HostAccess.Export
   fun getLength(): Int = this.values.size

   @HostAccess.Export
   fun get(index: Int): T {
      return this.values.get(index)
   }
}

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
   }

   @HostAccess.Export
   fun makeVector2(x: Double, y: Double): JsVector2 {
      return JsVector2(Vector2(x, y))
   }

   fun buildJsEntity(entityInfo: EntityInfo): JsEntity {
      val itemOnGround = entityInfo.itemEntityInfo?.let { itemEntityInfo ->
         JsItemOnGroundComponent(
            api = this,
            entityInfo = entityInfo,

            description = itemEntityInfo.description,
            name = itemEntityInfo.itemName,
            itemTypeId = itemEntityInfo.itemConfigKey,
            canBePickedUp = itemEntityInfo.canBePickedUp,
            amount = itemEntityInfo.amount
         )
      }

      val character = entityInfo.characterEntityInfo?.let {
         JsCharacterComponent(
            api = this,
            entityInfo = entityInfo,

            name = it.name,
            gender = it.gender,
            skinColor = it.skinColor,
            age = it.age,
            description = it.description,
            equippedItemInfo = it.equippedItemInfo,
            hairColor = it.hairColor,
            hairStyle = it.hairStyle
         )
      }

      return JsEntity(
         entityId = entityInfo.entityId.value,
         location = entityInfo.location.toJs(),
         itemOnGround = itemOnGround,
         character = character
      )
   }

   @HostAccess.Export
   fun getCurrentInventory(): JsArray<JsInventoryItem> {
      return listOf<JsInventoryItem>().toJs()
   }

   @HostAccess.Export
   fun getCurrentNearbyEntities(): JsArray<JsEntity> {
      return this.agent.mostRecentInputs.newObservations.entitiesById.values.map { entityInfo ->
         this.buildJsEntity(entityInfo)
      }.toList().toJs()
   }

   @HostAccess.Export
   fun sleep(millis: Long) {
      Thread.sleep(millis)
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

