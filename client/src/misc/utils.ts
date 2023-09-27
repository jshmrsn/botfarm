// joshr: from https://stackoverflow.com/a/27747377

// joshr: Doesn't work on mobile for react-native
// if (window.crypto == null) {
//
//   const arr = new Uint8Array((len || 40) / 2)
//   window.crypto.getRandomValues(arr)
//   return Array.from(arr, dec2hex).join('')
// }

export function throwExpression(error: Error): never {
  throw error;
}

export function throwError(errorMessage: string): never {
  throw new Error(errorMessage);
}

function makeIdWithoutCrypto(length: number): string {
  let result = '';
  const characters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  const charactersLength = characters.length;
  let counter = 0;
  while (counter < length) {
    result += characters.charAt(Math.floor(Math.random() * charactersLength));
    counter += 1;
  }
  return result;
}


export function generateId(length?: number): string {
  if (length === undefined) {
    length = 12
  }

  return makeIdWithoutCrypto(length)
}

export function dec2hex(dec: number): string {
  return dec.toString(16).padStart(2, "0")
}

export function shallowCopyInstance<T extends {}>(instance: T, properties?: any): T {
  const copy: T = Object.assign({}, instance)
  Object.setPrototypeOf(copy, Object.getPrototypeOf(instance))

  if (properties !== undefined) {
    Object.assign(copy, properties)
  }

  return copy
}


export function getUnixTimeSeconds(): number {
  return new Date().getTime() / 1000
}

