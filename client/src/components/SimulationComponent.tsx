import React, {useEffect, useState} from "react";
import {ActionIcon, Button, Text} from "@mantine/core";
import {
  IconArrowLeft,
  IconGridDots,
  IconHammer,
  IconInfoCircle,
  IconMessages,
  IconQuestionMark
} from "@tabler/icons-react";
import Phaser from "phaser";
import {SimulationScene, SimulationSceneContext} from "../game/SimulationScene";
import {ClientSimulationData} from "../simulation/EntityData";
import useWebSocket from "react-use-websocket";
import {generateId} from "../misc/utils";
import {useWindowSize} from "@react-hook/window-size";
import {GameSimulation} from "../game/GameSimulation";
import {ChatInputArea} from "./ChatInputArea";
import {MyInventoryPanel} from "./MyInventoryPanel";
import {handleWebSocketMessage} from "../common/handleWebSocketMessage";
import {DebugPanel} from "./DebugPanel";
import {AgentComponentData} from "../game/agentComponentData";
import {UserControlledComponentData} from "../game/userControlledComponentData";
import {ActivityPanel} from "./ActivityPanel";
import {CraftingPanel} from "./CraftingPanel";
import {InspectionPanel} from "./InspectionPanel";
import {Entity} from "../simulation/Entity";
import {
  PositionComponentData,
  resolveEntityPositionForCurrentTime,
  resolvePositionForCurrentTime
} from "../common/PositionComponentData";
import {Vector2} from "../misc/Vector2";
import {HelpPanel} from "./HelpPanel";

interface SimulationProps {
  simulationId: string
  shouldAllowWebGl: boolean
  shouldForceWebGl: boolean
  exit: () => void
}


function buildWebSocketMessage(type: string, data: object): string {
  return JSON.stringify({
    "type": type,
    "data": data
  })
}

function sendWebSocketMessage(webSocket: WebSocket, type: string, data: object) {
  console.log("sendWebSocketMessage", type)
  webSocket.send(buildWebSocketMessage(type, data))
}

export class DynamicState {
  forceRenderIndex: number = 0
  userId: string
  chatTextArea: HTMLTextAreaElement | null = null
  phaserScene: SimulationScene | null = null
  webSocket: WebSocket | null = null
  simulation: GameSimulation | null = null
  selectedEntityId: string | null = null
  setForceUpdateCounter: (counter: number) => void

  readonly clientId: string = generateId()

  constructor(userId: string, setForceUpdateCounter: (counter: number) => void) {
    this.userId = userId
    this.setForceUpdateCounter = setForceUpdateCounter
  }

  sendWebSocketMessage(type: string, data: object) {
    if (this.webSocket == null) {
      throw new Error("sendWebSocketMessage: webSocket is null")
    }

    sendWebSocketMessage(this.webSocket, type, data)
  }

  forceUpdate() {
    ++this.forceRenderIndex
    this.setForceUpdateCounter(this.forceRenderIndex)
  }
}

export enum PanelTypes {
  Activity,
  Inventory,
  Crafting
}

export const SimulationComponent = (props: SimulationProps) => {
  const simulationId = props.simulationId

  const [shouldShowDebugPanel, setShouldShowDebugPanel] = useState(false)
  const [shouldShowHelpPanel, setShouldShowHelpPanel] = useState(false)

  const [showingPanels, setShowingPanels] = useState<PanelTypes[]>([])

  const [_forceUpdateCounter, setForceUpdateCounter] = useState(0)
  const [phaserContainerDiv, setPhaserContainerDiv] = useState<HTMLDivElement | null>(null)

  const [sceneLoadComplete, setSceneLoadComplete] = useState(false)
  const [selectedEntityId, setSelectedEntityId] = useState<string | null>(null)


  const storedUserId = localStorage.getItem("userId")

  let userId: string
  if (storedUserId == null) {
    userId = generateId()
    console.log("Storing userId", userId)
    localStorage.setItem("userId", userId)
  } else {
    userId = storedUserId
  }

  const [dynamicState, _setDynamicState] = useState<DynamicState>(new DynamicState(userId, setForceUpdateCounter))

  const userControlledEntity = dynamicState.simulation?.entities.find(it => {
    const userControlledComponent = it.getComponentOrNull<UserControlledComponentData>("UserControlledComponentData")
    return userControlledComponent != null && userControlledComponent.data?.userId === dynamicState.userId
  })
  console.log("userControlledEntity", userControlledEntity)
  console.log("dynamicState.userId", dynamicState.userId)
  

  const [windowWidth, windowHeight] = useWindowSize()

  if (dynamicState.phaserScene != null) {
    dynamicState.selectedEntityId = selectedEntityId
  }

  var loc = window.location;
  let websocketProtocol = loc.protocol === "https:" ? "wss" : "ws"
  const websocketHost = loc.host.replace(":3005", ":5001") // replace react-dev-server port with default server port
  let websocketUrl: string = websocketProtocol + "://" + websocketHost + loc.pathname + "/ws";

  const maxSceneContainerWidth = windowWidth
  const maxSceneContainerHeight = windowHeight

  const sceneContainerWidth = maxSceneContainerWidth
  const sceneContainerHeight = maxSceneContainerHeight

  useEffect(() => {
    // joshr: This effect is intended to detect when a react-dev-server remount causes a websocket disconnect, so that
    // we can avoid showing a disconnect error and exiting the simulation in that case
    return () => {
      if (dynamicState.webSocket != null) {
        dynamicState.webSocket.close()
        dynamicState.webSocket = null
      }
    }
  }, [])

  const handleSimulationDataSnapshotReceived = (initialSimulationData: ClientSimulationData) => {
    console.log("Create new simulation for initial data")
    if (dynamicState.simulation == null) {
      setForceUpdateCounter(dynamicState.forceRenderIndex + 1)
      dynamicState.simulation = new GameSimulation(
        initialSimulationData,
        (newSimulationData) => {
          dynamicState.forceUpdate()
        },
        (type: string, data: any) => {
          if (dynamicState.webSocket != null) {
            sendWebSocketMessage(dynamicState.webSocket, type, data)
          } else {
            console.error("sendMessageImplementation can't send message because no web socket is available")
          }
        }
      )
    } else {
      throw new Error("Unexpected snapshot after initial simulation data")
    }
  }

  useWebSocket(websocketUrl, {
    onOpen: (event) => {
      console.log('WebSocket connection established.', event, "userId", dynamicState.userId, "clientId", dynamicState.clientId)
      dynamicState.webSocket = event.target as WebSocket

      dynamicState.sendWebSocketMessage("ConnectionRequest", {
        userId: dynamicState.userId,
        clientId: dynamicState.clientId,
        simulationId: simulationId
      })
    },
    onMessage: (event) => {
      const data: string = event.data
      handleWebSocketMessage(data, handleSimulationDataSnapshotReceived, dynamicState.simulation);
    },
    onClose: (event) => {
      console.log("WebSocket onClose event", event)

      if (dynamicState.webSocket != null) {
        dynamicState.webSocket = null
        alert("Disconnected")
        props.exit()
      }
    }
  })


  const hasSimulationData = dynamicState.simulation != null
  const hasWebSocket = dynamicState.webSocket != null

  useEffect(() => {
    const webSocket = dynamicState.webSocket

    if (dynamicState.simulation != null &&
      phaserContainerDiv != null &&
      dynamicState.phaserScene == null &&
      webSocket != null) {
      console.log("CREATE PHASER")
      setSceneLoadComplete(false)

      const config: Phaser.Types.Core.GameConfig = {
        type: props.shouldForceWebGl ? Phaser.WEBGL : (props.shouldAllowWebGl ? Phaser.AUTO : Phaser.CANVAS),
        backgroundColor: '#1A1A1A',
        width: "100%",
        height: "100%",
        parent: phaserContainerDiv,
        physics: {
          default: "arcade",
          arcade: {
            gravity: {y: 0}
          }
        },
        pixelArt: false,
        scale: {
          mode: Phaser.Scale.RESIZE,
          autoCenter: Phaser.Scale.CENTER_BOTH
        }
      }

      const phaserGame = new Phaser.Game(config)

      const sceneContext: SimulationSceneContext = {
        dynamicState: dynamicState,
        sendWebSocketMessage: (type: string, data) => {
          sendWebSocketMessage(webSocket, type, data)
        },
        setSelectedEntityId: (entityId) => {
          setSelectedEntityId(entityId)
        },
        closePanels: () => setShowingPanels([]),
        showHelpPanel: () => {
          setShouldShowHelpPanel(true)
        }
      }

      const newPhaserScene = new SimulationScene(dynamicState.simulation, sceneContext)
      newPhaserScene.onLoadComplete(() => {
        setSceneLoadComplete(true)
      })

      phaserGame.scene.add("main", newPhaserScene, true)
      dynamicState.phaserScene = newPhaserScene

      return () => {
        console.log("destroy phaser")
        if (newPhaserScene.game != null) {
          newPhaserScene.game.destroy(true)
        }
        dynamicState.phaserScene = null
      }
    }
  }, [phaserContainerDiv, hasSimulationData, hasWebSocket]);


  let totalCost = 0.0
  if (dynamicState.simulation != null) {
    for (let entity of dynamicState.simulation.entities) {
      const agentComponent = entity.getComponentOrNull<AgentComponentData>("AgentComponentData")

      if (agentComponent != null) {
        totalCost += agentComponent.data.costDollars
      }
    }
  }

  const simulation = dynamicState.simulation

  const useMobileLayout = windowWidth < 600

  function isShowingPanel(panel: PanelTypes): boolean {
    return showingPanels.includes(panel)
  }

  function renderActivityPanel() {
    return isShowingPanel(PanelTypes.Activity)
      ? <ActivityPanel dynamicState={dynamicState}
                       windowHeight={windowHeight}
                       windowWidth={windowWidth}
                       userControlledEntity={userControlledEntity}
                       useMobileLayout={useMobileLayout}
      /> : null
  }

  function renderInventoryPanel() {
    return isShowingPanel(PanelTypes.Inventory)
      ? <MyInventoryPanel dynamicState={dynamicState}
                          windowHeight={windowHeight}
                          windowWidth={windowWidth}
                          userControlledEntity={userControlledEntity}
                          useMobileLayout={useMobileLayout}
      /> : null
  }

  function renderCraftingPanel() {
    return isShowingPanel(PanelTypes.Crafting)
      ? <CraftingPanel dynamicState={dynamicState}
                       windowHeight={windowHeight}
                       windowWidth={windowWidth}
                       userControlledEntity={userControlledEntity}
                       useMobileLayout={useMobileLayout}
      /> : null
  }

  const selectedEntity = selectedEntityId != null
    ? simulation?.getEntityOrNull(selectedEntityId)
    : null

  function renderInspectionPanel() {
    return simulation != null && selectedEntity != null ?
      <InspectionPanel dynamicState={dynamicState}
                       windowHeight={windowHeight}
                       windowWidth={windowWidth}
                       useMobileLayout={useMobileLayout}
                       selectedEntity={selectedEntity}/> : null
  }

  function renderInspectButton() {
    const isShowingInspect = selectedEntity != null
    var button: HTMLButtonElement | null = null

    function selectNearestEntity() {
      if (userControlledEntity == null) {
        return
      }

      const playerPosition = resolveEntityPositionForCurrentTime(userControlledEntity)

      let nearestDistance = 10000.0
      let nearestEntity: Entity | null = null

      const simulation = dynamicState.simulation!

      for (const entity of simulation.entities) {
        const positionComponent = entity.getComponentOrNull<PositionComponentData>("PositionComponentData")
        const userControlledComponent = entity.getComponentOrNull<UserControlledComponentData>("UserControlledComponentData")

        if (positionComponent != null && (userControlledComponent == null || userControlledComponent.data.userId !== dynamicState.userId)) {
          const position = resolvePositionForCurrentTime(positionComponent)
          let searchScore = Vector2.distance(playerPosition, position)

          if (nearestEntity?.getComponentOrNull<AgentComponentData>("AgentComponentData") != null) {
            searchScore *= 0.3
          }

          if (searchScore < nearestDistance) {
            nearestDistance = searchScore
            nearestEntity = entity
          }
        }
      }

      if (nearestEntity != null) {
        setSelectedEntityId(nearestEntity.entityId)
      }
    }

    return <div
      key={"inspect-button"}
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
        ref={actionIcon => button = actionIcon}
        color={isShowingInspect ? "blue" : "gray"}
        size={44}
        variant={isShowingInspect ? "filled" : "subtle"}
        onClick={() => {
          if (isShowingInspect) {
            setSelectedEntityId(null)
          } else {
            selectNearestEntity()
          }

          button?.blur()
        }}
      >
        <IconInfoCircle/>
      </ActionIcon>
    </div>
  }

  function renderPanelButton(
    panelType: PanelTypes,
    icon: JSX.Element
  ) {
    var button: HTMLButtonElement | null = null

    return <div
      key={"panel-button:" + panelType}
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
        ref={actionIcon => button = actionIcon}
        color={isShowingPanel(panelType) ? "blue" : "gray"}
        size={44}
        variant={isShowingPanel(panelType) ? "filled" : "subtle"}
        onClick={() => {
          if (isShowingPanel(panelType)) {
            setShowingPanels(showingPanels.filter(it => it !== panelType))
          } else {
            setShowingPanels([...showingPanels, panelType])
          }

          button?.blur()
        }}
      >
        {icon}
      </ActionIcon>
    </div>
  }

  const chatAreaWidth = Math.min(windowWidth - 20, 500)
  const remainingWindowWidth = windowWidth - chatAreaWidth

  const autoInteraction = dynamicState.phaserScene?.calculatedAutoInteraction
  let actionButtonRef: HTMLButtonElement | null = null
  let actionButton: JSX.Element | null = null
  if (autoInteraction != null && autoInteraction.targetEntity != null) {
    let actionTitle = autoInteraction.actionTitle

    actionButton = <Button
      key={"action-button"}
      ref={button => actionButtonRef = button}
      fullWidth
      color={"blue"}
      variant={"filled"}
      leftIcon={autoInteraction.actionIcon}
      style={{
        height: 44
      }}
      onClick={() => {
        actionButtonRef?.blur()
        dynamicState?.phaserScene?.autoInteract()
      }}>
      {actionTitle}
    </Button>
  }

  var helpButtonRef: HTMLButtonElement | null = null

  function renderGameUi() {
    return <React.Fragment>
      <div
        key="right-header"
        style={{
          display: "flex",
          flexDirection: "row",
          top: 5,
          right: 5,
          position: "absolute",
          gap: 10
        }}
      >
        <div style={{
          display: "flex",
          flexDirection: "row",
          alignItems: "right",
          gap: 10
        }}>
          <Text style={{
            fontSize: 10,
            color: "white",
            textAlign: "right"
          }}>Cost ${totalCost.toFixed(2)}</Text>

          <Text style={{
            fontSize: 10,
            color: "white",
            textAlign: "right"
          }}>v0.1.1</Text>
        </div>

        <div
          key="help-button-container"
          style={{
            display: "flex",
            flexDirection: "row",
            padding: 0,
            alignItems: "center",
            backgroundColor: "rgba(255, 255, 255, 0.5)",
            height: 35,
            backdropFilter: "blur(5px)",
            WebkitBackdropFilter: "blur(5px)",
            borderRadius: 5,
            gap: 10
          }}
        >
          <ActionIcon ref={button => helpButtonRef = button} size={35} variant={"subtle"} onClick={() => {
            setShouldShowHelpPanel(!shouldShowHelpPanel)
            helpButtonRef?.blur()
          }}>
            <IconQuestionMark size={18}/>
          </ActionIcon>
        </div>
      </div>

      {!useMobileLayout && simulation != null ? <div
        key={"left-panel-container"}
        style={{
          top: useMobileLayout ? windowHeight * 0.5 : 120,
          bottom: 130,
          position: "absolute",
          left: 10,
          display: "flex",
          flexDirection: "row",
          gap: 6
        }}
      >
        {renderActivityPanel()}
        {renderInspectionPanel()}
      </div> : null}

      {!useMobileLayout && simulation != null ? <div
        key={"right-panel-container"}
        style={{
          top: useMobileLayout ? windowHeight * 0.5 : 120,
          bottom: 130,
          position: "absolute",
          right: 10,
          display: "flex",
          flexDirection: "column",
          gap: 6
        }}
      >
        {renderInventoryPanel()}
        {renderCraftingPanel()}
      </div> : null}

      {simulation != null && shouldShowDebugPanel ?
        <DebugPanel dynamicState={dynamicState} windowHeight={windowHeight}
                    selectedEntityId={selectedEntityId}/> : null}

      <div
        style={{
          top: "auto",
          position: "absolute",
          bottom: 0,
          left: remainingWindowWidth / 2,
          width: chatAreaWidth,
          display: "flex",
          flexDirection: "column",
          gap: 5,
          paddingBottom: 5
        }}
      >
        {useMobileLayout ? renderInventoryPanel() : null}
        {useMobileLayout ? renderActivityPanel() : null}
        {useMobileLayout ? renderCraftingPanel() : null}
        {useMobileLayout ? renderInspectionPanel() : null}

        <div
          key={"button-row"}
          style={{
            paddingBottom: 5,
            borderRadius: 6,
            padding: 4,
            width: "100%",
            flexBasis: 50,
            display: "flex",
            flexDirection: "row",
            gap: 10
          }}
        >
          {renderPanelButton(PanelTypes.Activity, <IconMessages size={24}/>)}
          {renderPanelButton(PanelTypes.Inventory, <IconGridDots size={24}/>)}
          {renderPanelButton(PanelTypes.Crafting, <IconHammer size={24}/>)}
          {renderInspectButton()}
          <div style={{
            flexBasis: 30
          }}/>

          {actionButton}
        </div>

        <ChatInputArea windowWidth={windowWidth}
                       dynamicState={dynamicState}
                       showingPanels={showingPanels}
                       setShowingPanels={setShowingPanels}/>
      </div>
    </React.Fragment>
  }

  return <div
    style={{
      backgroundColor: "white",
      display: "flex",
      flexDirection: "column",
      gap: 0,
      alignItems: "center",
    }}
  >
    <div
      key={"column-split"}
      style={{
        display: "flex",
        flexDirection: "row",
        flexGrow: 1,
        width: "100%"
      }}
    >
      <div
        key="game-phase-scene-container"
        style={{
          height: sceneContainerHeight,
          flexBasis: sceneContainerWidth,
          position: "relative"
        }}
      >
        <div
          ref={setPhaserContainerDiv}
          style={{
            width: windowWidth,
            height: "100%",
            position: "relative"
          }}
        />

        <div
          key="left-header"
          style={{
            display: "flex",
            flexDirection: "row",
            paddingRight: 10,
            paddingLeft: 10,
            position: "absolute",
            top: 5,
            left: 5,
            gap: 10
          }}
        >
          <div
            key="close-button-container"
            style={{
              display: "flex",
              flexDirection: "row",
              padding: 0,
              alignItems: "center",
              backgroundColor: "rgba(255, 255, 255, 0.5)",
              height: 35,
              backdropFilter: "blur(5px)",
              WebkitBackdropFilter: "blur(5px)",
              borderRadius: 5,
              gap: 10
            }}
          >
            <ActionIcon size={35} variant={"subtle"} onClick={() => {
              props.exit()
            }}>
              <IconArrowLeft size={18}/>
            </ActionIcon>
          </div>
        </div>

        {sceneLoadComplete ? renderGameUi() : null}
      </div>
    </div>

    {simulation != null && shouldShowHelpPanel ?
      <HelpPanel dynamicState={dynamicState} windowHeight={windowHeight} windowWidth={windowWidth}
                 close={() => {
                   setShouldShowHelpPanel(false)
                 }}
      /> : null}
  </div>
}

