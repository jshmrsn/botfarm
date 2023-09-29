package botfarm.game.systems

import botfarm.engine.simulation.EntityComponent
import botfarm.engine.simulation.TickSystemContext
import botfarm.game.GameSimulation
import botfarm.game.components.KillableComponentData

fun cleanupKilledEntitiesTickSystem(
   context: TickSystemContext,
   killableComponent: EntityComponent<KillableComponentData>
) {
   val simulation = context.simulation as GameSimulation

   val simulationTime = simulation.getCurrentSimulationTime()

   val killableComponentData = killableComponent.data

   if (killableComponentData.killedAtTime != null &&
      simulationTime - killableComponentData.killedAtTime > 2.0
   ) {
      context.simulation.queueCallbackWithoutDelay {
         context.entity.destroy()
      }
   }
}

