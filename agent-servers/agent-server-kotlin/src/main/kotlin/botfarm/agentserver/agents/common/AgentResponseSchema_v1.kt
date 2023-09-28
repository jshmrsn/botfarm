package botfarm.agentserver.agents.common

import botfarm.apidata.*
import botfarm.misc.JsonArraySchema
import botfarm.misc.JsonNumberSchema
import botfarm.misc.JsonObjectSchema
import botfarm.misc.JsonStringSchema
import kotlinx.serialization.Serializable

object AgentResponseSchema_v1 {
   val reasonKey = "reason"
   val locationToWalkToAndReasonKey = "locationToWalkToAndReason"
   val destinationKey = "location"

   val functionName = "response"
   val iWantToSayKey = "iWantToSay"
   val facialExpressionEmojiKey = "facialExpressionEmoji"

   val useEquippedToolItemKey = "useEquippedToolItem"

   val newThoughtsKey = "newThoughts"

   val actionOnInventoryItemKey = "actionOnInventoryItem"
   val itemConfigKeyKey = "itemConfigKey"
   val amountKey = "amount"

   val actionOnEntityKey = "actionOnEntity"
   val targetEntityIdKey = "targetEntityId"
   val actionIdKey = "actionId"

   val craftItemKey = "craftItem"

   @Serializable
   class AgentResponseFunctionInputs(
      val locationToWalkToAndReason: ReasonToWalkToAndReason? = null,
      val actionOnEntity: ActionOnEntity? = null,
      val actionOnInventoryItem: ActionOnInventoryItem? = null,
      val craftItem: CraftItemAction? = null,
      val iWantToSay: String? = null,
      val facialExpressionEmoji: String? = null,
      val newThoughts: List<String>? = null,
      val useEquippedToolItem: UseEquippedToolItem? = null
   )

   val functionSchema = JsonObjectSchema(
      properties = mapOf(
         locationToWalkToAndReasonKey to JsonObjectSchema(
            properties = mapOf(
               destinationKey to JsonArraySchema(
                  description = "Represented as an array of two numbers for x and y coordinates.",
                  items = JsonNumberSchema()
               ),
               reasonKey to JsonStringSchema("Short reason of why you want to walk here")
            ),
            required = listOf(destinationKey)
         ),
         actionOnEntityKey to JsonObjectSchema(
            properties = mapOf(
               actionIdKey to JsonStringSchema("The actionId that you would like to take on the target entity"),
               targetEntityIdKey to JsonStringSchema("The entityId of the entity that would like to take action on"),
               reasonKey to JsonStringSchema("Reason why you are taking this action")
            ),
            required = listOf(targetEntityIdKey, actionIdKey)
         ),
         actionOnInventoryItemKey to JsonObjectSchema(
            properties = mapOf(
               itemConfigKeyKey to JsonStringSchema("The itemConfigKey of the item you would like take an action on"),
               actionIdKey to JsonStringSchema("The actionId you would like to take on this item"),
               reasonKey to JsonStringSchema("Reason why you are taking this action"),
               amountKey to JsonStringSchema("The amount of the items you would like to take this action on (not always relevant)")
            ),
            required = listOf(itemConfigKeyKey, actionIdKey)
         ),
         useEquippedToolItemKey to JsonObjectSchema(
            properties = mapOf(
               reasonKey to JsonStringSchema("Reason why you are taking this action")
            ),
            required = listOf()
         ),
         craftItemKey to JsonObjectSchema(
            properties = mapOf(
               itemConfigKeyKey to JsonStringSchema("The itemConfigKey of the item you would like take an action on"),
               reasonKey to JsonStringSchema("Reason why you are taking this action")
            ),
            required = listOf(itemConfigKeyKey)
         ),
         iWantToSayKey to JsonStringSchema("Use this input when you would like to talk out loud to interact with other people"),
         facialExpressionEmojiKey to JsonStringSchema("Provide a single emoji to represent your current mood as a facial expression"),
         newThoughtsKey to JsonArraySchema(
            description = "Thoughts, memories, or reflections that you would like to store for the long term, so you can remember them in future prompts from the intelligence system.",
            items = JsonStringSchema()
         )
      ),
      required = listOf(facialExpressionEmojiKey)
   )
}