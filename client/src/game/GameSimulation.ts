import {Simulation} from "../simulation/Simulation";
import {ClientSimulationData} from "../simulation/EntityData";
import {Vector2} from "../misc/Vector2";
import {Entity} from "../simulation/Entity";
import {
  PositionComponent,
  resolveEntityPositionForCurrentTime,
  resolvePositionForCurrentTime
} from "../common/PositionComponentData";

export class GameSimulation extends Simulation {
  constructor(
    initialSimulationData: ClientSimulationData,
    onSimulationDataChanged: (newData: ClientSimulationData) => void,
    sendMessageImplementation: (type: string, data: any) => void
  ) {
    super(initialSimulationData, onSimulationDataChanged, sendMessageImplementation)
  }


  getNearestEntities(
    searchLocation: Vector2,
    maxDistance: number | null,
    filter: (entity: Entity) => boolean
  ) {
    const result = this.entities.filter(entity => {
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
}