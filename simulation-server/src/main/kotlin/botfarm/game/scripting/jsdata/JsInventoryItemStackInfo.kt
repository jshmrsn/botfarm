package botfarm.game.scripting.jsdata

import botfarmshared.game.apidata.ItemStackInfo
import org.graalvm.polyglot.HostAccess


class JsInventoryItemStackInfo(
   val api: AgentJavaScriptApi?,
   val itemStackInfo: ItemStackInfo,
   @HostAccess.Export @JvmField val stackIndex: Int
) {
   @HostAccess.Export
   @JvmField
   val name: String = itemStackInfo.itemName

   @HostAccess.Export
   @JvmField
   val description = itemStackInfo.itemDescription

   @HostAccess.Export
   @JvmField
   val itemTypeId = itemStackInfo.itemConfigKey

   @HostAccess.Export
   @JvmField
   val isEquipped = itemStackInfo.isEquipped

   @HostAccess.Export
   @JvmField
   val canBeEquipped = itemStackInfo.canBeEquipped

   @HostAccess.Export
   @JvmField
   val amount = itemStackInfo.amount

   @HostAccess.Export
   @JvmField
   val canBeUsedWhenEquipped = itemStackInfo.spawnItemOnUseConfigKey != null

   @HostAccess.Export
   @JvmField
   val spawnItemOnUseItemTypeId = itemStackInfo.spawnItemOnUseConfigKey

   @HostAccess.Export
   fun dropAll() {
      this.dropAll(reason = null)
   }

   @HostAccess.Export
   fun dropAll(reason: String?) {
      if (this.api == null) {
         throw Exception("JsInventoryItemStackInfo.dropAll: api is null")
      }

      this.api.dropItem(
         itemConfigKey = this.itemTypeId,
         stackIndex = this.stackIndex,
         amount = null,
         reason = reason
      )
   }

   @HostAccess.Export
   fun dropAmount(amount: Int) {
      this.dropAmount(amount = amount, reason = null)
   }

   @HostAccess.Export
   fun dropAmount(amount: Int, reason: String?) {
      if (this.api == null) {
         throw Exception("JsInventoryItemStackInfo.dropAmount: api is null")
      }

      this.api.dropItem(
         itemConfigKey = this.itemTypeId,
         stackIndex = this.stackIndex,
         amount = amount,
         reason = reason
      )
   }

   @HostAccess.Export
   fun equip() {
      this.equip(reason = null)
   }

   @HostAccess.Export
   fun equip(reason: String?) {
      if (this.api == null) {
         throw Exception("JsInventoryItemStackInfo.equip: api is null")
      }

      this.api.equipItem(
         itemConfigKey = this.itemTypeId,
         stackIndex = this.stackIndex,
         reason = reason
      )
   }

   @HostAccess.Export
   fun use() {
      this.use(reason = null)
   }

   @HostAccess.Export
   fun use(reason: String?) {
      if (this.api == null) {
         throw Exception("JsInventoryItemStackInfo.use: api is null")
      }

      this.api.useEquippedToolItem(
         reason = reason
      )
   }
}