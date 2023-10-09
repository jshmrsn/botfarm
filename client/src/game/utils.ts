import {Entity} from "../simulation/Entity";
import {Vector2} from "../misc/Vector2";
import {
  PositionComponent,
  resolveEntityPositionForCurrentTime,
  resolvePositionForCurrentTime
} from "../common/PositionComponentData";

export function getNearestEntitiesFromList(
  entities: Entity[],
  searchLocation: Vector2,
  maxDistance: number | null,
  filter: (entity: Entity) => boolean
) {
  const result = entities.filter(entity => {
    const positionComponent = PositionComponent.getOrNull(entity)

    if (positionComponent == null) {
      return false
    }

    const distance = Vector2.distance(resolvePositionForCurrentTime(positionComponent), searchLocation)

    if (maxDistance != null && distance > maxDistance) {
      return false
    }

    if (!filter(entity)) {
      return false
    }

    return true
  })

  result.sort((a, b) => {
    const distanceA = Vector2.distance(resolveEntityPositionForCurrentTime(a), searchLocation)
    const distanceB = Vector2.distance(resolveEntityPositionForCurrentTime(b), searchLocation)

    if (distanceA < distanceB) {
      return -1;
    }
    if (distanceA > distanceB) {
      return 1;
    }
    return 0;
  })

  return result
}