// import {ItemOnGroundComponent} from "./scripted-agent-interfaces"
const speak = api.speak;
function convertJsArray(jsArray) {
    const result = [];
    const length = jsArray.getLength();
    for (let i = 0; i < length; i++) {
        result.push(jsArray.get(i));
    }
    return result;
}
class Vector2 {
    constructor(x, y) {
        this.x = x;
        this.y = y;
    }
    getMagnitude() {
        return Math.sqrt(this.x * this.x + this.y * this.y);
    }
    distanceTo(other) {
        return this.minusVector(other).getMagnitude();
    }
    minusVector(other) {
        return new Vector2(this.x - other.x, this.y - other.y);
    }
    plusVector(other) {
        return new Vector2(this.x + other.x, this.y + other.y);
    }
}
function vector2(x, y) {
    return api.makeVector2(x, y);
}
function getCurrentNearbyEntities() {
    return convertJsArray(api.getCurrentNearbyEntities());
}
function getCurrentInventoryItemStacks() {
    return convertJsArray(api.getCurrentInventoryItemStacks());
}
function convertCraftingRecipe(it) {
    return {
        canCurrentlyAfford: it.canCurrentlyAfford,
        itemTypeId: it.itemTypeId,
        description: it.description,
        costEntries: convertJsArray(it.costEntries),
        craft: it.craft
    };
}
function getAllCraftingRecipes() {
    return convertJsArray(api.getAllCraftingRecipes()).map(it => {
        return convertCraftingRecipe(it);
    });
}
const getTotalInventoryAmountForItemTypeId = api.getTotalInventoryAmountForItemTypeId;
const recordThought = api.recordThought;
const walkTo = api.walkTo;
