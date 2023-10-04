package botfarmagent.game

import botfarmshared.engine.apidata.EntityId
import botfarmshared.engine.apidata.SimulationId
import botfarmshared.game.GameConstants
import botfarmshared.game.GameSimulationInfo
import botfarmshared.game.apidata.*
import botfarmshared.misc.Vector2

fun buildSyncInputs(
   syncId: String = "test-sync-1",
   agentType: String = "code"
) = AgentSyncInput(
   agentType = agentType,
   syncId = syncId,
   agentId = AgentId(value = "test-agent-1"),
   simulationId = SimulationId(value = "test-simulation-1"),
   simulationTime = 0.0,
   selfInfo = SelfInfo(
      entityInfo = EntityInfo(
         observedAtSimulationTime = 0.0,
         entityId = EntityId(value = "entity-1-self"),
         location = Vector2(x = 0.0, y = 0.0),
         itemInfo = null,
         damageableInfo = null,
         characterInfo = null,
         growerInfo = null
      ),
      corePersonality = "Friendly",
      initialMemories = listOf(),
      observationDistance = 0.0,
      inventoryInfo = InventoryInfo(itemStacks = listOf()),
      equippedItemConfigKey = null
   ),
   newObservations = Observations(
      spokenMessages = listOf(),
      selfSpokenMessages = listOf(),
      entitiesById = mapOf(),
      movementRecords = listOf(),
      actionOnEntityRecords = listOf(),
      actionOnInventoryItemActionRecords = listOf(),
      craftItemActionRecords = listOf(),
      activityStreamEntries = listOf(),
      actionResults = listOf(),
      startedActionUniqueIds = listOf()
   ),
   gameConstants = GameConstants,
   gameSimulationInfo = GameSimulationInfo(
      worldBounds = Vector2(
         x = 0.0,
         y = 0.0
      ),
      craftingRecipes = listOf()
   )
)