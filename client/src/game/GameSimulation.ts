import {Simulation} from "../simulation/Simulation";
import {ClientSimulationData} from "../simulation/EntityData";
import {Vector2} from "../misc/Vector2";
import {Entity} from "../simulation/Entity";
import {
  PositionComponent,
  resolveEntityPositionForCurrentTime,
  resolvePositionForCurrentTime
} from "../common/PositionComponentData";

import {GameSimulationConfig} from "./GameSimulationConfig";
import {getNearestEntitiesFromList} from "./utils";


export class GameSimulation extends Simulation {
  readonly gameSimulationConfig: GameSimulationConfig
  readonly worldBounds: Vector2;

  constructor(
    initialSimulationData: ClientSimulationData,
    onSimulationDataChanged: () => void,
    sendMessageImplementation: (type: string, data: any) => void
  ) {
    super(initialSimulationData, onSimulationDataChanged, sendMessageImplementation)

    const simulationConfig = this.getConfig<GameSimulationConfig>("game-simulation-config", "GameSimulationConfig")
    this.gameSimulationConfig = simulationConfig
    this.worldBounds = simulationConfig.worldBounds
  }

  sendSpawnRequest() {
    this.sendMessage("SpawnRequest", {})
  }

  sendDespawnRequest() {
    this.sendMessage("DespawnRequest", {})
  }

  getNearestEntities(
    searchLocation: Vector2,
    maxDistance: number | null,
    filter: (entity: Entity) => boolean
  ) {
    return getNearestEntitiesFromList(
      this.entities,
      searchLocation,
      maxDistance,
      filter
    )
  }
}