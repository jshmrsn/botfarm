import {EntityComponentData} from "../engine/simulation/EntityData";
import {Vector2Animation} from "../misc/Vector2Animation";
import {Entity} from "../engine/simulation/Entity";
import {EntityComponent} from "../engine/simulation/EntityComponent";
import {EntityComponentGetter} from "../engine/simulation/EntityComponentGetter";

export interface PositionComponentData extends EntityComponentData {
  positionAnimation: Vector2Animation
}

export const PositionComponent = new EntityComponentGetter<PositionComponentData>("PositionComponentData")

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