import {CharacterComponentData, InventoryComponentData} from "../game/CharacterComponentData";
import {ItemComponentData, ItemConfig} from "../game/ItemComponentData";
import {Switch, Text} from "@mantine/core";
import {renderSelectedEntityInspection} from "./RenderSelectedEntityInspection";
import React, {useState} from "react";
import {DynamicState} from "./SimulationComponent";
import {Entity} from "../simulation/Entity";
import {InventoryComponent} from "./InventoryComponent";
import {IconInfoCircle} from "@tabler/icons-react";
import {resolveEntityPositionForCurrentTime} from "../common/PositionComponentData";


interface InspectionPanelProps {
  windowHeight: number
  windowWidth: number
  dynamicState: DynamicState
  selectedEntity: Entity
  useMobileLayout: boolean
}

export function InspectionPanel(props: InspectionPanelProps) {
  const dynamicState = props.dynamicState
  const sideBarWidth = 400
  const simulation = dynamicState.simulation

  const [debugMode, setDebugMode] = useState(false)

  if (simulation == null) {
    return null
  }

  const entity = props.selectedEntity

  const inventoryComponent = entity.getComponentOrNull<InventoryComponentData>("InventoryComponentData")
  const characterComponent = entity.getComponentOrNull<CharacterComponentData>("CharacterComponentData")
  const itemComponent = entity.getComponentOrNull<ItemComponentData>("ItemComponentData")
  const itemConfig = itemComponent != null ? simulation.getConfig<ItemConfig>(itemComponent.data.itemConfigKey, "ItemConfig") : null

  const position = resolveEntityPositionForCurrentTime(entity)


  return <div
    key={"selected-entity-side-bar"}
    style={{
      width: props.useMobileLayout
        ? "100%"
        : debugMode ? Math.max(props.windowWidth * 0.4, Math.min(300, props.windowWidth - 20)) : 400,
      backgroundColor: "rgba(255, 255, 255, 0.5)",
      backdropFilter: "blur(5px)",
      WebkitBackdropFilter: "blur(5px)",
      padding: 10,
      borderRadius: 10,
      right: 10,
      display: "flex",
      flexDirection: "column",
      height: props.useMobileLayout ? props.windowHeight * 0.65 : undefined
    }}
  >
    <div
      key="header"
      style={{
        display: "flex",
        flexDirection: "column",
        gap: 1,
        marginBottom: 3
      }}
    >
      <div
        key="header-title"
        style={{
          display: "flex",
          flexDirection: "row",
          alignItems: "center",
          gap: 5
        }}
      >
        <IconInfoCircle size={15}/>
        <Text>Inspection</Text>
      </div>

      {characterComponent != null
        ? <Text weight={"bold"}>{characterComponent.data.name}</Text>
        : null}

      {itemConfig != null
        ? <Text weight={"bold"}>{itemConfig.name}</Text>
        : null}
    </div>

    <div
      key={"scroll-content"}
      style={{
        flexGrow: 1.0,
        overflowY: "scroll",
        gap: 6,
        flexDirection: "column",
        display: "flex",
        paddingTop: 10,
        paddingBottom: 10
      }}
    >
      <Text><b>Position</b> x{position.x.toFixed(0)}, y{position.y.toFixed(0)}</Text>

      <Text weight={"bold"}>Inventory</Text>
      {inventoryComponent != null ? <InventoryComponent entity={entity} dynamicState={props.dynamicState} viewOnly={true}/> : null}

      <Switch checked={debugMode}
              onChange={(event) => setDebugMode(event.currentTarget.checked)}
              label={"Debug Mode"}
      />

      {debugMode ? renderSelectedEntityInspection(entity) : null}
    </div>
  </div>
}