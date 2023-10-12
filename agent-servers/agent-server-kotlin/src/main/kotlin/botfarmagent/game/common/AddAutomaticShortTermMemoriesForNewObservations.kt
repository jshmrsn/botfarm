package botfarmagent.game.common

import botfarmshared.engine.apidata.EntityId
import botfarmshared.game.apidata.ActionResultType
import botfarmshared.game.apidata.ActionType
import botfarmshared.game.apidata.Observations

val String.quotedAndEscaped: String
   get() = "\"${this.replace("\"", "")}\""

fun buildAutomaticShortTermMemoriesForNewObservations(
   newObservations: Observations,
   selfEntityId: EntityId
): List<AutomaticShortTermMemory> {
   fun getNameForEntityId(entityId: EntityId?, itemConfigKey: String? = null): String {
      if (entityId == null) {
         return itemConfigKey ?: "?"
      }

      if (itemConfigKey != null) {
         return "'${itemConfigKey}' item entity '${entityId.value}'"
      } else {
         val entity = newObservations.entitiesById[entityId]

         if (entity != null && entity.entityInfo.characterInfo != null) {
            return "human named '${entity.entityInfo.characterInfo.name}' '${entityId.value}'"
         } else {
            return "entity '${entityId.value}'"
         }
      }
   }

   return mutableListOf<AutomaticShortTermMemory>()
      .also { newAutomaticShortTermMemories ->
         newObservations.activityStreamEntries.forEach { entry ->
            val message = entry.message ?: ""

            val reasonSuffix = if (entry.agentReason != null) {
               " (my reason for this action was: ${entry.agentReason.quotedAndEscaped})"
            } else {
               ""
            }

            val summary = if (entry.actionType == ActionType.Speak) {
               if (entry.sourceEntityId == selfEntityId) {
                  "I said: ${message.quotedAndEscaped}"
               } else {
                  "I heard ${getNameForEntityId(entry.sourceEntityId)} say: ${message.quotedAndEscaped}"
               }
            } else if (entry.actionType == ActionType.Thought) {
               "I had the thought: ${message.quotedAndEscaped}"
            } else if (entry.actionType == ActionType.DropItem) {
               if (entry.actionResultType == ActionResultType.Success) {
                  val shared = "an item: ${getNameForEntityId(entry.targetEntityId, entry.targetItemConfigKey)}"

                  if (entry.sourceEntityId == selfEntityId) {
                     "I dropped $shared"
                  } else {
                     "I saw ${getNameForEntityId(entry.sourceEntityId)} drop $shared"
                  }
               } else {
                  "I attempted to drop an item '${entry.targetItemConfigKey}', but got failure ${entry.actionResultType?.name}"
               }
            } else if (entry.actionType == ActionType.PickUpItem) {
               if (entry.actionResultType == ActionResultType.Success) {
                  val shared = "an '${entry.targetItemConfigKey}' item (${entry.targetEntityId?.value})"

                  if (entry.sourceEntityId == selfEntityId) {
                     "I picked up $shared"
                  } else {
                     "I saw ${getNameForEntityId(entry.sourceEntityId)} pick up $shared"
                  }
               } else {
                  "I attempted to pick up an entity item '${entry.targetItemConfigKey}' '${entry.targetEntityId?.value}', but got failure ${entry.actionResultType?.name}"
               }
            } else if (entry.actionType == ActionType.UseEquippedTool) {
               if (entry.actionResultType == ActionResultType.Success) {
                  val spawnedItemsString = (entry.spawnedItems ?: listOf()).map {
                     "${it.itemConfigKey} (${it.entityId.value})"
                  }.joinToString(", ")

                  val shared =
                     "an ${entry.targetItemConfigKey} inventory item to create entity items: $spawnedItemsString"

                  if (entry.sourceEntityId == selfEntityId) {
                     "I used $shared"
                  } else {
                     "I saw ${getNameForEntityId(entry.sourceEntityId, entry.sourceItemConfigKey)} use $shared"
                  }
               } else {
                  "I attempted to use the item ${entry.targetItemConfigKey} to create an '${entry.resultItemConfigKey}' item, but got failure ${entry.actionResultType?.name}"
               }
            } else if (entry.actionType == ActionType.UseToolToKillEntity) {
               if (entry.actionResultType == ActionResultType.Success) {
                  val shared =
                     "'${entry.actionItemConfigKey}' to harvest a '${entry.targetItemConfigKey}'" + if (entry.spawnedItems != null) {
                        ", which created new item entities on the ground: ${
                           entry.spawnedItems.map { it.itemConfigKey + " (${it.entityId.value})" }.joinToString(", ")
                        }"
                     } else {
                        ""
                     }

                  if (entry.sourceEntityId == selfEntityId) {
                     "I used $shared"
                  } else {
                     "I saw ${getNameForEntityId(entry.sourceEntityId)} use $shared"
                  }
               } else {
                  "I attempted to use '${entry.actionItemConfigKey}' to harvest a '${
                     getNameForEntityId(
                        entry.targetEntityId,
                        entry.targetItemConfigKey
                     )
                  }', but got failure ${entry.actionResultType?.name}"
               }
            } else if (entry.actionType == ActionType.UseToolToDamageEntity) {
               if (entry.actionResultType == ActionResultType.Success) {
                  val shared = "'${entry.actionItemConfigKey}' to damage a '${entry.targetItemConfigKey}'"

                  if (entry.sourceEntityId == selfEntityId) {
                     "I used $shared"
                  } else {
                     "I saw ${getNameForEntityId(entry.sourceEntityId)} use $shared"
                  }
               } else {
                  "I attempted to use '${entry.actionItemConfigKey}' to harvest a '${
                     getNameForEntityId(
                        entry.targetEntityId,
                        entry.targetItemConfigKey
                     )
                  }', but got failure ${entry.actionResultType?.name}"
               }
            } else {
               entry.title + (entry.message?.let {
                  "\n" + entry.message
               } ?: "")
            }

            newAutomaticShortTermMemories.add(
               AutomaticShortTermMemory(
                  time = entry.time,
                  summary = "$summary$reasonSuffix"
               )
            )
         }

         fun buildReasonSuffix(reason: String?) = if (reason != null) {
            " (because ${reason})"
         } else {
            ""
         }

         newObservations.movementRecords.forEach { movement ->
            val movementSummary =
               "I started walking from ${movement.startPoint.asJsonArrayRounded} to ${movement.endPoint.asJsonArrayRounded}" + buildReasonSuffix(
                  movement.reason
               )

            newAutomaticShortTermMemories.add(
               AutomaticShortTermMemory(
                  time = movement.startedAtTime,
                  summary = movementSummary,
                  deDuplicationCategory = "walkTo",
                  forcePreviousActivity = true
               )
            )
         }
      }
}