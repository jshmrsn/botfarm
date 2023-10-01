import {Fragment, useEffect, useState} from "react";

import {ActionIcon, Text} from "@mantine/core";
import {IconTrashFilled} from "@tabler/icons-react";
import {apiRequest} from "../api";
import {SimulationId} from "../simulation/Simulation";
import {useNavigate} from "react-router-dom";
import styled from "styled-components";
import {ScenarioInfo} from "../simulation/EntityData";

export interface SimulationInfo {
  simulationId: SimulationId
  scenarioInfo: ScenarioInfo
  isTerminated: boolean
  startedAtUnixTime: number
}


export interface ListSimulationsResult {
  scenarios: ScenarioInfo[]
  simulations: SimulationInfo[]
  terminatedSimulations: SimulationInfo[]
}

interface SelectSimulationProps {
  shouldAllowWebGl: boolean
  shouldForceWebGl: boolean

  setShouldAllowWebGl: (value: boolean) => void
  setShouldForceWebGl: (value: boolean) => void
  userId: string
}

interface CreateSimulationResponse {
  simulationInfo: SimulationInfo
}

const ListButton = styled.div`
  display: flex;
  flex-direction: row;
  gap: 10px;
  align-items: center;
  border-radius: 3px;
  background: rgba(255, 255, 255, 0.5);
  padding: 5px;
  padding-left: 10px;

  &:hover {
    background-color: rgba(255, 255, 255, 0.75);
  }

  cursor: pointer;
`;

export const SelectSimulationComponent = (props: SelectSimulationProps) => {
  const [isCreatingSimulation, setIsCreatingSimulation] = useState(false)
  const [listResult, setListResult] = useState<ListSimulationsResult | null>(null)
  const navigate = useNavigate()
  const userId = props.userId

  const refreshList = () => {
    setListResult(null)

    apiRequest("list-simulations", {
      userId: userId
    }, (response: ListSimulationsResult) => {
      setListResult(response)
    })
  }

  const simulationSelected = (simulationInfo: SimulationInfo) => {
    navigate(`/simulation/${simulationInfo.simulationId}`)
  }

  useEffect(() => {
    refreshList()
  }, []);

  function renderContent(listResult: ListSimulationsResult) {
    return <Fragment>
      <Text size={20} weight={"bold"}>Scenarios</Text>
      <div
        style={{
          display: "flex",
          flexDirection: "column",
          borderRadius: 3,
          padding: 5,
          gap: 5,
          marginBottom: 20
        }}
      >
        {listResult.scenarios.map((scenario, scenarioIndex) => {
          return <ListButton
            key={"scenario:" + scenarioIndex + ":" + scenario.identifier}
            style={{}}
            onClick={() => {
              setIsCreatingSimulation(true)
              apiRequest("create-simulation", {
                scenarioIdentifier: scenario.identifier
              }, (response: CreateSimulationResponse) => {
                simulationSelected(response.simulationInfo)
              })
            }}
          >
            <Text>{scenario.name || scenario.identifier}</Text>
          </ListButton>
        })}
      </div>

      <Text size={20} weight={"bold"}>Simulations</Text>

      <div
        style={{
          display: "flex",
          flexDirection: "column",
          borderRadius: 3,
          padding: 5,
          gap: 5
        }}
      >
        {
          listResult.simulations.length === 0 ?
            <Text>No simulations found.</Text> :
            listResult.simulations.map((simulationInfo, index) => <ListButton
                key={index + ":" + simulationInfo.simulationId}
                style={{}}
                onClick={() => {
                  simulationSelected(simulationInfo)
                }}
              >
                <Text>{simulationInfo.simulationId} ({simulationInfo.scenarioInfo.name || simulationInfo.scenarioInfo.identifier})</Text>

                <div
                  key={index + ":" + simulationInfo.simulationId}
                  style={{
                    flexGrow: 1.0,
                    flexBasis: 0
                  }}
                />

                <ActionIcon
                  variant="filled"
                  color={"red"}
                  onClick={(event) => {
                    event.stopPropagation()
                    apiRequest("terminate-simulation", {
                      simulationId: simulationInfo.simulationId
                    }, (response) => {
                      refreshList()
                    })
                  }}
                >
                  <IconTrashFilled size="1rem"/>
                </ActionIcon>
              </ListButton>
            )}
      </div>

      {listResult.terminatedSimulations.length === 0
        ? null
        : <div>
          <Text size={20} weight={"bold"}>Terminated Simulations</Text>

          <div
            style={{
              display: "flex",
              flexDirection: "column",
              borderRadius: 3,
              padding: 5,
              gap: 5
            }}
          >
            {
              listResult.terminatedSimulations.map((simulationInfo, index) => <ListButton
                  key={index + ":" + simulationInfo.simulationId}
                  style={{}}
                  onClick={() => {
                    simulationSelected(simulationInfo)
                  }}
                >
                  <Text>{simulationInfo.simulationId} ({simulationInfo.scenarioInfo.name || simulationInfo.scenarioInfo.identifier})</Text>

                  <div
                    key={index + ":" + simulationInfo.simulationId}
                    style={{
                      flexGrow: 1.0,
                      flexBasis: 0
                    }}
                  />
                </ListButton>
              )
            }
          </div>
        </div>}
    </Fragment>
  }

  return <div
    style={{
      display: "flex",
      flexDirection: "column",
      paddingTop: 0,
      gap: 5,
      height: "100%",
      bottom: 0
    }}
  >
    <div
      key={"header"}
      style={{
        display: "flex",
        flexDirection: "row",
        alignItems: "center",
        width: "100%",
        gap: 10,
        padding: 10,
        paddingRight: 30,
        paddingLeft: 15,
        backgroundColor: "rgba(163, 201, 116, 0.2)"
      }}
    >
      <img src="/logo192.png"
           alt={"Logo"}
           width={60}
           height={60}/>

      <Text size={30} color={"rgba(0, 0, 0, 0.75)"}>Botfarm</Text>
      <div key={"spacer"} style={{flexGrow: 1.0}}/>

      <a href={"https://github.com/jshmrsn/botfarm"}>
        <img src="/github.png"
             alt={"Logo"}
             width={34}
             height={34}/>
      </a>
    </div>

    <div
      key={"content"}
      style={{
        paddingLeft: 10,
        paddingRight: 10,
        paddingBottom: 10,
        overflowY: "auto",
        display: "flex",
        flexDirection: "column"
      }}
    >
      {listResult ? renderContent(listResult) : <Text>Loading...</Text>}

      <div key={"spacer"} style={{flexGrow: 1.0}}/>

      <div
        key={"footer"}
        style={{
          display: "flex",
          flexDirection: "row",
          alignItems: "center",
          justifyItems: "center",
          width: "100%"
        }}
      >
      </div>
    </div>
  </div>
}

