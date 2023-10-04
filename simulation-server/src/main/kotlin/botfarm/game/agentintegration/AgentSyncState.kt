package botfarm.game.agentintegration

import botfarm.game.GameSimulation

class AgentSyncState(
   val simulation: GameSimulation
) {
   // Prevent new agents from seeing events from before they are started
   var previousNewEventCheckTime = this.simulation.getCurrentSimulationTime()
   var mutableObservations = MutableObservations()
}