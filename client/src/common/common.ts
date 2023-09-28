import {ClientSimulationData, Config, EntityComponentData, EntityData, EntityId} from "../simulation/EntityData";
import {ActivityStreamEntry} from "../game/ActivityStreamEntry";
import {SerializationDiff} from "../misc/serializationDiff";
import {Vector2} from "../misc/Vector2";

export interface TilemapConfig extends Config {
  tilemapUrl: string
}

export interface RegisteredCompositeAnimation {
  key: string
  includedVariants: string[]
}

export interface CompositeAnimationRegistryConfig extends Config {
  registeredCompositeAnimations: RegisteredCompositeAnimation[]
  includedCategories: string[]
}

export interface CharacterBodySelectionsConfig extends Config {
  skinColors: string[]
  bodyTypes: string[]
  bodies: string[]
  heads: string[]
  noses: string[]
  eyes: RegisteredCompositeAnimation[]
  wrinkles: string[]
  hairs: RegisteredCompositeAnimation[]
}

export interface SpriteConfig extends Config {
  baseScale: Vector2,
  baseOffset: Vector2,
  textureUrl: string
  atlasUrl: string | undefined
  animationsUrl: string | undefined
  animations: SpriteAnimation[]
  depthOffset: number
}

export interface SpriteAnimation {
  keySuffix: string | null,
  frameRate: number | undefined
  duration: number | undefined
  repeat: number | undefined
  framesPrefix: string | null
  singleFrame: string | null
  framesStart: number
  framesEnd: number
  framesZeroPad: number
}


export interface SimulationSnapshotWebSocketMessage {
  simulationData: ClientSimulationData
}

export interface EntityComponentWebSocketMessage {
  entityId: EntityId
  componentTypeName: string
  diff: SerializationDiff
  simulationTime: number
}

export interface AlertWebSocketMessage {
  message: string
  mode: string
}

export interface NewActivityStreamEntryWebSocketMessage {
  activityStreamEntry: ActivityStreamEntry
}

export interface EntityCreatedWebSocketMessage {
  entityData: EntityData
  simulationTime: number
}

export interface EntityDestroyedWebSocketMessage {
  entityId: EntityId
  simulationTime: number
}

