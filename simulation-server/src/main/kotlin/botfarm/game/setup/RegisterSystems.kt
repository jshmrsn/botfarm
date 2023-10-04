package botfarm.game.setup

import botfarm.game.systems.syncAgentCoroutineSystem
import botfarm.engine.simulation.Systems
import botfarm.game.components.AgentComponentData
import botfarm.game.systems.pendingInteractionTickSystem
import botfarm.game.systems.cleanupKilledEntitiesTickSystem
import botfarm.game.systems.updateGrowersTickSystem

val gameSystems = Systems()

fun registerGameSystems() {
   gameSystems.registerTickSystem2(::pendingInteractionTickSystem)
   gameSystems.registerTickSystem(::cleanupKilledEntitiesTickSystem)
   gameSystems.registerTickSystem(::updateGrowersTickSystem)
   gameSystems.registerCoroutineSystem<AgentComponentData>(::syncAgentCoroutineSystem)
}

