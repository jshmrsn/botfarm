declare const api: any

declare class JsArray {
  getLength(): number

  get(index: number): any
}

function convertJsArray<T>(jsArray: JsArray): T[] {
  const result: T[] = []
  const length = jsArray.getLength()
  for (let i = 0; i < length; i++) {
    result.push(jsArray.get(i))
  }
  return result
}

interface JsVector2 {
  readonly x: number
  readonly y: number
}

class Vector2 {
  readonly x: number
  readonly y: number

  constructor(x: number, y: number) {
    this.x = x;
    this.y = y;
  }

  getMagnitude(): number {
    return Math.sqrt(this.x * this.x + this.y * this.y)
  }

  distanceTo(other: Vector2): number {
    return this.minusVector(other).getMagnitude()
  }

  minusVector(other: Vector2): Vector2 {
    return new Vector2(this.x - other.x, this.y - other.y)
  }

  plusVector(other: Vector2): Vector2 {
    return new Vector2(this.x + other.x, this.y + other.y)
  }
}

interface ItemOnGroundComponent {
  description: string
  name: string
  itemTypeId: string
  canBePickedUp: boolean
  amount: number
  pickup: () => void
}

interface JsEntity {
  location: JsVector2
  entityId: string,
  itemOnGround: ItemOnGroundComponent | null
}

function vector2(x: number, y: number): Vector2 {
  return api.makeVector2(x, y)
}

function getCurrentNearbyEntities() {
  return convertJsArray(api.getCurrentNearbyEntities())
}

function getCurrentInventoryItemStacks() {
  return convertJsArray<any>(api.getCurrentInventoryItemStacks())
}

function convertCraftingRecipe(it: any) {
  return {
    canCurrentlyAfford: it.canCurrentlyAfford,
    itemTypeId: it.itemTypeId,
    description: it.description,
    costEntries: convertJsArray(it.costEntries),
    craft: it.craft
  }
}

function getAllCraftingRecipes(): any[] {
  return convertJsArray<any>(api.getAllCraftingRecipes()).map(it => {
    return convertCraftingRecipe(it)
  })
}

const getTotalInventoryAmountForItemTypeId = api.getTotalInventoryAmountForItemTypeId
const recordThought = api.recordThought
const walkTo = api.walkTo
const pickUpItem = api.pickUpItem
const speak = api.speak
const setFacialExpressionEmoji = api.setFacialExpressionEmoji
const interactWithEntity = api.interactWithEntity
const getSelfEntity = api.getSelfEntity