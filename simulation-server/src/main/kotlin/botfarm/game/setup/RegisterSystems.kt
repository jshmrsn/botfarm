package botfarm.game.setup

import botfarm.game.systems.agentCoroutineSystem
import botfarm.engine.simulation.Systems
import botfarm.game.components.AgentComponentData
import botfarm.game.systems.pendingInteractionTickSystem
import botfarm.game.systems.cleanupKilledEntitiesTickSystem
import botfarm.game.systems.updateGrowersTickSystem

fun registerGameSystems() {
   Systems.default.registerTickSystem2(::pendingInteractionTickSystem)
   Systems.default.registerTickSystem(::cleanupKilledEntitiesTickSystem)
   Systems.default.registerTickSystem(::updateGrowersTickSystem)
   Systems.default.registerCoroutineSystem<AgentComponentData>(::agentCoroutineSystem)
}

