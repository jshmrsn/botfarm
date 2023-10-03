package botfarmagent.game.agents.scripted

import botfarmshared.game.apidata.ActiveGrowthInfo
import botfarmshared.game.apidata.EntityInfo
import botfarmshared.game.apidata.ItemInfo
import org.graalvm.polyglot.HostAccess

class JsEntity(
   val api: AgentJavaScriptApi,
   val entityInfo: EntityInfo
) {
   @HostAccess.Export
   @JvmField
   val location: JsVector2 = entityInfo.location.toJs()

   @HostAccess.Export
   @JvmField
   val entityId: String = entityInfo.entityId.value

   @HostAccess.Export
   @JvmField
   val grower: JsGrowerComponent? = entityInfo.growerInfo?.let { growerInfo ->
      JsGrowerComponent(
         api = api,
         entityInfo = entityInfo,
         activeGrowth = growerInfo.activeGrowthInfo?.let { JsActiveGrowth(it) }
      )
   }

   @HostAccess.Export
   @JvmField
   val itemOnGround: JsItemOnGroundComponent? = entityInfo.itemInfo?.let { itemInfo ->
      JsItemOnGroundComponent(
         api = api,
         entityInfo = entityInfo,
         description = itemInfo.description,
         name = itemInfo.itemName,
         itemTypeId = itemInfo.itemConfigKey,
         canBePickedUp = itemInfo.canBePickedUp,
         amount = itemInfo.amount
      )
   }

   @HostAccess.Export
   @JvmField
   val damageable: JsDamageableComponent? = entityInfo.damageableInfo?.let { damageableInfo ->
      JsDamageableComponent(
         api = api,
         entityInfo = entityInfo,
         canBeDamagedByEquippedItemTypeId = damageableInfo.damageableByEquippedToolItemConfigKey,
         hp = damageableInfo.hp
      )
   }

   @HostAccess.Export
   @JvmField
   val character: JsCharacterComponent? = entityInfo.characterInfo?.let {
      JsCharacterComponent(
         api = api,
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
      this.api.pickUpItem(
         entityId = this.entityInfo.entityId.value
      )
   }
}

class JsDamageableComponent(
   val api: AgentJavaScriptApi,
   val entityInfo: EntityInfo,
   @HostAccess.Export @JvmField val hp: Int,
   @HostAccess.Export @JvmField val canBeDamagedByEquippedItemTypeId: String?
) {
   @HostAccess.Export
   fun attackWithEquippedItem() {
      this.api.interactWithEntity(
         entityId = this.entityInfo.entityId.value
      )
   }
}



class JsActiveGrowth(
   val activeGrowthInfo: ActiveGrowthInfo
) {
   @HostAccess.Export
   @JvmField
   val growingItemTypeId = activeGrowthInfo.growableItemConfigKey

   @HostAccess.Export
   @JvmField
   val growingIntoItemTypeId = activeGrowthInfo.growingIntoItemConfigKey

   @HostAccess.Export
   @JvmField
   val duration = activeGrowthInfo.duration

   @HostAccess.Export
   @JvmField
   val startTime = activeGrowthInfo.startTime
}

class JsGrowerComponent(
   val api: AgentJavaScriptApi,
   val entityInfo: EntityInfo,
   @HostAccess.Export @JvmField val activeGrowth: JsActiveGrowth?
) {
   @HostAccess.Export
   fun startGrowingEquippedItem() {
      this.api.interactWithEntity(
         entityId = this.entityInfo.entityId.value
      )
   }
}


