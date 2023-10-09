package botfarmagent.game

import botfarmshared.engine.apidata.EntityId
import botfarmshared.engine.apidata.SimulationId
import botfarmshared.game.GameConstants
import botfarmshared.game.GameSimulationInfo
import botfarmshared.game.apidata.*
import botfarmshared.misc.Vector2

val x = EntityInfoWrapper
fun buildSyncInputs(
   syncId: String = "test-sync-1",
   agentType: String = "script"
) = AgentSyncInput(
   agentType = agentType,
   syncId = syncId,
   agentId = AgentId(value = "test-agent-1"),
   simulationId = SimulationId(value = "test-simulation-1"),
   simulationTime = 0.0,
   selfInfo = SelfInfo(
      entityInfoWrapper = EntityInfoWrapper(
         entityInfo = EntityInfo(
            observedAtSimulationTime = 0.0,
            entityId = EntityId(value = "entity-1-self"),
            location = Vector2(x = 0.0, y = 0.0),
            itemInfo = null,
            damageableInfo = null,
            characterInfo = null,
            growerInfo = null
         ),
         javaScriptVariableName = "test",
         serializedAsJavaScript = "?"
      ),
      corePersonality = "Friendly",
      initialMemories = listOf(),
      observationRadius = 0.0,
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
      startedActionUniqueIds = listOf(),
      selfThoughts = listOf(),
      scriptExecutionErrors = listOf()
   ),
   gameConstants = GameConstants,
   gameSimulationInfo = GameSimulationInfo(
      worldBounds = Vector2(
         x = 0.0,
         y = 0.0
      ),
      craftingRecipeInfoWrappers = listOf()
   ),
   agentTypeScriptInterfaceString = "",
   mostRecentCompletedScriptId = null
)