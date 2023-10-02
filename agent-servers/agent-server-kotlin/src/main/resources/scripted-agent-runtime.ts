declare const api: any

const speak = api.speak

declare class JsArray {
  getLength(): number
  get(index: number): any
}

function convertJsArray(jsArray: JsArray): any[] {
  const result: any[] = []
  const length = jsArray.getLength()
  for (let i = 0; i < length; i++) {
    result.push(jsArray.get(i))
  }
  return result
}

interface NativeVector2 {
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

function getCurrentNearbyEntities() {
  const res = api.getCurrentNearbyEntities()
  const raw = convertJsArray(res)

  return raw.map(it => {
    return {
      entityId: it.entityId,
      location: new Vector2(it.location.x, it.location.y)
    }
  })
}