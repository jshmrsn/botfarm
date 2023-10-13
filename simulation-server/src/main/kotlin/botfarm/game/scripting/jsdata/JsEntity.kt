package botfarm.game.scripting.jsdata

import botfarmshared.game.apidata.ActiveGrowthInfo
import botfarmshared.game.apidata.EntityInfo
import botfarmshared.game.apidata.ItemInfo
import org.graalvm.polyglot.HostAccess

class JsEntity(
   val api: AgentJavaScriptApi?,
   val entityInfo: EntityInfo
) {
   @HostAccess.Export
   @JvmField
   val location: JsVector2 = this.entityInfo.location.roundedToJs()

   @HostAccess.Export
   @JvmField
   val entityId: String = this.entityInfo.entityId.value

   @HostAccess.Export
   @JvmField
   val isVisible = this.entityInfo.isVisible

   @HostAccess.Export
   @JvmField
   val isStale = this.entityInfo.isStale

   @HostAccess.Export
   @JvmField
   val grower: JsGrowerComponent? = this.entityInfo.growerInfo?.let { growerInfo ->
      JsGrowerComponent(
         api = this.api,
         entityInfo = this.entityInfo,
         activeGrowth = growerInfo.activeGrowthInfo?.let { JsActiveGrowth(it) }
      )
   }

   @HostAccess.Export
   @JvmField
   val itemOnGround: JsItemOnGroundComponent? = this.entityInfo.itemInfo?.let { itemInfo ->
      JsItemOnGroundComponent(
         api = this.api,
         entityInfo = this.entityInfo,
         description = itemInfo.description,
         name = itemInfo.itemName,
         itemTypeId = itemInfo.itemConfigKey,
         canBePickedUp = itemInfo.canBePickedUp,
         amount = itemInfo.amount
      )
   }

   @HostAccess.Export
   @JvmField
   val damageable: JsDamageableComponent? = this.entityInfo.damageableInfo?.let { damageableInfo ->
      JsDamageableComponent(
         api = this.api,
         entityInfo = this.entityInfo,
         canBeDamagedByEquippedItemTypeId = damageableInfo.damageableByEquippedToolItemConfigKey,
         hp = damageableInfo.hp
      )
   }

   @HostAccess.Export
   @JvmField
   val character: JsCharacterComponent? = this.entityInfo.characterInfo?.let {
      JsCharacterComponent(
         entityInfo = this.entityInfo,
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
   val api: AgentJavaScriptApi?,
   val entityInfo: EntityInfo,
   @HostAccess.Export @JvmField val description: String,
   @HostAccess.Export @JvmField val name: String,
   @HostAccess.Export @JvmField val itemTypeId: String,
   @HostAccess.Export @JvmField val canBePickedUp: Boolean,
   @HostAccess.Export @JvmField val amount: Int
) {
   @HostAccess.Export
   fun pickUp(reason: String?) {
      if (this.api == null) {
         throw Exception("JsItemOnGroundComponent.pickUp: api is null")
      }

      this.api.pickUpItem(
         entityId = this.entityInfo.entityId.value,
         reason = reason
      )
   }

   @HostAccess.Export
   fun pickUp() {
      this.pickUp(reason = null)
   }
}

class JsDamageableComponent(
   val api: AgentJavaScriptApi?,
   val entityInfo: EntityInfo,
   @HostAccess.Export @JvmField val hp: Int,
   @HostAccess.Export @JvmField val canBeDamagedByEquippedItemTypeId: String?
) {
   @HostAccess.Export
   fun attackWithEquippedItem(reason: String?) {
      if (this.api == null) {
         throw Exception("JsDamageableComponent.attackWithEquippedItem: api is null")
      }

      this.api.interactWithEntity(
         entityId = this.entityInfo.entityId.value,
         reason = reason
      )
   }

   @HostAccess.Export
   fun attackWithEquippedItem() {
      this.attackWithEquippedItem(reason = null)
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
   val api: AgentJavaScriptApi?,
   val entityInfo: EntityInfo,
   @HostAccess.Export @JvmField val activeGrowth: JsActiveGrowth?
) {
   @HostAccess.Export
   fun startGrowingEquippedItem(reason: String?) {
      if (this.api == null) {
         throw Exception("JsGrowerComponent.startGrowingEquippedItem: api is null")
      }

      this.api.interactWithEntity(
         entityId = this.entityInfo.entityId.value,
         reason = reason
      )
   }
}


