package botfarm.game.agentintegration

import botfarm.common.PositionComponentData
import botfarm.common.resolvePosition
import botfarm.engine.simulation.Entity
import botfarm.engine.simulation.EntityComponent
import botfarm.game.GameSimulation
import botfarm.game.codeexecution.jsdata.AgentJavaScriptApi
import botfarm.game.components.CharacterComponentData
import botfarm.game.components.isDead
import botfarmshared.engine.apidata.EntityId
import botfarmshared.game.apidata.*
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source

class AgentSyncState(
   val simulation: GameSimulation,
   val entity: Entity,
   val agentType: String,
   val agentId: AgentId
) {
   val startedActionUniqueIds = mutableSetOf<String>()
   val actionResultsByActionUniqueId = mutableMapOf<String, ActionResult>()

   var mostRecentSyncInput: AgentSyncInput? = null

   // Prevent new agents from seeing events from before they are started
   var previousNewEventCheckSimulationTime = this.simulation.getCurrentSimulationTime()

   var mutableObservations = MutableObservations()

   var activeAction: Action? = null
   val pendingActions = mutableListOf<Action>()

   var activeScriptThread: Thread? = null

   var activeScriptToRun: ScriptToRun? = null
   var mostRecentCompletedScriptId: String? = null

   val javaScriptContext: Context =
      Context.newBuilder("js")
         .option("js.strict", "true")
         .build()

   val agentJavaScriptApi: AgentJavaScriptApi

   val agentTypeScriptInterfaceString = this::class.java.getResource("/scripted-agent-interfaces.ts")?.readText()
      ?: throw Exception("Scripted agent interfaces resource not found")

   init {
      val javaScriptBindings = this.javaScriptContext.getBindings("js")
      val agentJavaScriptApi = AgentJavaScriptApi(this)
      this.agentJavaScriptApi = agentJavaScriptApi
      javaScriptBindings.putMember("api", agentJavaScriptApi)

      val sourceName = "helpers"

      run {
         val runtimeSource =
            this::class.java.getResource("/scripted-agent-runtime.js")?.readText()
               ?: throw Exception("Scripted agent runtime JavaScript resource not found")

         val javaScriptSource = Source.newBuilder("js", runtimeSource, sourceName).build()
         this.javaScriptContext.eval(javaScriptSource)
      }
   }

   fun waitForMovement(
      positionComponent: EntityComponent<PositionComponentData>,
      movementResult: GameSimulation.MoveToResult.Success,
      callback: () -> Unit
   ) {
      val simulation = this.simulation

      simulation.queueCallback(
         condition = {
            val keyFrames = positionComponent.data.positionAnimation.keyFrames

            val result = positionComponent.entity.isDead ||
                    positionComponent.data.movementId != movementResult.movementId ||
                    keyFrames.isEmpty() ||
                    simulation.getCurrentSimulationTime() > keyFrames.last().time

            result
         }
      ) {
         callback()
      }
   }

   fun autoInteractWithEntity(
      targetEntity: Entity,
      callback: (AutoInteractType) -> Unit = {}
   ) {
      val simulation = this.simulation
      val entity = this.entity

      synchronized(simulation) {
         simulation.autoInteractWithEntity(
            entity = entity,
            targetEntity = targetEntity,
            callback = callback
         )
      }
   }

   fun speak(whatToSay: String, reason: String? = null) {
      val simulation = this.simulation

      synchronized(simulation) {
         this.mutableObservations.selfSpokenMessages.add(
            SelfSpokenMessage(
               message = whatToSay,
               reason = reason,
               location = this.entity.resolvePosition(),
               time = simulation.getCurrentSimulationTime()
            )
         )

         simulation.addCharacterMessage(
            entity = this.entity,
            message = whatToSay
         )
      }
   }

   fun recordThought(thought: String, reason: String? = null) {
      val simulation = this.simulation

      synchronized(simulation) {
         this.mutableObservations.selfThoughts.add(
            SelfThought(
               thought = thought,
               reason = reason,
               location = this.entity.resolvePosition(),
               time = simulation.getCurrentSimulationTime()
            )
         )

         simulation.broadcastAlertAsGameMessage(
            message = "Agent had thought: $thought"
         )
      }
   }
}