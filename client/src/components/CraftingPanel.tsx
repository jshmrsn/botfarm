import {ActivityStreamComponentData} from "../game/activityStreamComponentData";
import {IconArrowDown, IconGridDots, IconHammer, IconHandGrab, IconHandOff, IconTool} from "@tabler/icons-react";
import {ActionIcon, Text} from "@mantine/core";
import {InventoryComponent} from "./InventoryComponent";
import React from "react";
import {DynamicState} from "./SimulationComponent";
import {Entity} from "../simulation/Entity";
import {ItemConfig} from "../game/ItemComponentData";
import {InventoryComponentData} from "../game/CharacterComponentData";


interface CraftingPanelProps {
  windowHeight: number
  windowWidth: number
  dynamicState: DynamicState
  userControlledEntity?: Entity
  useMobileLayout: boolean
}


export function CraftingPanel(props: CraftingPanelProps): JSX.Element | null {
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

  const craftingItemConfigs = simulation.configs
    .filter(it => it.type === "ItemConfig")
    .map(it => (it as any) as ItemConfig)
    .filter(it => it.craftableConfig != null)


  const inventoryComponent = userControlledEntity.getComponentOrNull<InventoryComponentData>("InventoryComponentData")

  if (inventoryComponent == null) {
    return null
  }

  const inventory = inventoryComponent.data.inventory;
  const itemStacks = inventory.itemStacks

  const inventoryAmountByItemConfigKey: Record<string, number> = {}
  for (const itemStack of itemStacks) {
    const previousAmount = inventoryAmountByItemConfigKey[itemStack.itemConfigKey] || 0
    inventoryAmountByItemConfigKey[itemStack.itemConfigKey] = previousAmount + itemStack.amount
  }

  return <div
    style={{
      width: props.useMobileLayout ? "100%" : sideBarWidth,
      backgroundColor: "rgba(255, 255, 255, 0.5)",
      backdropFilter: "blur(5px)",
      WebkitBackdropFilter: "blur(5px)",
      padding: 10,
      borderRadius: 10,
      right: 10,
      display: "flex",
      flexDirection: "column",
      height: props.useMobileLayout ? props.windowHeight * 0.25 : 0,
      flexGrow: 1
    }}
  >
    <div
      key="header"
      style={{
        display: "flex",
        flexDirection: "row",
        alignItems: "center",
        gap: 5,
        marginBottom: 5
      }}
    >
      <IconHammer size={15}/>
      <Text>Crafting</Text>
    </div>

    <div
      key={"scroll-content"}
      style={{
        flexGrow: 1.0,
        overflowY: "auto"
      }}
    >
      <div
        style={{
          display: "flex",
          flexDirection: "column",
          gap: 5
        }}
      >
        {craftingItemConfigs.map((craftableItemConfig, index) => {
          const itemConfigKey = craftableItemConfig.key;
          const craftableConfig = craftableItemConfig.craftableConfig!

          let canAfford = true
          for (let costEntry of craftableConfig.craftingCost.entries) {
            const hasAmount = inventoryAmountByItemConfigKey[costEntry.itemConfigKey] || 0

            if (hasAmount < costEntry.amount) {
              canAfford = false
            }
          }


          return <div
            key={itemConfigKey + ":" + index}
            style={{
              display: "column",
              flexDirection: "row",
              gap: 5, //todo: why is gap not working?
              backgroundColor: "rgba(0, 0, 0, 0.1)",
              borderRadius: 6,
              padding: 5
            }}
          >
            <div
              style={{
                display: "flex",
                flexDirection: "row",
                gap: 5,
              }}
            >
              <Text><b>{craftableItemConfig.name}</b></Text>
              <img
                src={craftableItemConfig.iconUrl}
                alt={"Item icon for " + craftableItemConfig.name}
                style={{
                  flexBasis: 40,
                  height: 40
                }}
              />
              {craftableConfig.craftingAmount > 1 ? <Text><b>x{craftableConfig.craftingAmount}</b></Text> : null}
              {canAfford ? <ActionIcon size={35} variant={"filled"} onClick={() => {
                simulation.sendMessage("CraftItemRequest", {
                  itemConfigKey: itemConfigKey
                })
              }}>
                <IconHammer size={18}/>
              </ActionIcon> : null}
            </div>

            <div
              key="cost"
              style={{
                display: "column",
                flexDirection: "row",
                gap: 5,
                backgroundColor: "rgba(0, 0, 0, 0.15)",
                borderRadius: 6,
                padding: 5
              }}
            >
              {craftableConfig.craftingCost.entries.map((costEntry, costEntryIndex) => {
                const costItemConfig = simulation.getConfig<ItemConfig>(costEntry.itemConfigKey, "ItemConfig")
                const hasAmount = inventoryAmountByItemConfigKey[costEntry.itemConfigKey] || 0

                return <div
                  style={{
                    display: "flex",
                    flexDirection: "row",
                    gap: 5
                  }}
                >
                  <Text><b>{costItemConfig.name}</b></Text>
                  <img
                    src={costItemConfig.iconUrl}
                    alt={"Item icon for " + costItemConfig.name}
                    style={{
                      flexBasis: 40,
                      height: 40
                    }}
                  />
                  <Text color={hasAmount >= costEntry.amount ? "green" : "red"}><b>{hasAmount}/{costEntry.amount}</b></Text>
                </div>
              })}
            </div>
          </div>
        })}
      </div>
    </div>
  </div>
}