import {ActionIcon} from "@mantine/core";
import React, {ReactElement} from "react";
import {DynamicState} from "./DynamicState";
import {Entity} from "../simulation/Entity";
import {InventoryComponentData} from "../game/CharacterComponentData";
import {ItemConfig} from "../game/ItemComponentData";
import {PanelType, renderPanelButton} from "./GameSimulationComponent";
import {IconGridDots} from "@tabler/icons-react";

interface QuickInventoryProps {
  windowHeight: number
  windowWidth: number
  dynamicState: DynamicState
  userControlledEntity: Entity | null
  perspectiveEntity: Entity
  useMobileLayout: boolean
  showingPanels: PanelType[]
  setShowingPanels: (panels: PanelType[]) => void
}

interface QuickInventoryEntry {
  firstStackIndex: number
  itemConfig: ItemConfig
  isEquipped: boolean
  totalAmount: number
  equippedStackIndex: number | null
}

export function QuickInventory(props: QuickInventoryProps): ReactElement | null {
  const simulation = props.dynamicState.simulation

  if (simulation == null) {
    return null
  }

  const perspectiveEntity = props.perspectiveEntity

  const inventoryComponent = perspectiveEntity.getComponentOrNull<InventoryComponentData>("InventoryComponentData")

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
    let equippedStackIndex: number | null = null

    itemStacks.forEach((checkItemStack, checkItemStackIndex) => {
      if (checkItemStack.itemConfigKey === itemConfigKey) {
        totalAmount += checkItemStack.amount
        if (checkItemStack.isEquipped) {
          equippedStackIndex = checkItemStackIndex
        }
      }
    })

    entries.push({
      isEquipped: equippedStackIndex != null,
      equippedStackIndex: equippedStackIndex,
      totalAmount: totalAmount,
      firstStackIndex: stackIndex,
      itemConfig: itemConfig
    })
  })

  entries.sort((a, b) => {
    if (a.itemConfig.key < b.itemConfig.key) {
      return -1
    } else {
      return 1
    }
  })

  const content = entries.map((entry, entryIndex) => {
    const itemConfig = entry.itemConfig
    const isEquipped = entry.isEquipped
    const equippedStackIndex = entry.equippedStackIndex

    let buttonRef: HTMLButtonElement | null = null

    return <div
      key={itemConfig.key + ":" + entryIndex}
      style={{
        display: "flex",
        flexDirection: "row",
        padding: 0,
        alignItems: "center",
        backgroundColor: "rgba(255, 255, 255, 0.5)",
        height: 50,
        flexBasis: 50,
        backdropFilter: "blur(5px)",
        WebkitBackdropFilter: "blur(5px)",
        borderRadius: 5,
        gap: 10
      }}
    >
      <ActionIcon
        ref={actionIcon => buttonRef = actionIcon}
        color={isEquipped ? "blue" : "gray"}
        size={50}
        variant={isEquipped ? "filled" : "subtle"}
        disabled={props.perspectiveEntity !== props.userControlledEntity}
        onClick={() => {
          buttonRef?.blur()

          if (equippedStackIndex == null) {
            simulation.sendMessage("EquipItemRequest", {
              expectedItemConfigKey: itemConfig.key,
              stackIndex: entry.firstStackIndex
            })
          } else {
            simulation.sendMessage("UnequipItemRequest", {
              expectedItemConfigKey: itemConfig.key,
              stackIndex: equippedStackIndex
            })
          }
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
  })

  return <div
    style={{
      borderRadius: 6,
      paddingLeft: 5,
      paddingRight: 5,
      width: "100%",
      flexBasis: 50 + 8,
      display: "flex",
      flexDirection: "row",
      backgroundColor: "rgba(255, 255, 255, 0.5)",
      gap: 4,
      alignItems: "center",
      pointerEvents: "auto"
    }}
  >
    <div
      key={"scroll"}
      style={{
        overflowX: "auto",
        overflowY: "hidden",
        display: "flex",
        flexGrow: 1.0,
        flexBasis: 0,
        gap: 4
      }}
    >
      {content}
    </div>

    {renderPanelButton(PanelType.Inventory, <IconGridDots size={24}/>, props.showingPanels, props.setShowingPanels)}
  </div>
}