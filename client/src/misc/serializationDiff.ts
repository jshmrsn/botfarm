export interface SerializationDiff {
  value: any | null
  index: number | null
  key: string | null
  size: number | null
  items: SerializationDiff[]
  fields: SerializationDiff[]
  remove: string[] | null
}

export function deserializeDiff(
  previous: any | null,
  diff: SerializationDiff | null
): any | null {
  if (diff == null) {
    return null
  }

  if (diff.value != null) {
    return diff.value
  }

  if (diff.items != null || diff.size != null) {
    if (previous == null) {
      throw new Error("Expected non-null previous value for updated array: " + JSON.stringify(diff))
    }

    const previousArray: any[] = previous

    let newArray: any[];
    if (diff.size != null) {
      newArray = previousArray.slice(0, diff.size)
    } else {
      newArray = [...previousArray]
    }

    if (diff.items != null) {
      for (let item of diff.items) {
        if (item.index == null) {
          throw new Error("Unexpected null index for item inside array diff")
        }

        const previousValue = previousArray[item.index]
        newArray[item.index] = deserializeDiff(previousValue, item)
      }
    }

    return newArray
  } else if (diff.fields != null || diff.remove != null) {
    if (previous == null) {
      throw new Error("Expected non-null previous value for updated object: " + JSON.stringify(diff))
    }

    const newObject = Object.assign({}, previous)

    if (diff.fields != null) {
      for (let field of diff.fields) {
        if (field.key == null) {
          throw new Error("Unexpected null key for item inside object diff")
        }

        const previousFieldValue = previous[field.key]

        newObject[field.key] = deserializeDiff(
          previousFieldValue,
          field
        )
      }
    }

    if (diff.remove != null) {
      for (let keyToRemove of diff.remove) {
        delete newObject[keyToRemove]
      }
    }

    return newObject
  } else {
    return null
  }
}


