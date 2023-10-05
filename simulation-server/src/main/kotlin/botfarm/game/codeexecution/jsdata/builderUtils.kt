package botfarm.game.codeexecution.jsdata

import botfarm.game.codeexecution.JavaScriptCodeSerialization
import botfarm.game.components.ItemStack
import botfarm.game.config.ItemConfig
import botfarmshared.game.apidata.EntityInfo
import botfarmshared.game.apidata.EntityInfoWrapper
import botfarmshared.game.apidata.ItemStackInfo

fun ItemStack.buildInfo(itemConfig: ItemConfig): ItemStackInfo {
   return ItemStackInfo(
      itemConfigKey = this.itemConfigKey,
      amount = this.amount,
      itemName = itemConfig.name,
      itemDescription = itemConfig.description,
      canBeDropped = itemConfig.storableConfig?.canBeDropped ?: false,
      canBeEquipped = itemConfig.equippableConfig != null,
      isEquipped = this.isEquipped,
      spawnItemOnUseConfigKey = itemConfig.spawnItemOnUseConfig?.spawnItemConfigKey
   )
}

fun EntityInfo.buildWrapper(
   javaScriptVariableName: String,
   api: AgentJavaScriptApi
): EntityInfoWrapper {
   return EntityInfoWrapper(
      entityInfo = this,
      javaScriptVariableName = javaScriptVariableName,
      serializedAsJavaScript = JavaScriptCodeSerialization.serialize(
         JsEntity(
            api = api,
            entityInfo = this
         )
      )
   )
}