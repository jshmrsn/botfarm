package botfarmagent.game.agents.scripted

import botfarmshared.game.apidata.AgentStepResult
import botfarmshared.game.apidata.Interactions
import botfarmshared.misc.Vector2
import org.graalvm.polyglot.HostAccess

class JsVector2(
   value: Vector2
) {
   @HostAccess.Export
   @JvmField
   val x = value.x

   @HostAccess.Export
   @JvmField
   val y = value.y
}

class NearbyEntity(
   @HostAccess.Export
   @JvmField
   val location: JsVector2,
   @HostAccess.Export
   @JvmField
   val entityId: String
)

fun <T> List<T>.toJs(): JsArray<T> {
   return JsArray(this)
}

fun Vector2.toJs(): JsVector2 {
   return JsVector2(this)
}


class JsArray<T>(
   val values: List<T>
) {
   @HostAccess.Export
   fun getLength(): Int = this.values.size

   @HostAccess.Export
   fun get(index: Int): T {
      return this.values.get(index)
   }
}

class AgentJavaScriptApi(
   val agent: ScriptedAgent
) {
   @HostAccess.Export
   fun speak(message: String) {
      synchronized(this.agent) {
         this.agent.addPendingResult(AgentStepResult(
            interactions = Interactions(
               speak = message
            )
         ))
      }
   }

   @HostAccess.Export
   fun getCurrentNearbyEntities(): JsArray<NearbyEntity> {
      return this.agent.mostRecentInputs.newObservations.entitiesById.values.map {
         NearbyEntity(
            entityId = it.entityId.value,
            location = it.location.toJs()
         )
      }.toList().toJs()
   }

   @HostAccess.Export
   fun sleep(millis: Long) {
      Thread.sleep(millis)
   }

   @HostAccess.Export
   fun pickUp() {

   }

   @HostAccess.Export
   fun walk(
      valueA: Array<Int>,
      valueB: Map<String, Int>
   ) {
//      val state = this.agentAPI.state
//      val simulation = this.agentAPI.simulation
//      val entity = this.agentAPI.entity
//      val endPoint = Vector2(2000.0, 2000.0) + Vector2.randomSignedXY(1000.0)
//
//
//      println("a: " + valueA)
//      println("b: " + valueB)
//      this.agentAPI.walk(
//         endPoint = endPoint,
//         reason = "AI"
//      )
//
//      while (true) {
//         val shouldBreak = synchronized(simulation) {
//            val positionComponent = entity.getComponent<PositionComponentData>()
//            val keyFrames = positionComponent.data.positionAnimation.keyFrames
//
//            val isDoneMoving = keyFrames.isEmpty() ||
//                    simulation.getCurrentSimulationTime() > keyFrames.last().time
//
//            if (isDoneMoving) {
//               true
//            } else {
//               false
//            }
//         }
//
//         if (shouldBreak) {
//            break
//         } else {
//            Thread.sleep(100)
//         }
//      }
   }
}