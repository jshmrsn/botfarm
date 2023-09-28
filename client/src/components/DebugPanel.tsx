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
      <Tabs defaultValue="debug">

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

