from turtle import isvisible
from xmlrpc.client import Boolean
from pydantic import BaseModel
from typing import List, Optional, Dict
from enum import Enum

class Vector2(BaseModel):
    x: float
    y: float

class GameConstants(BaseModel):
    distanceUnit: str
    peopleSize: float

EntityId = str
AgentId = str
SimulationId = str

class ActionType(Enum):
    UseToolToDamageEntity = "UseToolToDamageEntity"
    UseToolToKillEntity = "UseToolToKillEntity"
    PlaceGrowableInGrower = "PlaceGrowableInGrower"
    DropItem = "DropItem"
    PickUpItem = "PickUpItem"
    UseEquippedTool = "UseEquippedTool"
    EquipItem = "EquipItem"
    UnequipItem = "UnequipItem"
    Speak = "Speak"
    Thought = "Thought"
    Craft = "Craft"

class ActionResultType(Enum):
    Success = "Success"
    Failed = "Failed"
    NoValidAction = "NoValidAction"
    TargetNotAnItem = "TargetNotAnItem"
    UnexpectedItemInStack = "UnexpectedItemInStack"
    UnexpectedEquippedItem = "UnexpectedEquippedItem"
    StillTooFarAfterMoving = "StillTooFarAfterMoving"
    FailedToMoveForAction = "FailedToMoveForAction"
    TargetNoLongerExists = "TargetNoLongerExists"
    InvalidTargetEntityId = "InvalidTargetEntityId"
    TargetAlreadyDead = "TargetAlreadyDead"
    NoToolItemEquipped = "NoToolItemEquipped"
    Busy = "Busy"
    Obstructed = "Obstructed"
    NoActionForEquippedTool = "NoActionForEquippedTool"
    ItemNotInInventory = "ItemNotInInventory"
    UnexpectedAutoAction = "UnexpectedAutoAction"

class ItemCollectionEntry(BaseModel):
    itemConfigKey: str
    amount: int

class ItemCollection(BaseModel):
    entries: List[ItemCollectionEntry] = []

class ObservedSpokenMessage(BaseModel):
    entityId: EntityId
    messageId: str
    characterName: str
    message: str
    time: float
    speakerLocation: Vector2
    myLocation: Vector2

class MovementRecord(BaseModel):
    startedAtTime: float
    endPoint: Vector2
    startPoint: Vector2
    reason: Optional[str]

class ItemInfo(BaseModel):
    name: str
    description: str
    itemConfigKey: str

class CharacterEntityInfo(BaseModel):
    name: str
    gender: str
    skinColor: str
    age: int
    description: str
    equippedItemInfo: Optional[ItemInfo] = None
    hairColor: Optional[str] = None
    hairStyle: Optional[str] = None

class ItemEntityInfo(BaseModel):
    itemConfigKey: str
    itemName: str
    description: str
    canBePickedUp: bool
    amount: int

class DamageableEntityInfo(BaseModel):
    damageableByEquippedToolItemConfigKey: Optional[str] = None
    hp: int

class ActiveGrowthInfo(BaseModel):
    growableItemConfigKey: str
    growingIntoItemConfigKey: str
    startTime: float
    duration: float

class GrowerEntityInfo(BaseModel):
    activeGrowthInfo: Optional[ActiveGrowthInfo] = None
    canReceiveGrowableItemConfigKeys: List[str]

class EntityInfo(BaseModel):
    observedAtSimulationTime: float
    entityId: EntityId
    location: Vector2
    itemInfo: Optional[ItemEntityInfo] = None
    damageableInfo: Optional[DamageableEntityInfo] = None
    characterInfo: Optional[CharacterEntityInfo] = None
    growerInfo: Optional[GrowerEntityInfo] = None
    isStale: bool
    isVisible: bool

class EntityInfoWrapper(BaseModel):
    entityInfo: EntityInfo
    serializedAsJavaScript: str
    javaScriptVariableName: str

class SpawnedItemEntity(BaseModel):
    amount: int
    itemConfigKey: str
    entityId: EntityId

class ActivityStreamEntry(BaseModel):
    time: float
    title: Optional[str] = None
    message: Optional[str] = None
    longMessage: Optional[str] = None
    shouldReportToAi: bool
    agentReason: Optional[str] = None
    agentUniqueActionId: Optional[str] = None
    actionType: Optional[ActionType] = None
    actionResultType: Optional[ActionResultType] = None
    actionItemConfigKey: Optional[str] = None
    sourceItemConfigKey: Optional[str] = None
    sourceLocation: Optional[Vector2] = None
    sourceEntityId: Optional[EntityId] = None
    targetEntityId: Optional[EntityId] = None
    targetItemConfigKey: Optional[str] = None
    resultItemConfigKey: Optional[str] = None
    resultEntityId: Optional[EntityId] = None
    onlyShowForPerspectiveEntity: bool
    observedByEntityIds: Optional[List[EntityId]] = None
    spawnedItems: Optional[List[SpawnedItemEntity]] = None


class ScriptExecutionError(BaseModel):
    error: str
    scriptId: str

class ActionResult(BaseModel):
    actionUniqueId: str

class ObservedNewEntity(BaseModel):
    entityInfoWrapper: EntityInfoWrapper

class ObservedEntityChanged(BaseModel):
    entityInfoWrapper: EntityInfoWrapper

class ObservedEntityDestroyed(BaseModel):
    entityId: EntityId

class EntityObservationEvent(BaseModel):
    newEntity: Optional[ObservedNewEntity] = None
    entityChanged: Optional[ObservedEntityChanged] = None
    entityDestroyed: Optional[ObservedEntityDestroyed] = None

class Observations(BaseModel):
    entityObservationEvents: List[EntityObservationEvent]
    scriptExecutionErrors: List[ScriptExecutionError]
    movementRecords: List[MovementRecord]
    activityStreamEntries: List[ActivityStreamEntry]
    actionResults: List[ActionResult]
    startedActionUniqueIds: List[str]

class ItemStackInfo(BaseModel):
    amount: int
    itemConfigKey: str
    itemName: str
    itemDescription: str
    canBeEquipped: bool
    canBeDropped: bool
    isEquipped: bool
    spawnItemOnUseConfigKey: Optional[str] = None

class ItemStackInfoWrapper(BaseModel):
    itemStackInfo: ItemStackInfo
    serializedAsJavaScript: str
    javaScriptVariableName: str

class InventoryInfo(BaseModel):
    itemStacks: List[ItemStackInfoWrapper]

class SelfInfo(BaseModel):
    entityInfoWrapper: EntityInfoWrapper
    corePersonality: str
    initialMemories: List[str]
    observationRadius: float
    inventoryInfo: InventoryInfo
    equippedItemConfigKey: Optional[str] = None

class CraftingRecipeInfo(BaseModel):
    itemConfigKey: str
    itemName: str
    description: str
    cost: ItemCollection
    amount: int
    canCurrentlyAfford: bool

class CraftingRecipeInfoWrapper(BaseModel):
    craftingRecipeInfo: CraftingRecipeInfo
    serializedAsJavaScript: str
    javaScriptVariableName: str

class GameSimulationInfo(BaseModel):
    worldBounds: Vector2
    craftingRecipeInfoWrappers: List[CraftingRecipeInfoWrapper]

class AgentSyncInput(BaseModel):
    agentType: str
    syncId: str
    agentId: AgentId
    simulationId: SimulationId
    simulationTime: float
    selfInfo: SelfInfo
    newObservations: Observations
    gameConstants: GameConstants
    gameSimulationInfo: GameSimulationInfo
    agentTypeScriptInterfaceString: str
    mostRecentCompletedScriptId: Optional[str] = None

class AgentSyncRequest(BaseModel):
    input: AgentSyncInput
