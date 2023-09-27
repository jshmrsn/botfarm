import {Tabs} from "@mantine/core";
import {DynamicState} from "./SimulationComponent";
import {renderSelectedEntityInspection} from "./RenderSelectedEntityInspection";
import React from "react";
import {CharacterComponentData, InventoryComponentData} from "../game/CharacterComponentData";
import {ItemComponentData, ItemConfig} from "../game/ItemComponentData";
import {UserControlledComponentData} from "../game/userControlledComponentData";
import {EntityId} from "../simulation/EntityData";


interface DebugPanelProps {
  windowHeight: number
  dynamicState: DynamicState
  selectedEntityId: EntityId | null
}

export function DebugPanel(props: DebugPanelProps) {
  const windowHeight = props.windowHeight
  const dynamicState = props.dynamicState
  const sideBarWidth = 400
  const simulation = props.dynamicState.simulation

  if (simulation == null) {
    return null
  }

  const playerControlledEntity = simulation.entities.find(entity => {
    const userControlledComponent = entity.getComponentDataOrNull<UserControlledComponentData>("UserControlledComponentData")
    return userControlledComponent != null && userControlledComponent.userId === dynamicState.userId
  })

  const selectedEntityId = props.selectedEntityId

  const entity = selectedEntityId != null ? simulation.getEntityOrNull(selectedEntityId) : playerControlledEntity

  if (entity == null) {
    return null
  }

  const inventoryComponent = entity.getComponentOrNull<InventoryComponentData>("InventoryComponentData")
  const characterComponent = entity.getComponentOrNull<CharacterComponentData>("CharacterComponentData")
  const itemComponent = entity.getComponentOrNull<ItemComponentData>("ItemComponentData")
  const itemConfig = itemComponent != null ? simulation.getConfig<ItemConfig>(itemComponent.data.itemConfigKey, "ItemConfig") : null

  const scrollAreaHeight = windowHeight - 100 - 60 - 20 - 40;

  return <div
    key={"selected-entity-side-bar"}
    style={{
      width: sideBarWidth,
      top: 60,
      bottom: 100,
      backgroundColor: "rgba(255, 255, 255, 0.5)",
      position: "absolute",
      backdropFilter: "blur(5px)",
      WebkitBackdropFilter: "blur(5px)",
      padding: 10,
      borderRadius: 10,
      left: 10,
      display: "flex",
      flexDirection: "column",
    }}
  >
    <div
      key={"content"}
      style={{
        display: "flex",
        flexDirection: "column",
        flexGrow: 1
      }}
    >
      {/*{itemConfig != null && itemComponent != null*/}
      {/*  ? <div*/}
      {/*    key={"item-info"}*/}
      {/*    style={{*/}
      {/*      display: "flex",*/}
      {/*      flexDirection: "column"*/}
      {/*    }}*/}
      {/*  >*/}
      {/*    <Text><b>Item: {itemConfig.name} (x{itemComponent.data.amount})</b></Text>*/}
      {/*  </div>*/}
      {/*  : null}*/}

      <Tabs defaultValue="debug">
        {/*<Tabs.List>*/}
        {/*  <Tabs.Tab value="info" icon={<IconInfoCircle size="0.8rem"/>}>Info</Tabs.Tab>*/}
        {/*  {inventoryComponent != null ?*/}
        {/*    <Tabs.Tab value="inventory" icon={<IconGridDots size="0.8rem"/>}>Inventory</Tabs.Tab> : null}*/}
        {/*  <Tabs.Tab value="debug" icon={<IconBug size="0.8rem"/>}>Debug</Tabs.Tab>*/}
        {/*</Tabs.List>*/}

        {/*<Tabs.Panel value="info" pt="xs">*/}
        {/*  <div*/}
        {/*    key={"content"}*/}
        {/*    style={{*/}
        {/*      flexGrow: 1.0,*/}
        {/*      height: scrollAreaHeight,*/}
        {/*      display: "flex",*/}
        {/*      flexDirection: "column",*/}
        {/*      overflowY: "scroll"*/}
        {/*    }}*/}
        {/*  >*/}
        {/*    {itemConfig != null && itemComponent != null*/}
        {/*      ?*/}
        {/*      <Text>HP: {itemComponent.data.hp} / {itemConfig.maxHp} </Text>*/}
        {/*      : null}*/}
        {/*  </div>*/}
        {/*</Tabs.Panel>*/}

        {inventoryComponent != null ? <Tabs.Panel value="inventory" pt="xs">
          <div
            key={"content"}
            style={{
              flexGrow: 1.0,
              height: scrollAreaHeight,
              display: "flex",
              flexDirection: "column",
              overflowY: "scroll"
            }}
          >
          </div>
        </Tabs.Panel> : null}

        <Tabs.Panel value="debug" pt="xs">
          <div
            key={"content"}
            style={{
              flexGrow: 1.0,
              height: scrollAreaHeight,
              display: "flex",
              flexDirection: "column",
              overflowY: "scroll"
            }}
          >
          {renderSelectedEntityInspection(entity)}
          </div>
        </Tabs.Panel>
      </Tabs>
    </div>
  </div>
}

