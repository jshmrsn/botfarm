package botfarm.game.ai
import botfarm.common.PositionComponentData
import botfarmshared.misc.Vector2
import org.graalvm.polyglot.HostAccess

class AgentApiJavaScriptBindings(
   val agentAPI: AgentApi
) {
   private val state = this.agentAPI.state

   @HostAccess.Export
   fun speak(message: String) {
      this.agentAPI.speak(message)
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
      val state = this.agentAPI.state
      val simulation = this.agentAPI.simulation
      val entity = this.agentAPI.entity
      val endPoint = Vector2(2000.0, 2000.0) + Vector2.randomSignedXY(1000.0)


      println("a: " + valueA)
      println("b: " + valueB)
      this.agentAPI.walk(
         endPoint = endPoint,
         reason = "AI"
      )

      while (true) {
         val shouldBreak = synchronized(simulation) {
            val positionComponent = entity.getComponent<PositionComponentData>()
            val keyFrames = positionComponent.data.positionAnimation.keyFrames

            val isDoneMoving = keyFrames.isEmpty() ||
                    simulation.getCurrentSimulationTime() > keyFrames.last().time

            if (isDoneMoving) {
               true
            } else {
               false
            }
         }

         if (shouldBreak) {
            break
         } else {
            Thread.sleep(100)
         }
      }
   }
}

