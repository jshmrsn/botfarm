package botfarmagent.game.common

import botfarmshared.engine.apidata.EntityId
import botfarmshared.game.apidata.ActionResultType
import botfarmshared.game.apidata.ActionType
import botfarmshared.game.apidata.Observations

val String.quotedAndEscaped: String
   get() = "\"${this.replace("\"", "")}\""

fun buildReasonSuffix(reason: String?) = if (reason != null) {
   " (because ${reason})"
} else {
   ""
}

fun buildAutomaticShortTermMemoriesForNewObservations(
   newObservations: Observations,
   selfEntityId: EntityId
): List<AutomaticShortTermMemory> {
   return mutableListOf<AutomaticShortTermMemory>()
      .also { newAutomaticShortTermMemories ->
         newObservations.activityStreamEntries.forEach { entry ->
            val message = entry.message ?: ""

            val reasonSuffix = if (entry.agentReason != null) {
               " (my reason for this action was: ${entry.agentReason.quotedAndEscaped})"
            } else {
               ""
            }

            val summary: String

            if (entry.actionType == ActionType.Speak) {
               if (entry.sourceEntityId == selfEntityId) {
                  summary = "I said: ${message.quotedAndEscaped}"
               } else {
                  summary = "I heard ${entry.sourceName?.quotedAndEscaped} say: ${message.quotedAndEscaped}"
               }
            } else if (entry.actionType == ActionType.Thought) {
               summary = "I had the thought: ${message.quotedAndEscaped}"
            } else if (entry.actionType == ActionType.DropItem) {
               if (entry.actionResultType == ActionResultType.Success) {
                  val shared = "an item: ${entry.targetName?.quotedAndEscaped}"

                  if (entry.sourceEntityId == selfEntityId) {
                     summary = "I dropped $shared"
                  } else {
                     summary = "I saw ${entry.sourceName?.quotedAndEscaped} drop $shared"
                  }
               } else {
                  summary =
                     "I attempted to drop an item '${entry.targetName?.quotedAndEscaped}', but got failure ${entry.actionResultType?.name}"
               }
            } else if (entry.actionType == ActionType.PickupItem) {
               if (entry.actionResultType == ActionResultType.Success) {
                  val shared = "an item: ${entry.targetName?.quotedAndEscaped}"

                  if (entry.sourceEntityId == selfEntityId) {
                     summary = "I picked up $shared"
                  } else {
                     summary = "I saw ${entry.sourceName?.quotedAndEscaped} pick up $shared"
                  }
               } else {
                  summary =
                     "I attempted to pick up an item ${entry.targetName?.quotedAndEscaped}, but got failure ${entry.actionResultType?.name}"
               }
            } else if (entry.actionType == ActionType.UseEquippedTool) {
               if (entry.actionResultType == ActionResultType.Success) {
                  val shared = "the item ${entry.targetName?.quotedAndEscaped} to create an ${entry.resultName?.quotedAndEscaped}"

                  if (entry.sourceEntityId == selfEntityId) {
                     summary = "I used $shared"
                  } else {
                     summary = "I saw ${entry.sourceName?.quotedAndEscaped} use $shared"
                  }
               } else {
                  summary =
                     "I attempted to use the item ${entry.targetName?.quotedAndEscaped} to create an ${entry.resultName?.quotedAndEscaped}, but got failure ${entry.actionResultType?.name}"
               }
            } else if (entry.actionType == ActionType.UseToolToDamageEntity) {
               if (entry.actionResultType == ActionResultType.Success) {
                  val shared =
                     "'${entry.actionItemName}' to harvest a '${entry.targetName}'" + if (entry.spawnedItems != null) {
                        ", which created new item entities on the ground: ${
                           entry.spawnedItems.map { it.itemConfigKey }.joinToString(", ")
                        }"
                     } else {
                        ""
                     }

                  if (entry.sourceEntityId == selfEntityId) {
                     summary = "I used $shared"
                  } else {
                     summary = "I saw ${entry.sourceName?.quotedAndEscaped} use $shared"
                  }
               } else {
                  summary =
                     "I attempted to use '${entry.actionItemName}' to harvest a '${entry.targetName}', but got failure ${entry.actionResultType?.name}"
               }
            } else {
               summary = entry.title + (entry.message?.let {
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

//         newObservations.selfSpokenMessages.forEach { selfSpokenMessage ->
//            newAutomaticShortTermMemories.add(
//               AutomaticShortTermMemory(
//                  time = selfSpokenMessage.time,
//                  summary = "I said \"${selfSpokenMessage.message}\" (while standing at ${selfSpokenMessage.location.asJsonArrayRounded})",
//                  forcePreviousActivity = true
//               )
//            )
//         }
//
//         newObservations.spokenMessages.forEach { observedMessage ->
//            println("Adding heard message: ${observedMessage.message}")
//
//            newAutomaticShortTermMemories.add(
//               AutomaticShortTermMemory(
//                  time = observedMessage.time,
//                  summary = "I heard ${observedMessage.characterName} say \"${observedMessage.message}\" (they were at ${observedMessage.speakerLocation.asJsonArrayRounded})",
//               )
//            )
//         }

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

//         newObservations.actionOnEntityRecords.forEach { record ->
//            val reasonSuffix = buildReasonSuffix(record.reason)
//
//            val summary = if (record.resultAutoInteractType == record.desiredAutoInteractType) {
//               "I took the action '${record.resultAutoInteractType.name}' on entity '${record.targetEntityId.value}'$reasonSuffix"
//            } else {
//               "I attempted to take the action '${record.resultAutoInteractType.name}' on entity '${record.targetEntityId.value}'$reasonSuffix, but instead got the outcome ${record.resultAutoInteractType.name}"
//            }
//
//            newAutomaticShortTermMemories.add(
//               AutomaticShortTermMemory(
//                  time = record.startedAtTime,
//                  summary = summary,
//                  forcePreviousActivity = true
//               )
//            )
//         }

//         newObservations.actionOnInventoryItemActionRecords.forEach { record ->
//            val reasonSuffix = buildReasonSuffix(
//               record.reason
//            )
//
//            val summary = if (record.desiredActionOnInventoryType == record.resultActionOnInventoryType) {
//               "I performed action '${record.resultActionOnInventoryType.name}' on the item '${record.itemConfigKey}' from my inventory$reasonSuffix"
//            } else {
//               "I attempted to perform action '${record.desiredActionOnInventoryType.name}' on the item '${record.itemConfigKey}' from my inventory '${record.itemConfigKey}'$reasonSuffix, but instead got the outcome ${record.resultActionOnInventoryType.name}"
//            }
//
//            newAutomaticShortTermMemories.add(
//               AutomaticShortTermMemory(
//                  time = record.startedAtTime,
//                  summary = summary,
//                  forcePreviousActivity = true
//               )
//            )
//         }

//         newObservations.craftItemActionRecords.forEach { record ->
//            newAutomaticShortTermMemories.add(
//               AutomaticShortTermMemory(
//                  time = record.startedAtTime,
//                  summary = "I crafted an '${record.itemConfigKey}' item" + buildReasonSuffix(
//                     record.reason
//                  ),
//                  forcePreviousActivity = true
//               )
//            )
//         }

//         newObservations.selfThoughts.forEach { record ->
//            newAutomaticShortTermMemories.add(
//               AutomaticShortTermMemory(
//                  time = record.time,
//                  summary = "I had the thought '${record.thought}'" + buildReasonSuffix(
//                     record.reason
//                  ),
//                  forcePreviousActivity = true
//               )
//            )
//         }
      }
}