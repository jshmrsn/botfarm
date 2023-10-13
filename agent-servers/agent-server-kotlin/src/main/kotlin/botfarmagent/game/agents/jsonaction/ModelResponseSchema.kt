package botfarmagent.game.agents.jsonaction

import botfarmshared.engine.apidata.EntityId
import botfarmshared.game.apidata.*
import botfarmshared.misc.JsonArraySchema
import botfarmshared.misc.JsonNumberSchema
import botfarmshared.misc.JsonObjectSchema
import botfarmshared.misc.JsonStringSchema
import kotlinx.serialization.Serializable

object ModelResponseSchema {
   val actionsKey = "actions"

   val reasonKey = "reason"

   val walkTo = "walkTo"

   val functionName = "response"
   val speak = "speak"
   val facialExpressionEmojiKey = "facialExpressionEmoji"

   val useEquippedToolItemKey = "useEquippedToolItem"

   val recordThoughtKey = "recordThought"

   val actionOnInventoryItemKey = "actionOnInventoryItem"
   val itemConfigKeyKey = "itemConfigKey"
   val amountKey = "amount"

   val actionOnEntityKey = "actionOnEntity"
   val targetEntityIdKey = "targetEntityId"
   val actionIdKey = "actionId"

   val craftItemKey = "craftItem"

   @Serializable
   class GenericActionOnEntity(
      val targetEntityId: EntityId,
      val actionId: String
   )

   @Serializable
   class GenericActionOnInventoryItem(
      val actionId: String,
      val itemConfigKey: String,
      val stackIndex: Int? = null,
      val amount: Int? = null
   )

   @Serializable
   class AgentResponseAction(
      val reason: String? = null,
      val walkTo: List<Double>? = null,
      val actionOnEntity: GenericActionOnEntity? = null,
      val actionOnInventoryItem: GenericActionOnInventoryItem? = null,
      val craftItem: CraftItemAction? = null,
      val speak: String? = null,
      val facialExpressionEmoji: String? = null,
      val recordThought: String? = null,
      val useEquippedToolItem: UseEquippedToolItem? = null
   )

   @Serializable
   class AgentResponseFunctionInputs(
      val actions: List<AgentResponseAction>? = null
   )

   val actionSchema = JsonObjectSchema(
      description = "A single action to perform. You can provide a reason along with exactly one other top-level to perform different types of actions.",
      properties = mapOf(
         reasonKey to JsonStringSchema("Reason why you are taking this action"),
         walkTo to JsonArraySchema(
            description = "Represented as an array of two numbers for x and y coordinates.",
            items = JsonNumberSchema()
         ),
         actionOnEntityKey to JsonObjectSchema(
            properties = mapOf(
               actionIdKey to JsonStringSchema("The actionId that you would like to take on the target entity"),
               targetEntityIdKey to JsonStringSchema("The entityId of the entity that would like to take action on")
            ),
            required = listOf(targetEntityIdKey, actionIdKey)
         ),
         actionOnInventoryItemKey to JsonObjectSchema(
            properties = mapOf(
               itemConfigKeyKey to JsonStringSchema("The itemConfigKey of the item you would like take an action on"),
               actionIdKey to JsonStringSchema("The actionId you would like to take on this item"),
               amountKey to JsonStringSchema("The amount of the items you would like to take this action on (not always relevant)")
            ),
            required = listOf(itemConfigKeyKey, actionIdKey)
         ),
         useEquippedToolItemKey to JsonObjectSchema(
            properties = mapOf(),
            description = "Provided empty non-null object to use this action",
            required = listOf()
         ),
         craftItemKey to JsonStringSchema(
            description = "The itemConfigKey of the item you would like take an action on"
         ),
         speak to JsonStringSchema("Use this input when you would like to talk out loud to interact with other people"),
         facialExpressionEmojiKey to JsonStringSchema("Provide a single emoji to represent your current mood as a facial expression"),
         recordThoughtKey to JsonStringSchema(
            description = "Record a thoughts, memory, or reflection that you would like to store for the long term, so you can remember them in future prompts from the intelligence system."
         )
      ),
      required = listOf(facialExpressionEmojiKey)
   )

   val functionSchema = JsonObjectSchema(
      properties = mapOf(
         actionsKey to JsonArraySchema(
            description = "Actions to perform.",
            items = this.actionSchema
         )
      ),
      required = listOf(facialExpressionEmojiKey)
   )
}