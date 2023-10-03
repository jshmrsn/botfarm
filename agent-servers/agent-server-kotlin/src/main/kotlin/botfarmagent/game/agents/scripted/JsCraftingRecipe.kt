package botfarmagent.game.agents.scripted

import botfarmshared.game.apidata.CraftingRecipe
import org.graalvm.polyglot.HostAccess

class JsItemCostEntry(
   @HostAccess.Export @JvmField val itemTypeId: String,
   @HostAccess.Export @JvmField val amount: Int
)


class JsCraftingRecipe(
   val api: AgentJavaScriptApi,
   val craftingRecipe: CraftingRecipe,
   @HostAccess.Export
   @JvmField
   val canCurrentlyAfford: Boolean,
   jsConversionContext: JsConversionContext?
) {
   @HostAccess.Export
   @JvmField
   val itemTypeId: String = this.craftingRecipe.itemConfigKey

   @HostAccess.Export
   @JvmField
   val description: String = this.craftingRecipe.description

   @HostAccess.Export
   @JvmField
   val costEntries: Any = this.craftingRecipe.cost.entries.map {
      JsItemCostEntry(
         itemTypeId = it.itemConfigKey,
         amount = it.amount
      )
   }.toJs(jsConversionContext)

   @HostAccess.Export
   fun craft(reason: String?) {
      this.api.craftItem(
         itemConfigKey = this.craftingRecipe.itemConfigKey,
         reason = reason
      )
   }
}