package botfarmagent.game.common

import botfarmshared.engine.apidata.EntityId
import botfarmshared.game.apidata.Observations

fun buildAutomaticShortTermMemoriesForNewObservations(
   newObservations: Observations,
   selfEntityId: EntityId
): List<AutomaticShortTermMemory> {
   return mutableListOf<AutomaticShortTermMemory>()
      .also { newAutomaticShortTermMemories ->
         newObservations.activityStreamEntries.forEach { activityStreamEntry ->
            if (activityStreamEntry.sourceEntityId != selfEntityId) {
               newAutomaticShortTermMemories.add(
                  AutomaticShortTermMemory(
                     time = activityStreamEntry.time,
                     summary = activityStreamEntry.title + (activityStreamEntry.message?.let {
                        "\n" + activityStreamEntry.message
                     } ?: "")
                  )
               )
            }
         }

         newObservations.selfSpokenMessages.forEach { selfSpokenMessage ->
            newAutomaticShortTermMemories.add(
               AutomaticShortTermMemory(
                  time = selfSpokenMessage.time,
                  summary = "I said \"${selfSpokenMessage.message}\" (while standing at ${selfSpokenMessage.location.asJsonArrayRounded})",
                  forcePreviousActivity = true
               )
            )
         }

         newObservations.spokenMessages.forEach { observedMessage ->
            println("Adding heard message: ${observedMessage.message}")

            newAutomaticShortTermMemories.add(
               AutomaticShortTermMemory(
                  time = observedMessage.time,
                  summary = "I heard ${observedMessage.characterName} say \"${observedMessage.message}\" (they were at ${observedMessage.speakerLocation.asJsonArrayRounded})",
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

         newObservations.actionOnEntityRecords.forEach { record ->
            val reasonSuffix = buildReasonSuffix(record.reason)

            val summary = if (record.resultAutoInteractType == record.desiredAutoInteractType) {
               "I took the action '${record.resultAutoInteractType.name}' on entity '${record.targetEntityId.value}'$reasonSuffix"
            } else {
               "I attempted to take the action '${record.resultAutoInteractType.name}' on entity '${record.targetEntityId.value}'$reasonSuffix, but instead got the outcome ${record.resultAutoInteractType.name}"
            }

            newAutomaticShortTermMemories.add(
               AutomaticShortTermMemory(
                  time = record.startedAtTime,
                  summary = summary,
                  forcePreviousActivity = true
               )
            )
         }

         newObservations.actionOnInventoryItemActionRecords.forEach { record ->
            val reasonSuffix = buildReasonSuffix(
               record.reason
            )

            val summary = if (record.desiredActionOnInventoryType == record.resultActionOnInventoryType) {
               "I performed action '${record.resultActionOnInventoryType.name}' on the item '${record.itemConfigKey}' from my inventory$reasonSuffix"
            } else {
               "I attempted to perform action '${record.desiredActionOnInventoryType.name}' on the item '${record.itemConfigKey}' from my inventory '${record.itemConfigKey}'$reasonSuffix, but instead got the outcome ${record.resultActionOnInventoryType.name}"
            }

            newAutomaticShortTermMemories.add(
               AutomaticShortTermMemory(
                  time = record.startedAtTime,
                  summary = summary,
                  forcePreviousActivity = true
               )
            )
         }

         newObservations.craftItemActionRecords.forEach { record ->
            newAutomaticShortTermMemories.add(
               AutomaticShortTermMemory(
                  time = record.startedAtTime,
                  summary = "I crafted an '${record.itemConfigKey}' item" + buildReasonSuffix(
                     record.reason
                  ),
                  forcePreviousActivity = true
               )
            )
         }

         newObservations.selfThoughts.forEach { record ->
            newAutomaticShortTermMemories.add(
               AutomaticShortTermMemory(
                  time = record.time,
                  summary = "I had the thought '${record.thought}'" + buildReasonSuffix(
                     record.reason
                  ),
                  forcePreviousActivity = true
               )
            )
         }
      }
}