import {Config, EntityComponentData} from "../simulation/EntityData";
import {CompositeAnimation} from "./CompositeAnimation";


interface ItemCollectionEntry {
  itemConfigKey: string
  amount: number
}

export interface ItemCollection {
  entries: ItemCollectionEntry[]
}
export interface ItemConfig extends Config {
  name: string
  description: string
  spriteConfigKey: string
  iconUrl: string
  canBePickedUp: boolean
  canBeDropped: boolean
  maxHp: number
  canBeEquipped: boolean
  canBeDamagedByItem: string | null
  spawnItemOnDestruction: string | null
  blocksPathfinding: boolean,
  craftingCost: ItemCollection | null
  craftingAmount: number
  equippedCompositeAnimation: CompositeAnimation | null
}

export interface ItemComponentData extends EntityComponentData {
  hp: number
  itemConfigKey: string
  amount: number
}
