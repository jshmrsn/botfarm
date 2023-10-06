package botfarm.game.systems

import botfarm.engine.simulation.EntityComponent
import botfarm.engine.simulation.TickSystemContext
import botfarm.game.GameSimulation
import botfarm.game.components.DamageableComponentData

fun cleanupKilledEntitiesTickSystem(
   context: TickSystemContext,
   killableComponent: EntityComponent<DamageableComponentData>
) {
   val killableComponentData = killableComponent.data

   if (killableComponentData.killedAtTime == null) {
      return
   }

   val simulation = context.simulation as GameSimulation

   val simulationTime = simulation.getCurrentSimulationTime()

   val timeSinceKilled = simulationTime - killableComponentData.killedAtTime

   if (timeSinceKilled > 2.0) {
      context.simulation.queueCallbackWithoutDelay {
         context.entity.destroy()
      }
   }
}

