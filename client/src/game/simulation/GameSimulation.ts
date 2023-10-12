import {ClientId, Simulation, SimulationContext} from "../../engine/simulation/Simulation";
import {ClientSimulationData, EntityId} from "../../engine/simulation/EntityData";
import {Vector2} from "../../misc/Vector2";
import {Entity} from "../../engine/simulation/Entity";

import {GameSimulationConfig} from "./GameSimulationConfig";
import {getNearestEntitiesFromList} from "../../common/utils";
import {GameClientStateComponentData} from "../../common/GameClientStateComponentData";


export class GameSimulation extends Simulation {
  readonly gameSimulationConfig: GameSimulationConfig
  readonly worldBounds: Vector2;

  constructor(
    context: SimulationContext
  ) {
    super(context)

    const simulationConfig = this.getConfig<GameSimulationConfig>("game-simulation-config", "GameSimulationConfig")
    this.gameSimulationConfig = simulationConfig
    this.worldBounds = simulationConfig.worldBounds
  }

  getGameClientState(): GameClientStateComponentData {
    if (this.isReplay) {
      return {
        type: "GameClientStateComponentData",
        shouldSpectateByDefault: true,
        perspectiveEntityIdOverride: this.replayPerspectiveEntityIdOverride
      }
    } else {
      const componentData = this.getEntityOrNull("game-client-state:" + this.clientId)?.getComponentDataOrNull<GameClientStateComponentData>("GameClientStateComponentData")
      return componentData ?? {
        type: "GameClientStateComponentData",
        shouldSpectateByDefault: false,
        perspectiveEntityIdOverride: null
      }
    }
  }

  private replayPerspectiveEntityIdOverride: string | null = null

  setPerspectiveEntityIdOverride(perspectiveEntityIdOverride: EntityId | null) {
    if (this.isReplay) {
      this.replayPerspectiveEntityIdOverride = perspectiveEntityIdOverride
      this.onSimulationDataChanged()
    } else {
      this.sendMessage("SetPerspectiveEntityIdOverrideRequest", {
        perspectiveEntityIdOverride: perspectiveEntityIdOverride
      })
    }
  }

  get perspectiveEntityIdOverride(): EntityId | null {
    return this.getGameClientState().perspectiveEntityIdOverride
  }

  setShouldSpectateByDefault(shouldSpectateByDefault: boolean) {
    if (!this.isReplay) {
      this.sendMessage("SetShouldSpectateByDefaultRequest", {
        shouldSpectateByDefault: shouldSpectateByDefault
      })
    }
  }

  get shouldSpectateByDefault(): boolean {
    return this.getGameClientState().shouldSpectateByDefault
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