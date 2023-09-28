package botfarm.simulationserver.game

import botfarm.simulationserver.simulation.EntityComponent
import botfarm.simulationserver.simulation.TickSystemContext

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

