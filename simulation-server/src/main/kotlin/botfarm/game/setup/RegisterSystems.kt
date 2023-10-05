package botfarm.game.setup

import botfarm.game.systems.syncAgentCoroutineSystem
import botfarm.engine.simulation.Systems
import botfarm.game.components.AgentControlledComponentData
import botfarm.game.systems.pendingInteractionTickSystem
import botfarm.game.systems.cleanupKilledEntitiesTickSystem
import botfarm.game.systems.updateGrowersTickSystem

val gameSystems = Systems().also {
   it.registerTickSystem2(::pendingInteractionTickSystem)
   it.registerTickSystem(::cleanupKilledEntitiesTickSystem)
   it.registerTickSystem(::updateGrowersTickSystem)
   it.registerCoroutineSystem<AgentControlledComponentData>(::syncAgentCoroutineSystem)
}

