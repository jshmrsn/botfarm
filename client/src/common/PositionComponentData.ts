import {EntityComponentData} from "../simulation/EntityData";
import {Vector2Animation} from "../misc/Vector2Animation";
import {Entity} from "../simulation/Entity";
import {EntityComponent} from "../simulation/EntityComponent";

export interface PositionComponentData extends EntityComponentData {
  positionAnimation: Vector2Animation
}

export function resolveEntityPositionForCurrentTime(entity: Entity) {
  const positionComponent = entity.getComponent<PositionComponentData>("PositionComponentData")
  return Vector2Animation.resolve(positionComponent.data.positionAnimation, entity.simulation.getCurrentSimulationTime())
}

export function resolveEntityPositionForTime(entity: Entity, simulationTime: number) {
  const positionComponent = entity.getComponent<PositionComponentData>("PositionComponentData")
  return Vector2Animation.resolve(positionComponent.data.positionAnimation, simulationTime)
}

export function resolvePositionForCurrentTime(positionComponent: EntityComponent<PositionComponentData>) {
  return Vector2Animation.resolve(positionComponent.data.positionAnimation, positionComponent.entity.simulation.getCurrentSimulationTime())
}

export function resolvePositionForTime(positionComponent: EntityComponent<PositionComponentData>, simulationTime: number) {
  return Vector2Animation.resolve(positionComponent.data.positionAnimation, simulationTime)
}