import {ActivityStreamComponentData} from "../game/activityStreamComponentData";
import {IconArrowDown, IconGridDots, IconHandGrab, IconHandOff} from "@tabler/icons-react";
import {ActionIcon, Text} from "@mantine/core";
import {InventoryListComponent} from "./InventoryListComponent";
import React from "react";
import {DynamicState} from "./DynamicState";
import {Entity} from "../simulation/Entity";
import {InventoryComponentData} from "../game/CharacterComponentData";
import {ItemConfig} from "../game/ItemComponentData";

interface QuickInventoryProps {
  windowHeight: number
  windowWidth: number
  dynamicState: DynamicState
  userControlledEntity: Entity | null
  useMobileLayout: boolean
}

interface QuickInventoryEntry {
  firstStackIndex: number
  itemConfig: ItemConfig
  isEquipped: boolean
  totalAmount: number
}

export function QuickInventory(props: QuickInventoryProps): JSX.Element | null {
  const windowHeight = props.windowHeight
  const windowWidth = props.windowWidth
  const dynamicState = props.dynamicState
  const sideBarWidth = 250
  const simulation = props.dynamicState.simulation

  if (simulation == null) {
    return null
  }

  const userControlledEntity = props.userControlledEntity

  if (userControlledEntity == null) {
    return null
  }

  const inventoryComponent = userControlledEntity.getComponentOrNull<InventoryComponentData>("InventoryComponentData")

  if (inventoryComponent == null) {
    return null
  }
  const inventory = inventoryComponent.data.inventory;
  const itemStacks = inventory.itemStacks

  const entries: QuickInventoryEntry[] = []
  const foundItemConfigKeys: Record<string, boolean> = {}

  itemStacks.forEach((itemStack, stackIndex) => {
    const itemConfigKey = itemStack.itemConfigKey;
    const itemConfig = simulation.getConfig<ItemConfig>(itemConfigKey, "ItemConfig")

    if (foundItemConfigKeys[itemStack.itemConfigKey]) {
      return
    }

    foundItemConfigKeys[itemStack.itemConfigKey] = true

    if (itemConfig.equippableConfig == null ||
      itemConfig.equippableConfig.equipmentSlot !== "Tool") {
      return
    }

    let totalAmount = 0
    let isEquipped = false

    for (let checkItemStack of itemStacks) {
      if (checkItemStack.itemConfigKey === itemConfigKey) {
        totalAmount += checkItemStack.amount
        isEquipped = isEquipped || checkItemStack.isEquipped
      }
    }

    entries.push({
      isEquipped: isEquipped,
      totalAmount: totalAmount,
      firstStackIndex: stackIndex,
      itemConfig: itemConfig
    })
  })

  entries.sort((a, b) => {
    if (a.isEquipped !== b.isEquipped) {
      if (a.isEquipped) {
        return -1
      } else {
        return 1
      }
    } else if (a.itemConfig.key < b.itemConfig.key) {
      return -1
    } else {
      return 1
    }
  })

  const content = entries.map((entry, entryIndex) => {
    const itemConfig = entry.itemConfig
    const isEquipped = entry.isEquipped

    let buttonRef: HTMLButtonElement | null = null

    return <div
      key={itemConfig.key + ":" + entryIndex}
      style={{
        display: "flex",
        flexDirection: "row",
        padding: 0,
        alignItems: "center",
        backgroundColor: "rgba(255, 255, 255, 0.5)",
        height: 44,
        backdropFilter: "blur(5px)",
        WebkitBackdropFilter: "blur(5px)",
        borderRadius: 5,
        gap: 10
      }}
    >
      <ActionIcon
        ref={actionIcon => buttonRef = actionIcon}
        color={isEquipped ? "blue" : "gray"}
        size={44}
        variant={isEquipped ? "filled" : "subtle"}
        onClick={() => {

        }}
      >
        <img
          src={itemConfig.iconUrl}
          alt={"Item icon for " + itemConfig.name}
          style={{
            flexBasis: 40,
            height: 40
          }}
        />
      </ActionIcon>
    </div>

    // return <div
    //   key={itemConfig.key + ":" + entryIndex}
    //   style={{
    //     display: "flex",
    //     flexDirection: "row",
    //     gap: 5,
    //     backgroundColor: "rgba(0, 0, 0, 0.1)",
    //     borderRadius: 6,
    //     padding: 5
    //   }}
    // >
    //   <Text><b>{itemConfig.name}</b></Text>
    //
    //   {/*<Text><b>x{itemStack.amount}</b></Text>*/}
    //   {/*{!props.viewOnly && itemConfig.equippableConfig && !isEquipped ?*/}
    //   {/*  <ActionIcon size={35} variant={"filled"} onClick={() => {*/}
    //   {/*    simulation.sendMessage("EquipItemRequest", {*/}
    //   {/*      expectedItemConfigKey: itemConfigKey,*/}
    //   {/*      stackIndex: stackIndex*/}
    //   {/*    })*/}
    //   {/*  }}>*/}
    //   {/*    <IconHandGrab size={18}/>*/}
    //   {/*  </ActionIcon> : null}*/}
    //
    //   {/*{!props.viewOnly && itemConfig.equippableConfig && isEquipped ?*/}
    //   {/*  <ActionIcon size={35} variant={"filled"} onClick={() => {*/}
    //   {/*    simulation.sendMessage("UnequipItemRequest", {*/}
    //   {/*      expectedItemConfigKey: itemConfigKey,*/}
    //   {/*      stackIndex: stackIndex*/}
    //   {/*    })*/}
    //   {/*  }}>*/}
    //   {/*    <IconHandOff size={18}/>*/}
    //   {/*  </ActionIcon> : null}*/}
    //
    //   {/*{!props.viewOnly && itemConfig.storableConfig && itemConfig.storableConfig.canBeDropped ?*/}
    //   {/*  <ActionIcon size={35} variant={"filled"} onClick={() => {*/}
    //   {/*    simulation.sendMessage("DropItemRequest", {*/}
    //   {/*      itemConfigKey: itemConfigKey,*/}
    //   {/*      amountFromStack: null, // TODO*/}
    //   {/*      stackIndex: stackIndex*/}
    //   {/*    })*/}
    //   {/*  }}>*/}
    //   {/*    <IconArrowDown size={18}/>*/}
    //   {/*  </ActionIcon> : null}*/}
    // </div>
  })

  return <div
    style={{
      borderRadius: 6,
      paddingLeft: 4,
      paddingRight: 15,
      width: "100%",
      flexBasis: 48,
      display: "flex",
      flexDirection: "row",
      backgroundColor: "rgba(0, 0, 0, 0.4)",
      gap: 8,
      alignItems: "center"
    }}
  >
    {content}
  </div>
}