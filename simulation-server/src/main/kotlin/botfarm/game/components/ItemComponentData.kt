package botfarm.game.components

import botfarm.engine.simulation.Entity
import botfarm.engine.simulation.EntityComponentData
import botfarm.game.config.ItemConfig

data class ItemComponentData(
   val itemConfigKey: String,
   val amount: Int = 1
) : EntityComponentData()

val Entity.itemConfigOrNull: ItemConfig?
   get() = this.getComponentOrNull<ItemComponentData>()?.let {
      this.simulation.getConfig<ItemConfig>(it.data.itemConfigKey)
   }

val Entity.debugName: String
   get() {
      val itemConfig = this.itemConfigOrNull
      val character = this.getComponentOrNull<CharacterComponentData>()
      val typeName = if (character != null) {
         "Character '${character.data.name}'"
      } else if (itemConfig != null) {
         "Item '${itemConfig.name}'"
      } else {
         "?"
      }

      return typeName + " (" + this.entityId.value + ")"
   }