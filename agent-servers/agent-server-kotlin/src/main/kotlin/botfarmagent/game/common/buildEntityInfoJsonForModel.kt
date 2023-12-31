package botfarmagent.game.common

import botfarmshared.game.apidata.EntityInfo
import kotlinx.serialization.json.*

fun buildEntityInfoJsonForMemory(
   entityInfo: EntityInfo
): JsonObject {
   val characterInfo = entityInfo.characterInfo

   return buildJsonObject {
      put("entityId", entityInfo.entityId.value)

      if (characterInfo != null) {
         put("description", characterInfo.description)
         put("name", characterInfo.name)
         put("age", characterInfo.age)
         put("gender", characterInfo.gender)
      }

      val itemInfo = entityInfo.itemInfo

      if (itemInfo != null) {
         put("itemName", itemInfo.itemName)
         put("description", itemInfo.description)
      }

      put("location", entityInfo.location.asJsonArrayRounded)
   }
}

