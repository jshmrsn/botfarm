import React, {useState} from "react";
import {ActionIcon, Textarea} from "@mantine/core";
import {IconChartBubble, IconGridDots, IconMessageCircle, IconMessages, IconSend} from "@tabler/icons-react";
import {PanelTypes} from "./SimulationComponent";
import {DynamicState} from "./DynamicState";


interface ChatInputAreaProps {
  windowWidth: number
  dynamicState: DynamicState
  showingPanels: PanelTypes[]
  setShowingPanels: (panels: PanelTypes[]) => void
  notifyChatInputIsFocused: (focused: boolean) => void
}

export function ChatInputArea(props: ChatInputAreaProps): JSX.Element {
  const windowWidth = props.windowWidth
  const dynamicState = props.dynamicState

  const [previousTypedPrompt, setPreviousTypedPrompt] = useState(localStorage.getItem("previousTypedPrompt") || "")
  const [typedPrompt, setTypedPrompt] = useState("")

  function sendTypedPrompt() {
    if (typedPrompt.length > 0) {
      dynamicState.sendWebSocketMessage("AddCharacterMessageRequest", {
        message: typedPrompt
      })

      localStorage.setItem("previousTypedPrompt", typedPrompt)
      setPreviousTypedPrompt(typedPrompt)
      setTypedPrompt("")
    }
  }

  const chatAreaWidth = Math.min(windowWidth - 20, 500)
  const remainingWindowWidth = windowWidth - chatAreaWidth

  var sendButton: HTMLButtonElement | null = null

  return <div
    key={"chat-input-area"}
    style={{
      backgroundColor: "rgba(255, 255, 255, 0.5)",
      bottom: 10,
      backdropFilter: "blur(5px)",
      WebkitBackdropFilter: "blur(5px)",
      borderRadius: 6,
      left: remainingWindowWidth / 2,
      width: chatAreaWidth
    }}
  >
    <div style={{
      display: "flex",
      flexDirection: "row",
      width: "100%",
      paddingLeft: 4,
      paddingRight: 2,
      paddingTop: 2,
      paddingBottom: 2,
      alignItems: "center"
    }}>
      <div style={{
        flexDirection: "column",
        flexGrow: 1.0
      }}>
        <Textarea
          value={typedPrompt}
          ref={textArea => {
            dynamicState.chatTextArea = textArea
          }}
          onChange={(event) => setTypedPrompt(event.target.value)}
          onFocus={(focusEvent) => {
            props.notifyChatInputIsFocused(true)
          }}
          onBlur={() => {
            props.notifyChatInputIsFocused(false)
          }}
          autosize={true}
          style={{
            // height: "100%"
          }}
          placeholder="Chat..."
          rows={5}
          onKeyDown={(event) => {
            // console.log("onKeyDown", event)
            if (event.code === "Escape") {
              (event.target as any).blur()
            } else if (event.code === "ArrowUp") {
              if (typedPrompt === "") {
                setTypedPrompt(previousTypedPrompt)
              }
            } else if (event.code === "Enter" && !event.shiftKey) {
              event.preventDefault()
              sendTypedPrompt()
            }
          }}
        />
      </div>

      <ActionIcon
        size={50}
        color={typedPrompt.length > 0 ? "default" : "gray"}
        ref={actionIcon => sendButton = actionIcon}
        onClick={() => {
          sendTypedPrompt()
          sendButton?.blur()
        }}
      >
        <IconSend size="1.125rem"/>
      </ActionIcon>
    </div>
  </div>
}

