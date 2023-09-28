package botfarm.simulationserver.game

import botfarm.simulationserver.game.ai.agentCoroutineSystem
import botfarm.simulationserver.simulation.Systems

fun registerSystems() {
   Systems.default.registerTickSystem2(::pendingInteractionTickSystem)
   Systems.default.registerTickSystem(::cleanupKilledEntitiesTickSystem)
   Systems.default.registerTickSystem(::updateGrowersTickSystem)
   Systems.default.registerCoroutineSystem<AgentComponentData>(::agentCoroutineSystem)
}
