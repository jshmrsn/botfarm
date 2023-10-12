from pydantic import BaseModel
from typing import List, Optional, Dict
from enum import Enum
from .request import Vector2, EntityId

class PromptUsage(BaseModel):
    promptTokens: int = 0
    completionTokens: int = 0
    totalTokens: int = 0

class ModelUsagePricing(BaseModel):
    modelId: str
    costPer1kInput: float
    costPer1kOutput: float

class PromptUsageInfo(BaseModel):
    usage: PromptUsage
    modelUsagePricing: ModelUsagePricing

class AgentStatus(Enum):
    Idle = "Idle"
    UpdatingMemory = "UpdatingMemory"
    Running = "Running"
    Error = "Error"
    RateLimited = "RateLimited"


class RunningPromptInfo(BaseModel):
    prompt: str
    promptId: str
    description: str
    inputTokens: int


class PromptResultInfo(BaseModel):
    response: str
    promptId: str
    description: str
    completionTokens: int


class ScriptToRun(BaseModel):
    scriptId: str
    script: str


class ScriptExecutionError(BaseModel):
    error: str
    scriptId: str

class WalkAction(BaseModel):
    location: Vector2

class UseEquippedToolItem(BaseModel):
    pass

class ActionOnEntity(BaseModel):
    targetEntityId: EntityId

class DropInventoryItem(BaseModel):
    itemConfigKey: str
    stackIndex: Optional[int] = None
    amount: Optional[int] = None

class EquipInventoryItem(BaseModel):
    itemConfigKey: str
    stackIndex: Optional[int] = None

class CraftItemAction(BaseModel):
    itemConfigKey: str


class Action(BaseModel):
    actionUniqueId: str
    reason: Optional[str] = None
    walk: Optional[WalkAction] = None
    pickUpEntity: Optional[ActionOnEntity] = None
    useEquippedToolItemOnEntity: Optional[ActionOnEntity] = None
    useEquippedToolItem: Optional[UseEquippedToolItem] = None
    dropInventoryItem: Optional[DropInventoryItem] = None
    equipInventoryItem: Optional[EquipInventoryItem] = None
    craftItem: Optional[CraftItemAction] = None
    speak: Optional[str] = None
    facialExpressionEmoji: Optional[str] = None
    recordThought: Optional[str] = None

class AgentSyncOutput(BaseModel):
    agentStatus: Optional[AgentStatus] = None
    debugInfoByKey: Optional[Dict[str, str]] = None
    startedRunningPrompt: Optional[RunningPromptInfo] = None
    promptResult: Optional[PromptResultInfo] = None
    actions: Optional[List[Action]] = None
    scriptToRun: Optional[ScriptToRun] = None
    error: Optional[str] = None
    promptUsages: List[PromptUsageInfo] = []

class AgentSyncResponse(BaseModel):
    outputs: List[AgentSyncOutput]

