package botfarmagent.game.agents.scripted

import botfarmshared.game.apidata.ItemStackInfo
import org.graalvm.polyglot.HostAccess

class JsInventoryItemStackInfo(
   val api: AgentJavaScriptApi,
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
      this.api.dropItem(
         itemConfigKey = this.itemTypeId,
         stackIndex = this.stackIndex,
         amount = null,
         reason = null
      )
   }

   @HostAccess.Export
   fun dropAmount(amount: Int) {
      this.api.dropItem(
         itemConfigKey = this.itemTypeId,
         stackIndex = this.stackIndex,
         amount = amount,
         reason = null
      )
   }

   @HostAccess.Export
   fun equip() {
      this.api.equipItem(
         itemConfigKey = this.itemTypeId,
         stackIndex = this.stackIndex,
         reason = null
      )
   }


   @HostAccess.Export
   fun use() {
      this.api.useEquippedItem(
         reason = null
      )
   }
}