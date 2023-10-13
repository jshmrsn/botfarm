package botfarmagent.game.common

import botfarmshared.engine.apidata.EntityId
import botfarmshared.game.apidata.*
import botfarmshared.misc.Vector2

class LongTermMemory(
   val createdTime: Double,
   val id: Int,
   val content: String,
   val importance: Int,
   val createdAtLocation: Vector2
)

val String.quotedAndEscaped: String
   get() = "\"${this.replace("\"", "")}\""

class MemoryState {
   var shortTermMemory = ""
   val longTermMemories = mutableListOf<LongTermMemory>()
   val automaticShortTermMemories: MutableList<AutomaticShortTermMemory> = mutableListOf<AutomaticShortTermMemory>()
   val automaticShortTermMemoriesSinceLastPrompt: MutableList<AutomaticShortTermMemory> =
      mutableListOf<AutomaticShortTermMemory>()

   fun ingestNewObservations(
      selfInfo: SelfInfo,
      simulationTime: Double,
      entitiesById: Map<EntityId, EntityInfoWrapper>,
      newObservations: Observations
   ) {
      val selfEntityId = selfInfo.entityInfoWrapper.entityInfo.entityId

      fun getNameForEntityId(entityId: EntityId?, itemConfigKey: String? = null): String {
         if (entityId == null) {
            return itemConfigKey ?: "?"
         }

         return if (itemConfigKey != null) {
            "'${itemConfigKey}' (${entityId.value})"
         } else {
            val entity = entitiesById[entityId]

            if (entity != null && entity.entityInfo.characterInfo != null) {
               "human named '${entity.entityInfo.characterInfo.name}' (${entityId.value})"
            } else {
               "entity (${entityId.value})"
            }
         }
      }

      val newAutomaticShortTermMemories = mutableListOf<AutomaticShortTermMemory>()

      for (entry in newObservations.activityStreamEntries) {
         if (entry.actionType == ActionType.Thought) {
            this.ingestThought(
               selfInfo = selfInfo,
               simulationTime = simulationTime,
               thought = entry.message ?: "?"
            )
            continue
         }

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
               val shared = "a '${entry.targetItemConfigKey}' item (${entry.targetEntityId?.value})"

               if (entry.sourceEntityId == selfEntityId) {
                  "I picked up $shared"
               } else {
                  "I saw ${getNameForEntityId(entry.sourceEntityId)} pick up $shared"
               }
            } else {
               "I attempted to pick up an entity item '${entry.targetItemConfigKey}' '${entry.targetEntityId?.value}', but got failure ${entry.actionResultType?.name}"
            }
         } else if (entry.actionType == ActionType.PlaceGrowableInGrower) {
            if (entry.actionResultType == ActionResultType.Success) {
               val shared =
                  "a '${entry.actionItemConfigKey}' inventory item in a ${
                     getNameForEntityId(
                        entry.targetEntityId,
                        entry.targetItemConfigKey
                     )
                  }"

               if (entry.sourceEntityId == selfEntityId) {
                  "I planted $shared"
               } else {
                  "I saw ${getNameForEntityId(entry.sourceEntityId, entry.sourceItemConfigKey)} plant $shared"
               }
            } else {
               "I attempted to plant a '${entry.actionItemConfigKey}' inventory item in a ${
                  getNameForEntityId(
                     entry.targetEntityId,
                     entry.targetItemConfigKey
                  )
               }, but got failure ${entry.actionResultType?.name}"
            }
         } else if (entry.actionType == ActionType.Craft) {
            if (entry.actionResultType == ActionResultType.Success) {
               val shared =
                  "a '${entry.targetItemConfigKey}' item"

               if (entry.sourceEntityId == selfEntityId) {
                  "I crafted $shared"
               } else {
                  "I saw ${getNameForEntityId(entry.sourceEntityId, entry.sourceItemConfigKey)} craft $shared"
               }
            } else {
               "I attempted to craft a '${entry.targetItemConfigKey}' item, but got failure ${entry.actionResultType?.name}"
            }
         } else if (entry.actionType == ActionType.UseEquippedTool) {
            if (entry.actionResultType == ActionResultType.Success) {
               val spawnedItemsString = (entry.spawnedItems ?: listOf()).map {
                  "${it.itemConfigKey} (${it.entityId.value})"
               }.joinToString(", ")

               val shared =
                  "a ${entry.targetItemConfigKey} inventory item to create entity items: $spawnedItemsString"

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
            (entry.title ?: "no title?") + (entry.message?.let {
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

      addNewAutomaticShortTermMemories(this.automaticShortTermMemories, newAutomaticShortTermMemories)
      addNewAutomaticShortTermMemories(this.automaticShortTermMemoriesSinceLastPrompt, newAutomaticShortTermMemories)
   }

   fun ingestThought(
      selfInfo: SelfInfo,
      simulationTime: Double,
      thought: String
   ) {
      val initialLongTermMemory = LongTermMemory(
         createdAtLocation = selfInfo.entityInfoWrapper.entityInfo.location,
         importance = 100,
         createdTime = simulationTime,
         content = thought,
         id = 0
      )

      val initialAutomaticShortTermMemory = AutomaticShortTermMemory(
         time = simulationTime,
         summary = "I had the thought: \"" + initialLongTermMemory.content + "\"",
         forcePreviousActivity = true
      )

      this.automaticShortTermMemories.add(initialAutomaticShortTermMemory)
      this.automaticShortTermMemoriesSinceLastPrompt.add(initialAutomaticShortTermMemory)

      this.longTermMemories.add(initialLongTermMemory)
   }

   fun ingestInitialMemories(
      selfInfo: SelfInfo,
      simulationTime: Double
   ) {
      for (initialMemory in selfInfo.initialMemories) {
         this.ingestThought(
            selfInfo = selfInfo,
            thought = initialMemory,
            simulationTime = simulationTime
         )
      }
   }
}

