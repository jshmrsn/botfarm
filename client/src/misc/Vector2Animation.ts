import {Vector2} from "./Vector2";


export class Vector2KeyFrame {
  constructor(public value: Vector2, public time: number) {}
}

export class Vector2Animation {
  constructor(public keyFrames: Vector2KeyFrame[] = []) {
  }

  static resolve(self: Vector2Animation, time: number): Vector2 {
    let keyFrames = self.keyFrames;
    if (keyFrames.length === 0) {
      return Vector2.zero;
    }

    if (keyFrames.length === 1) {
      return keyFrames[0].value;
    }

    const last = keyFrames[keyFrames.length - 1];

    if (time >= last.time) {
      return last.value;
    }

    const first = keyFrames[0];

    if (time <= first.time) {
      return first.value;
    }

    for (let i = 0; i < keyFrames.length - 1; i++) {
      const currentElement = keyFrames[i];
      const nextElement = keyFrames[i + 1];

      if (time >= currentElement.time && time <= nextElement.time) {
        const span = nextElement.time - currentElement.time;
        const timeSinceCurrent = time - currentElement.time;

        if (span <= 0.0) {
          return nextElement.value;
        }

        const percent = timeSinceCurrent / span;
        return Vector2.lerp(currentElement.value, nextElement.value, percent);
      }
    }

    return last.value;
  }
}