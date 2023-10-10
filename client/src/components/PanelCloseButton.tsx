import React, {ReactElement} from "react";
import {IconX} from "@tabler/icons-react";
import styled from "styled-components";

const HighlightButton = styled.div`
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-items: center;
  border-radius: 15px;
  padding: 2px;
  background-color: rgba(255, 255, 255, 0.25);

  &:hover {
    background-color: rgba(255, 255, 255, 0.75);
  }

  cursor: pointer;
`;

export function PanelCloseButton(props: {
  close: () => void
  top?: number,
  right?: number,
  size?: number
}): ReactElement {
  const size = props.size || 40

  return <div
    style={{
      height: 0,
      backgroundColor: "red",
      position: "relative",
      top: 0,
      width: "100%"
    }}
  >
    <HighlightButton
      key="close-button-container"
      style={{
        position: "absolute",
        top: props.top || 0,
        right: props.right || 0,
        display: "flex",
        flexDirection: "row",
        padding: 0,
        alignItems: "center",
        height: size,
        width: size,
        // backdropFilter: "blur(5px)",
        // WebkitBackdropFilter: "blur(5px)",
        borderRadius: 5,
        gap: 10,
        justifyContent: "center"
      }}
      onClick={event => {
        event.stopPropagation()
        props.close()
      }}
    >
      <IconX color={"rgba(0, 0, 0, 0.3)"} size={size / 1.5}/>
    </HighlightButton>
  </div>
}