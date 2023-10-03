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
    const res = api.getCurrentNearbyEntities();
    const raw = convertJsArray(res);
    return raw;
}
function getCurrentInventory() {
    return api.getCurrentInventory();
}
const recordThought = api.recordThought;
const walkTo = api.walkTo;
