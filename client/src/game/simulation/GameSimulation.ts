import {ClientId, Simulation, SimulationContext} from "../../engine/simulation/Simulation";
import {ClientSimulationData, EntityId} from "../../engine/simulation/EntityData";
import {Vector2} from "../../misc/Vector2";
import {Entity} from "../../engine/simulation/Entity";

import {GameSimulationConfig} from "./GameSimulationConfig";
import {getNearestEntitiesFromList} from "../../common/utils";
import {GameClientStateComponentData} from "../../common/GameClientStateComponentData";
import {ItemComponent, ItemComponentData, ItemConfig} from "./ItemComponentData";
import {resolveEntityPositionForCurrentTime} from "../../common/PositionComponentData";


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

  getSignedEdgeDistanceFromEntity(location: Vector2, targetEntity: Entity): Vector2 {
    const itemComponent = ItemComponent.get(targetEntity)
    const itemConfig = itemComponent != null
      ? this.getConfig<ItemConfig>(itemComponent.data.itemConfigKey, "ItemConfig")
      : null

    const targetLocation = resolveEntityPositionForCurrentTime(targetEntity)

    const defaultTargetRadius = 15.0

    if (itemConfig !== null) {
      const collisionConfig = itemConfig.collisionConfig;

      if (collisionConfig !== null) {
        const targetCollisionCenter = Vector2.plus(targetLocation, collisionConfig.collisionOffset)

        const cellWidth = this.gameSimulationConfig.cellWidth
        const targetCollisionWidth = cellWidth * collisionConfig.width
        const cellHeight = this.gameSimulationConfig.cellHeight
        const targetCollisionHeight = cellHeight * collisionConfig.height

        const offsetFromCollisionCenter = Vector2.minus(location, targetCollisionCenter)

        const halfTargetCollisionWidth = targetCollisionWidth * 0.5
        const halfTargetCollisionHeight = targetCollisionHeight * 0.5

        let xEdgeDistance = 0;
        if (offsetFromCollisionCenter.x < -halfTargetCollisionWidth) {
          xEdgeDistance = offsetFromCollisionCenter.x + halfTargetCollisionWidth
        } else if (offsetFromCollisionCenter.x > halfTargetCollisionWidth) {
          xEdgeDistance = offsetFromCollisionCenter.x - halfTargetCollisionWidth
        }

        let yEdgeDistance = 0;
        if (offsetFromCollisionCenter.y < -halfTargetCollisionHeight) {
          yEdgeDistance = offsetFromCollisionCenter.y + halfTargetCollisionHeight
        } else if (offsetFromCollisionCenter.y > halfTargetCollisionHeight) {
          yEdgeDistance = offsetFromCollisionCenter.y - halfTargetCollisionHeight
        }

        return new Vector2(xEdgeDistance, yEdgeDistance)
      } else {
        return Vector2.uniform(Vector2.distance(location, targetLocation) - defaultTargetRadius)
      }
    } else {
      return Vector2.uniform(Vector2.distance(location, targetLocation) - defaultTargetRadius)
    }
  }
}
