import {EntityComponentData, EntityId} from "../simulation/EntityData";
import {CompositeAnimation} from "./CompositeAnimation";

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

  eyes: CompositeAnimation | null
  hair: CompositeAnimation | null
}


export interface CharacterComponentData extends EntityComponentData {
  name: string
  recentSpokenMessages: SpokenMessage[]
  facialExpressionEmoji: string | null,
  pendingInteractionTargetEntityId: EntityId | null
  equippedItemConfigKey: string | null
  bodySelections: CharacterBodySelections
}

export interface ItemStack {
  itemConfigKey: string,
  amount: number
}


export interface Inventory {
  itemStacks: ItemStack[]
}

export interface InventoryComponentData extends EntityComponentData {
  inventory: Inventory
}
