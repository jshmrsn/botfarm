package botfarm.game.codeexecution.jsdata

import botfarmshared.game.apidata.CraftingRecipeInfo
import org.graalvm.polyglot.HostAccess

class JsItemCostEntry(
   @HostAccess.Export @JvmField val itemTypeId: String,
   @HostAccess.Export @JvmField val amount: Int
)


class JsCraftingRecipe(
   val api: AgentJavaScriptApi,
   val craftingRecipeInfo: CraftingRecipeInfo,
   jsConversionContext: JsConversionContext? = null
) {
   @HostAccess.Export
   @JvmField
   val itemTypeId: String = this.craftingRecipeInfo.itemConfigKey

   @HostAccess.Export
   @JvmField
   val description: String = this.craftingRecipeInfo.description

   @HostAccess.Export
   @JvmField
   val costEntries: Any = this.craftingRecipeInfo.cost.entries.map {
      JsItemCostEntry(
         itemTypeId = it.itemConfigKey,
         amount = it.amount
      )
   }.toJs(jsConversionContext)

   @HostAccess.Export
   @JvmField
   val canCurrentlyAfford: Boolean = this.craftingRecipeInfo.canCurrentlyAfford

   @HostAccess.Export
   fun craft(reason: String?) {
      this.api.craftItem(
         itemConfigKey = this.craftingRecipeInfo.itemConfigKey,
         reason = reason
      )
   }
}

