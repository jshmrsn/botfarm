
interface Vector2 {
  readonly x: number
  readonly y: number
  getMagnitude(): number
  distanceTo(other: Vector2): number
  minus(other: Vector2): number
  plus(other: Vector2): number
}

declare function vector2(x: number, y: number): Vector2;

interface Entity {
  readonly entityId: string
  readonly location: Vector2

  readonly character: CharacterComponent | null
  readonly damageable: DamageableComponent | null
  readonly itemOnGround: ItemOnGroundComponent | null
  readonly grower: GrowerComponent | null
}

interface ActiveGrowth {
  growingItemTypeId: string
  growingIntoItemTypeId: string
  duration: number
  startTime: number
}

interface GrowerComponent {
  readonly activeGrowth: ActiveGrowth | null
  startGrowingEquippedItem()
}

interface ItemOnGroundComponent {
  description: string
  name: string
  itemTypeId: string
  canBePickedUp: boolean
  amount: number
  pickup()
}

interface DamageableComponent {
  hp: number
  canBeDamagedByEquippedItemTypeId: string | null
  attackWithEquippedItem()
}

interface CharacterComponent {
  name: string
}

interface InventoryItemStack {
  readonly name: string
  readonly description: string
  readonly itemTypeId: string
  readonly isEquipped: boolean
  readonly canBeEquipped: boolean
  readonly amount: number
  readonly stackIndex: number
  readonly spawnItemOnUseItemTypeId: string | null
  readonly canBeUsedWhenEquipped: boolean

  dropAll()
  dropAmount(amount: number)
  equip()
  use() // NOTE: Must be equipped before using, and requires canBeUsedWhenEquipped
}

// Functions to query current world state
declare function getCurrentInventoryItemStacks(): InventoryItemStack[];
declare function getCurrentNearbyEntities(): Entity[];

// General actions you can take
declare function walkTo(location: Vector2);
declare function speak(wordsToSay: string);
declare function recordThought(thought: string);
