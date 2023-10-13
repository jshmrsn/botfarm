package botfarmagent.game

import botfarmagent.misc.LanguageModelService
import botfarmshared.engine.apidata.EntityId
import botfarmshared.game.apidata.*

class AgentContext(
   val agentContainer: AgentContainer,
   val initialSyncInput: AgentSyncInput,
   val agentId: AgentId,
   val languageModelService: LanguageModelService,
   val agentType: String
)

abstract class Agent(
   val context: AgentContext
) {
   var mostRecentSentScriptId: String? = null
   val agentContainer = this.context.agentContainer
   val initialSyncInput = this.context.initialSyncInput
   var mostRecentSyncInput = this.context.initialSyncInput
      private set

   val agentId = this.context.agentId

   private val pendingInputs = mutableListOf<AgentSyncInput>()
   private val pendingOutputs = mutableListOf<AgentSyncOutput>()
   val receivedActionStartedIds = mutableSetOf<String>()
   val receivedActionResultById = mutableMapOf<String, ActionResult>()

   val entitiesById = mutableMapOf<EntityId, EntityInfoWrapper>()

   fun addPendingInput(input: AgentSyncInput) {
      synchronized(this) {
         this.pendingInputs.add(input)
      }
   }

   fun consumePendingInputs(): List<AgentSyncInput> {
      synchronized(this) {
         val copy = this.pendingInputs.toList()
         this.pendingInputs.clear()
         return copy
      }
   }

   fun consumePendingOutputs(): List<AgentSyncOutput> {
      return synchronized(this) {
         val copy = this.pendingOutputs.toList()
         this.pendingOutputs.clear()
         copy
      }
   }

   fun commonConsumeInput(
      input: AgentSyncInput
   ) {
      this.mostRecentSyncInput = input

      val newObservations = input.newObservations

      for (entityObservationEvent in newObservations.entityObservationEvents) {
         if (entityObservationEvent.newEntity != null) {
            val entityId = entityObservationEvent.newEntity.entityInfoWrapper.entityInfo.entityId
            this.entitiesById[entityId] = entityObservationEvent.newEntity.entityInfoWrapper
         } else if (entityObservationEvent.entityChanged != null) {
            val entityId = entityObservationEvent.entityChanged.entityInfoWrapper.entityInfo.entityId
            this.entitiesById[entityId] = entityObservationEvent.entityChanged.entityInfoWrapper
         } else if (entityObservationEvent.entityDestroyed != null) {
            this.entitiesById.remove(entityObservationEvent.entityDestroyed.entityId)
         }
      }

      newObservations.startedActionUniqueIds.forEach {
         println("Got action started: $it")
         this.receivedActionStartedIds.add(it)
      }

      newObservations.actionResults.forEach {
         println("Got action completed result: ${it.actionUniqueId}")
         this.receivedActionResultById[it.actionUniqueId] = it
      }
   }

   abstract fun consumeInput(
      input: AgentSyncInput
   )

   abstract suspend fun step(
      input: AgentSyncInput
   )

   fun addPendingOutput(output: AgentSyncOutput) {
      synchronized(this) {
         this.pendingOutputs.add(output)
      }
   }
}