export class Vector2 {
  static zero = new Vector2(0, 0);
  static one = new Vector2(1, 1);

  constructor(public x: number, public y: number) {
  }

  static lerp(a: Vector2, b: Vector2, alpha: number): Vector2 {
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