package botfarmagent.game.agents.scripted

import botfarmshared.game.apidata.EntityInfo
import kotlinx.serialization.json.*

fun buildStateForEntity(
   entityInfo: EntityInfo
): JsonObject {
   val characterEntityInfo = entityInfo.characterEntityInfo

   return buildJsonObject {
      put("entityId", entityInfo.entityId.value)

      if (characterEntityInfo != null) {
         put("description", characterEntityInfo.description)
         put("name", characterEntityInfo.name)
         put("age", characterEntityInfo.age)
         put("gender", characterEntityInfo.gender)
      }

      val itemEntityInfo = entityInfo.itemEntityInfo

      if (itemEntityInfo != null) {
         put("itemName", itemEntityInfo.itemName)
         put("description", itemEntityInfo.description)
      }

      if (entityInfo.availableActionIds != null) {
         putJsonArray("availableActionIds") {
            entityInfo.availableActionIds.forEach {
               add(it)
            }
         }
      }

      put("location", entityInfo.location.asJsonArrayRounded)
   }
}