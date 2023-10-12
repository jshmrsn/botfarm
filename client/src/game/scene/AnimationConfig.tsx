export interface AnimationConfig {
  name: string
  spriteCellRowIndex: number
  numberOfFrames: number
  frameRate: number
}

export interface RawSheetDefinitionLayer {
  zPos: number
  custom_animation?: string
  // category name: string
}

export interface AnimationsConfig {
  spriteCellWidth: number
  spriteCellHeight: number
  animations: AnimationConfig[]
}

export interface RawSheetDefinition {
  name: string
  type_name: string,
  variants: string[]
  match_body_color?: boolean
  //layer_N?: SheetDefinitionLayer
}

export interface SheetDefinitionLayer {
  zPos: number
  custom_animation: string | null
  textureKeysByCategoryByVariant: Record<string, Record<string, string>>
  texturePartialPathsByCategoryByVariant: Record<string, Record<string, string>>
  animationsConfig: AnimationsConfig
  animationConfigsByName: Record<string, AnimationConfig>
}

export interface SheetDefinition {
  name: string
  type_name: string,
  includedVariants: string[]
  match_body_color: boolean
  layers: SheetDefinitionLayer[]
}