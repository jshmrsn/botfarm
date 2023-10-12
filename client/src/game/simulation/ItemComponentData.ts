import {Config, EntityComponentData} from "../../engine/simulation/EntityData";
import {CompositeAnimationSelection} from "./CompositeAnimationSelection";
import {RandomConfig} from "../../common/RandomConfig";
import {EntityComponentGetter} from "../../engine/simulation/EntityComponentGetter";


export interface RandomItemQuantity {
  stackCount: RandomConfig;
  amountPerStack: RandomConfig;
}

export interface GrowerConfig {
  canReceiveGrowableItemConfigKeys: string[];
}

export interface GrowableConfig {
  growingSpriteConfigKey: string;
  progressAnimationNames: string[];
  timeToGrow: number;
  growsIntoItemConfigKey: string;
  growsIntoItemQuantity: RandomItemQuantity;
}

export interface SpawnItemOnUseConfig {
  spawnItemConfigKey: string;
  quantity: RandomItemQuantity;
}

export interface SpawnItemOnDestructionConfig {
  spawnItemConfigKey: string;
  quantity: RandomItemQuantity;
}

export interface CraftableConfig {
  craftingCost: ItemCollection;
  craftingAmount: number;
}


export interface ItemCollectionEntry {
  itemConfigKey: string
  amount: number
}

export interface ItemCollection {
  entries: ItemCollectionEntry[]
}

export type EquipmentSlot = "Tool" | "Chest" | "Pants" | "Shoes" | "Hat"

export class EquipmentSlots {
  static readonly Tool: EquipmentSlot = "Tool"
  static readonly Chest: EquipmentSlot = "Chest"
  static readonly Pants: EquipmentSlot = "Pants"
  static readonly Shoes: EquipmentSlot = "Shoes"
  static readonly Hat: EquipmentSlot = "Hat"
}

export interface EquippableConfig {
  equipmentSlot: EquipmentSlot
  equippedCompositeAnimation?: CompositeAnimationSelection | null;
}

export interface DamageableConfig {
  maxHp: number
  damageableByEquippedToolItemConfigKey: string | null
}

export interface StorableConfig {
  maxStackSize: number | null
  canBeDropped: boolean
}

export interface ItemConfig extends Config {
  name: string
  description: string
  spriteConfigKey: string
  iconUrl: string
  useCustomAnimationBaseName: string | null,
  equippableConfig: EquippableConfig | null
  damageableConfig: DamageableConfig | null
  blocksPathfinding: boolean,
  blocksPlacement: boolean,
  craftableConfig: CraftableConfig | null;
  storableConfig: StorableConfig | null
  spawnItemOnDestructionConfig: SpawnItemOnDestructionConfig | null; // tree spawns wood when cut down
  growerConfig: GrowerConfig | null; // farm plots receive and grow carrot seeds
  growableConfig: GrowableConfig | null; // carrot seeds grow into carrots
  spawnItemOnUseConfig: SpawnItemOnUseConfig | null; // hoe spawns farm plots
}

export interface ItemComponentData extends EntityComponentData {
  itemConfigKey: string
  amount: number
}

export const ItemComponent = new EntityComponentGetter<ItemComponentData>("ItemComponentData")

export interface DamageableComponentData extends EntityComponentData {
  hp: number
  killedAtTime: number | null
}

export const KillableComponent = new EntityComponentGetter<DamageableComponentData>("DamageableComponentData")

export interface ActiveGrowth {
  startTime: number,
  itemConfigKey: string
}

export interface GrowerComponentData extends EntityComponentData {
  activeGrowth: ActiveGrowth | null
}

export const GrowerComponent = new EntityComponentGetter<GrowerComponentData>("GrowerComponentData")
