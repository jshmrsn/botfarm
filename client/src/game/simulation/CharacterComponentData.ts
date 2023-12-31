import {EntityComponentData, EntityId} from "../../engine/simulation/EntityData";
import {CompositeAnimationSelection} from "./CompositeAnimationSelection";
import {Vector2} from "../../misc/Vector2";
import {EntityComponentGetter} from "../../engine/simulation/EntityComponentGetter";

export interface SpokenMessage {
  sentSimulationTime: number
  message: string
}

export interface CharacterBodySelections {
  skinColor: string
  bodyType: string

  head:  string
  body:  string
  nose: string | null
  wrinkles: string | null

  eyes: CompositeAnimationSelection | null
  hair: CompositeAnimationSelection | null
}

export type ActionType = "UseToolToDamageEntity" |
  "UseToolToKillEntity" |
  "PlaceGrowableInGrower" |
  "DropItem" |
  "PickUpItem" |
  "UseEquippedTool" |
  "EquipItem" |
  "UnequipItem" |
  "Speak" |
  "Thought" |
  "Craft"

export class ActionTypes {
  static readonly UseToolToKillEntity: ActionType = "UseToolToKillEntity"
  static readonly UseToolToDamageEntity: ActionType = "UseToolToDamageEntity"
  static readonly PlaceGrowableInGrower: ActionType = "PlaceGrowableInGrower"
  static readonly DropItem: ActionType = "DropItem"
  static readonly UseEquippedTool: ActionType = "UseEquippedTool"
  static readonly PickUpItem: ActionType = "PickUpItem"
  static readonly EquipItem: ActionType = "EquipItem"
  static readonly UnequipItem: ActionType = "UnequipItem"
  static readonly Speak: ActionType = "Speak"
  static readonly Thought: ActionType = "Thought"
  static readonly Craft: ActionType = "Craft"
}

export interface PerformedAction {
  performedAtLocation: Vector2
  startedAtSimulationTime: number
  actionIndex: number
  actionType: ActionType
  targetEntityId: EntityId | null
  duration: number
}

export interface UseEquippedToolItemRequest {
  expectedItemConfigKey: string
}

export interface CharacterComponentData extends EntityComponentData {
  name: string
  recentSpokenMessages: SpokenMessage[]
  facialExpressionEmoji: string | null,
  pendingInteractionTargetEntityId: EntityId | null
  pendingUseEquippedToolItemRequest: UseEquippedToolItemRequest | null
  bodySelections: CharacterBodySelections,
  performedAction: PerformedAction | null,
  observationRadius: number
}

export const CharacterComponent = new EntityComponentGetter<CharacterComponentData>("CharacterComponentData")

export interface ItemStack {
  itemConfigKey: string,
  amount: number
  isEquipped: boolean
}

export interface Inventory {
  itemStacks: ItemStack[]
}

export interface InventoryComponentData extends EntityComponentData {
  inventory: Inventory
}

export const InventoryComponent = new EntityComponentGetter<InventoryComponentData>("InventoryComponentData")
