import {CharacterComponentData, InventoryComponentData} from "../simulation/CharacterComponentData";
import {ItemComponentData, ItemConfig} from "../simulation/ItemComponentData";
import {ActionIcon, Button, Text} from "@mantine/core";
import {renderSelectedEntityInspection} from "./RenderSelectedEntityInspection";
import React, {Fragment, useState} from "react";
import {Entity} from "../../engine/simulation/Entity";
import {InventoryListComponent} from "./InventoryListComponent";
import {IconBug} from "@tabler/icons-react";
import {resolveEntityPositionForCurrentTime} from "../../common/PositionComponentData";
import {DynamicState} from "./DynamicState";
import {PanelCloseButton} from "./PanelCloseButton";
import {useWindowHeight} from "@react-hook/window-size";
import {buildIconDiv} from "./BuildIconDiv";


interface InspectionPanelProps {
  windowHeight: number
  windowWidth: number
  dynamicState: DynamicState
  selectedEntity: Entity
  useMobileLayout: boolean
  isViewingReplay: boolean
}

export function InspectionPanel(props: InspectionPanelProps) {
  const dynamicState = props.dynamicState
  const simulation = dynamicState.simulation

  const windowHeight = useWindowHeight()
  const [debugMode, setDebugMode] = useState(false)

  const scene = dynamicState.scene

  if (simulation == null || scene == null) {
    return null
  }

  const entity = props.selectedEntity

  const inventoryComponent = entity.getComponentOrNull<InventoryComponentData>("InventoryComponentData")
  const characterComponent = entity.getComponentOrNull<CharacterComponentData>("CharacterComponentData")
  const itemComponent = entity.getComponentOrNull<ItemComponentData>("ItemComponentData")
  const itemConfig = itemComponent != null ? simulation.getConfig<ItemConfig>(itemComponent.data.itemConfigKey, "ItemConfig") : null

  const position = resolveEntityPositionForCurrentTime(entity)

  const canSendMessages = !props.isViewingReplay

  return <div
    key={"inspection-panel"}
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
      height: props.useMobileLayout ? props.windowHeight * 0.65 : undefined,
      pointerEvents: "auto"
    }}
  >
    <PanelCloseButton close={() => {
      props.dynamicState.selectEntity(null)
    }} size={30} right={-5} top={-5}/>

    <div
      key={"debug-row"}
      style={{
        width: "100%",
        justifyContent: "right",
        display: "flex",
        flexDirection: "row",
        height: 1
      }}
    >
      <ActionIcon
        style={{
          top: -5,
          right: 30
        }}
        size={30}
        color={debugMode ? "blue" : "gray"}
        variant={debugMode ? "filled" : "subtle"}
        onClick={() => {
          setDebugMode(!debugMode)
        }}
      >
        <IconBug size="1.125rem"/>
      </ActionIcon>
    </div>

    <div
      key="header"
      style={{
        display: "flex",
        flexDirection: "row",
        gap: 10,
        marginBottom: 3,
        alignItems: "center"
      }}
    >
      {buildIconDiv(
        entity.entityId,
        simulation,
        scene,
        {
          profileIconSize: 40
        }
      )}

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
        overflowY: "auto",
        gap: 6,
        flexDirection: "column",
        display: "flex",
        paddingTop: 5,
        paddingBottom: 0
      }}
    >
      <Text><b>Position</b> x{position.x.toFixed(0)}, y{position.y.toFixed(0)}</Text>


      {canSendMessages && dynamicState.userControlledEntity === entity
        ? <Button
          variant={"filled"}
          onClick={() => {
            dynamicState.simulation?.sendMessage("ReRollRequest", {})
          }}
        >
          Re-Roll
        </Button>
        : null}

      {canSendMessages && dynamicState.userControlledEntity === entity
        ? <Button
          variant={"filled"}
          onClick={() => {
            dynamicState.simulation?.sendDespawnRequest()
          }}
        >
          De-spawn
        </Button>
        : null}

      {characterComponent != null &&
      (dynamicState.rawUserControlledEntity !== entity ||
        dynamicState.perspectiveEntity == null) ? <Button
        style={{}}
        variant={"filled"}
        onClick={() => {
          if (entity.entityId === dynamicState.perspectiveEntity?.entityId) {
            simulation.setPerspectiveEntityIdOverride(null)
          } else {
            simulation.setPerspectiveEntityIdOverride(entity.entityId)
            simulation.setShouldSpectateByDefault(false)
            scene.centerCameraOnEntityId(entity.entityId)
          }
        }}
      >
        {entity.entityId === dynamicState.perspectiveEntity?.entityId
          ? "Clear Perspective"
          : "View Perspective"}
      </Button> : null}

      {
        debugMode ? <Fragment>
          {inventoryComponent != null ? <Fragment>
            <Text weight={"bold"}>Inventory</Text>
            <InventoryListComponent
              perspectiveEntity={entity}
              userControlledEntity={null}
              dynamicState={dynamicState}
              viewOnly={true}/>
          </Fragment> : null}

          {debugMode ? renderSelectedEntityInspection(entity, windowHeight) : null}
        </Fragment> : null
      }
    </div>
  </div>
}