import React, {ReactElement, useState} from "react";
import {ActionIcon, Textarea} from "@mantine/core";
import {IconSend} from "@tabler/icons-react";
import {PanelType} from "./GameSimulationComponent";
import {DynamicState} from "./DynamicState";


export function ChatInputArea(
  props: {
    windowWidth: number
    dynamicState: DynamicState
    showingPanels: PanelType[]
    setShowingPanels: (panels: PanelType[]) => void
    notifyChatInputIsFocused: (focused: boolean) => void
  }
): ReactElement {
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


  let sendButton: HTMLButtonElement | null = null

  return <div
    key={"chat-input-area"}
    style={{
      backgroundColor: "rgba(255, 255, 255, 0.5)",
      bottom: 10,
      backdropFilter: "blur(5px)",
      WebkitBackdropFilter: "blur(5px)",
      borderRadius: 6,
      width: "100%",
      pointerEvents: "auto"
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

