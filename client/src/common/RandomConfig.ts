class Range {
  constructor(
    readonly min: number,
    readonly max: number
  ) {
  }
}

export class RandomConfig {
  readonly fixed: number | null;
  readonly range: Range | null;

  private constructor(fixed: number | null = null, range: Range | null = null) {
    this.fixed = fixed;
    this.range = range;
  }

  static zero = new RandomConfig(0.0, null);
  static one = new RandomConfig(1.0, null);

  static fixed(value: number): RandomConfig {
    return new RandomConfig(value, null);
  }

  static range(min: number, max: number): RandomConfig {
    return new RandomConfig(null, new Range(min, max));
  }

  static rollDouble(self: RandomConfig): number {
    if (self.fixed !== null) {
      return self.fixed;
    }

    if (self.range !== null) {
      return Math.random() * (self.range.max - self.range.min) + self.range.min;
    }

    throw new Error("Invalid RandomConfig");
  }

  static rollInt(self: RandomConfig): number {
    return Math.round(this.rollDouble(self));
  }
}