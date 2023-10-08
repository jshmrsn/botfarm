

export function clampZeroOne(value: number) {
  return Math.min(1, Math.max(0, value))
}

export function lerp(a: number, b: number, alpha: number) {
  alpha = clampZeroOne(alpha)
  return a + alpha * (b - a)
}

export function signedPow(value: number, exponent: number) {
  return Math.pow(Math.abs(value), exponent) * Math.sign(value)
}

export class Vector2 {
  static zero = new Vector2(0, 0);
  static one = new Vector2(1, 1);

  constructor(public x: number, public y: number) {
  }

  static lerp(a: Vector2, b: Vector2, alpha: number): Vector2 {
    alpha = Math.min(1, Math.max(0, alpha))

    const newX = a.x + alpha * (b.x - a.x);
    const newY = a.y + alpha * (b.y - a.y);
    return new Vector2(newX, newY);
  }

  static timesScalar(a: Vector2, scalar: number): Vector2 {
    return new Vector2(a.x * scalar, a.y * scalar);
  }

  static timesVector(a: Vector2, b: Vector2): Vector2 {
    return new Vector2(a.x * b.x, a.y * b.y);
  }

  static signedPow(value: Vector2, exponent: number): Vector2 {
    return new Vector2(signedPow(value.x, exponent), signedPow(value.y, exponent));
  }

  static div(a: Vector2, scalar: number): Vector2 {
    return new Vector2(a.x / scalar, a.y / scalar);
  }

  static plus(a: Vector2, b: Vector2): Vector2 {
    return new Vector2(a.x + b.x, a.y + b.y);
  }

  static minus(a: Vector2, b: Vector2): Vector2 {
    return new Vector2(a.x - b.x, a.y - b.y);
  }

  static dot(a: Vector2, b: Vector2): number {
    return a.x * b.x + a.y * b.y;
  }

  static magnitude(a: Vector2): number {
    return Math.sqrt(a.x * a.x + a.y * a.y);
  }

  static normalize(a: Vector2): Vector2 {
    const mag = Math.max(this.magnitude(a), 0.00001);
    return new Vector2(a.x / mag, a.y / mag);
  }

  static distance(a: Vector2, b: Vector2): number {
    const dx = a.x - b.x;
    const dy = a.y - b.y;
    return Math.sqrt(dx * dx + dy * dy);
  }

  static angleBetween(a: Vector2, b: Vector2): number {
    const dot = this.dot(a, b);
    const mag1 = this.magnitude(a);
    const mag2 = this.magnitude(b);
    return Math.acos(dot / (mag1 * mag2)) * (180.0 / Math.PI);
  }

  toString(): string {
    return `Vector2(x=${this.x}, y=${this.y})`;
  }
}