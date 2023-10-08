import {Vector2} from "../misc/Vector2";
import {GameSimulationScene} from "../game/GameSimulationScene";
import Phaser from "phaser";
import Graphics = Phaser.GameObjects.Graphics;
import FilterMode = Phaser.Textures.FilterMode;
import Layer = Phaser.GameObjects.Layer;
import {SpriteAnimation} from "./common";


export interface SpriteDisplayProperties {
  layer?: Layer | null
  textureName: string
  animation?: Phaser.Types.Animations.PlayAnimationConfig | null
  animationShouldFallbackWhenDoneIdentifier?: string | null
  fallbackAnimation?: Phaser.Types.Animations.PlayAnimationConfig | null
  position: Vector2
  scale?: Vector2 | null
  alpha?: number | null
  depth?: number | null
  filterMode?: FilterMode | null
}

export interface TextDisplayProperties {
  layer?: Layer | null
  useUiCamera?: boolean | null
  text?: string | null
  position?: Vector2 | null
  scale?: Vector2 | null
  origin?: Vector2 | null
  strokeThickness?: number | null
  strokeColor?: string | null
  color?: string | null
  alpha?: number | null

  fontSize?: number | null
  font?: string | null
  fontStyle?: string | null

  backgroundColor?: string | null

  align?: "left" | "right" | "center" | "justify" | null // left, right, center or justify.

  depth?: number | null

  style?: Phaser.Types.GameObjects.Text.TextStyle
}

export class RenderedSprite {
  sprite: Phaser.GameObjects.Sprite | null = null
  spriteProperties: SpriteDisplayProperties | null = null
  graphics: Graphics | null = null

  text: Phaser.GameObjects.Text | null = null
  textProperties: TextDisplayProperties | null = null
}


export class RenderContext {
  private scene: GameSimulationScene;

  private renderedGameObjectsByKey: Map<string, RenderedSprite> = new Map<string, RenderedSprite>()
  private unusedRenderedGameObjectKeys: Set<string> = new Set()

  constructor(scene: GameSimulationScene) {
    this.scene = scene
  }

  render(contentCallback: () => void) {
    this.unusedRenderedGameObjectKeys = new Set(this.renderedGameObjectsByKey.keys())

    contentCallback()

    this.unusedRenderedGameObjectKeys.forEach(unusedRenderedSpriteKey => {
      const unusedRenderedSprite = this.renderedGameObjectsByKey.get(unusedRenderedSpriteKey)

      if (unusedRenderedSprite != null) {
        if (unusedRenderedSprite.sprite != null) {
          unusedRenderedSprite.sprite.destroy()
        }

        if (unusedRenderedSprite.text != null) {
          unusedRenderedSprite.text.destroy()
        }

        if (unusedRenderedSprite.graphics != null) {
          unusedRenderedSprite.graphics.destroy()
        }
      } else {
        throw new Error("missing unusedRenderedSprite: " + unusedRenderedSpriteKey)
      }

      this.renderedGameObjectsByKey.delete(unusedRenderedSpriteKey)
    })
  }

  getOrAddRenderedGameObject = (key: string) => {
    const existing = this.renderedGameObjectsByKey.get(key)

    if (existing != null) {
      if (!this.unusedRenderedGameObjectKeys.has(key)) {
        throw new Error("Same game object key rendered twice in same frame: " + key)
      }

      this.unusedRenderedGameObjectKeys.delete(key)

      return existing
    } else {
      const renderedSprite = new RenderedSprite()
      this.renderedGameObjectsByKey.set(key, renderedSprite)
      return renderedSprite
    }
  }

  readonly renderText = (key: string, textProperties: TextDisplayProperties) => {
    const renderedGameObject = this.getOrAddRenderedGameObject(key)

    if (renderedGameObject.sprite != null) {
      renderedGameObject.sprite.destroy()
      renderedGameObject.sprite = null
      renderedGameObject.spriteProperties = null
    }

    if (renderedGameObject.graphics != null) {
      renderedGameObject.graphics.destroy()
      renderedGameObject.graphics = null
    }

    if (renderedGameObject.text == null) {
      const text = this.scene.add.text(0, 0, '?', textProperties.style)
      if (textProperties.layer != null) {
        textProperties.layer.add(text)
      }

      renderedGameObject.text = text
      text.setResolution(1)
      text.texture.setFilter(FilterMode.LINEAR)

      const useUiCamera = textProperties.useUiCamera ?? true

      if (useUiCamera) {
        this.scene.mainCamera.ignore(text)
      } else {
        this.scene.uiCamera.ignore(text)
      }
    }

    const text = renderedGameObject.text
    const position = textProperties.position ?? Vector2.zero
    const scale = textProperties.scale ?? Vector2.one

    text.setX(position.x)
    text.setY(position.y)
    text.setScale(scale.x, scale.y)
    text.setAlign(textProperties.align ?? "left")


    const origin = textProperties.origin ?? Vector2.zero
    text.setOrigin(origin.x, origin.y)

    const previousProperties = renderedGameObject.textProperties

    if (previousProperties == null || previousProperties.alpha !== textProperties.alpha) {
      text.setAlpha(textProperties.alpha ?? 1.0)
    }

    if (previousProperties == null || previousProperties.text !== textProperties.text) {
      text.setText(textProperties.text ?? "")
    }

    if (previousProperties == null || previousProperties.font !== textProperties.font) {
      text.setFont(textProperties.font ?? "Arial Black")
    }

    if (previousProperties == null || previousProperties.fontSize !== textProperties.fontSize) {
      text.setFontSize(textProperties.fontSize ?? 30)
    }

    if (previousProperties == null || previousProperties.depth !== textProperties.depth) {
      text.setDepth(textProperties.depth ?? 0)
    }

    if (previousProperties == null || previousProperties.depth !== textProperties.depth) {
      text.setDepth(textProperties.depth ?? 0)
    }

    if (previousProperties == null || previousProperties.color !== textProperties.color) {
      text.setColor(textProperties.color ?? "white")
    }

    if (previousProperties == null || previousProperties.fontStyle !== textProperties.fontStyle) {
      text.setFontStyle(textProperties.fontStyle ?? "bold")
    }

    if (previousProperties == null || previousProperties.backgroundColor !== textProperties.backgroundColor) {
      if (textProperties.backgroundColor != null) {
        text.setBackgroundColor(textProperties.backgroundColor)
      } else {
        // todo: remove background color?
      }
    }

    if (previousProperties == null ||
      textProperties.strokeThickness !== previousProperties.strokeThickness ||
      textProperties.strokeColor !== previousProperties.strokeColor) {
      if (textProperties.strokeThickness != null ||
        textProperties.strokeColor != null) {
        text.setStroke(textProperties.strokeColor || '#000000', textProperties.strokeThickness || 6)
      } else {
        // todo: remove stroke?
      }
    }

    renderedGameObject.textProperties = textProperties
    return renderedGameObject.text
  }

  readonly renderGraphics = (key: string, layer: Layer | null, initialConfigurationCallback: (graphics: Graphics) => void) => {
    const renderedGameObject = this.getOrAddRenderedGameObject(key)

    if (renderedGameObject.sprite != null) {
      renderedGameObject.sprite.destroy()
      renderedGameObject.sprite = null
      renderedGameObject.spriteProperties = null
    }

    if (renderedGameObject.text != null) {
      renderedGameObject.text.destroy()
      renderedGameObject.text = null
      renderedGameObject.textProperties = null
    }

    if (renderedGameObject.graphics == null) {
      renderedGameObject.graphics = this.scene.add.graphics()

      if (layer != null) {
        layer.add(renderedGameObject.graphics)
      }

      // TODO: Support uiCamera graphics
      this.scene.uiCamera.ignore(renderedGameObject.graphics)

      initialConfigurationCallback(renderedGameObject.graphics)
    }

    return renderedGameObject.graphics
  }

  readonly renderSprite = (key: string, spriteProperties: SpriteDisplayProperties) => {
    const renderedGameObject = this.getOrAddRenderedGameObject(key)

    if (renderedGameObject.text != null) {
      renderedGameObject.text.destroy()
      renderedGameObject.text = null
      renderedGameObject.textProperties = null
    }

    if (renderedGameObject.graphics != null) {
      renderedGameObject.graphics.destroy()
      renderedGameObject.graphics = null
    }

    if (renderedGameObject.spriteProperties != null) {
      if (renderedGameObject.spriteProperties.textureName !== spriteProperties.textureName) {
        if (renderedGameObject.sprite != null) {
          renderedGameObject.sprite.destroy()
          renderedGameObject.sprite = null
        }

        renderedGameObject.spriteProperties = null
      }
    }

    if (renderedGameObject.sprite == null) {
      const sprite = this.scene.add
        .sprite(0, 0, spriteProperties.textureName)

      if (spriteProperties.layer != null) {
        spriteProperties.layer.add(sprite)
      }

      renderedGameObject.sprite = sprite

      this.scene.uiCamera.ignore(renderedGameObject.sprite);
    }

    const previousSpriteProperties = renderedGameObject.spriteProperties
    let animation = spriteProperties.animation

    const sprite = renderedGameObject.sprite
    if (animation != null) {
      const previousAnimation = previousSpriteProperties?.animation

      if (spriteProperties.animationShouldFallbackWhenDoneIdentifier != null &&
        previousSpriteProperties != null &&
        spriteProperties.animationShouldFallbackWhenDoneIdentifier === previousSpriteProperties.animationShouldFallbackWhenDoneIdentifier) {

        if (!sprite.anims.isPlaying) {
          animation = spriteProperties.fallbackAnimation || animation
        }
      }

      if (previousAnimation == null) {
        sprite.anims.play(animation, true)
      } else if (animation.key !== previousAnimation.key) {
        sprite.anims.play(animation, true)
      }
    } else {
      sprite.anims.stop()
    }

    if (previousSpriteProperties == null || previousSpriteProperties.filterMode !== spriteProperties.filterMode) {
      sprite.texture.setFilter(spriteProperties.filterMode || FilterMode.LINEAR)
    }

    const scale = spriteProperties.scale || Vector2.one
    sprite.setScale(scale.x, scale.y)

    sprite.setDepth(spriteProperties.depth ?? 0)

    const position = spriteProperties.position
    sprite.setX(position.x)
    sprite.setY(position.y)

    sprite.setAlpha(spriteProperties.alpha ?? 1.0)

    renderedGameObject.spriteProperties = spriteProperties

    return renderedGameObject.sprite!
  }
}