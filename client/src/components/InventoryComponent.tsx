import {DynamicState} from "./SimulationComponent";
import {CharacterComponentData, InventoryComponentData} from "../game/CharacterComponentData";
import {Entity} from "../simulation/Entity";
import {ItemConfig} from "../game/ItemComponentData";
import {ActionIcon, Text} from "@mantine/core";
import React from "react";
import {IconArrowDown, IconHandGrab, IconHandOff} from "@tabler/icons-react";

interface InventoryComponentProps {
  entity: Entity
  dynamicState: DynamicState
  viewOnly: boolean
}

export function InventoryComponent(props: InventoryComponentProps): JSX.Element | null {
  const dynamicState = props.dynamicState
  const entity = props.entity
  const simulation = dynamicState.simulation!

  const inventoryComponent = entity.getComponentOrNull<InventoryComponentData>("InventoryComponentData")

  if (inventoryComponent == null) {
    return null
  }
  const inventory = inventoryComponent.data.inventory;
  const itemStacks = inventory.itemStacks


  const characterComponent = entity.getComponentOrNull<CharacterComponentData>("CharacterComponentData")
  const equippedItemConfigKey = characterComponent?.data.equippedItemConfigKey


  return <div
    style={{
      display: "flex",
      flexDirection: "column",
      gap: 5
    }}
  >
    {itemStacks.map((itemStack, index) => {
      const itemConfigKey = itemStack.itemConfigKey;
      const itemConfig = simulation.getConfig<ItemConfig>(itemConfigKey, "ItemConfig")
      const isEquipped = equippedItemConfigKey === itemStack.itemConfigKey

      return <div
        key={itemConfigKey + ":" + index}
        style={{
          display: "flex",
          flexDirection: "row",
          gap: 5,
          backgroundColor: "rgba(0, 0, 0, 0.1)",
          borderRadius: 6,
          padding: 5
        }}
      >
        <Text><b>{itemConfig.name}</b></Text>
        <img
          src={itemConfig.iconUrl}
          alt={"Item icon for " + itemConfig.name}
          style={{
            flexBasis: 40,
            height: 40
          }}
        />
        <Text><b>x{itemStack.amount}</b></Text>
        {!props.viewOnly && itemConfig.canBeEquipped && !isEquipped ? <ActionIcon size={35} variant={"filled"} onClick={() => {
          simulation.sendMessage("EquipItemMessage", {
            itemConfigKey: itemConfigKey
          })
        }}>
          <IconHandGrab size={18}/>
        </ActionIcon> : null}

        {!props.viewOnly && itemConfig.canBeEquipped && isEquipped ? <ActionIcon size={35} variant={"filled"} onClick={() => {
          simulation.sendMessage("EquipItemMessage", {
            itemConfigKey: null
          })
        }}>
          <IconHandOff size={18}/>
        </ActionIcon> : null}

        {!props.viewOnly && itemConfig.canBeDropped ? <ActionIcon size={35} variant={"filled"} onClick={() => {
          simulation.sendMessage("DropItemMessage", {
            itemConfigKey: itemConfigKey,
            amount: null // TODO (Also: todo, support specifying which stack)
          })
        }}>
          <IconArrowDown size={18}/>
        </ActionIcon> : null}
      </div>
    })}
  </div>
}
