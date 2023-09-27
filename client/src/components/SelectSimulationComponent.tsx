import {useEffect, useRef, useState} from "react";
import {ActionIcon, Button, Switch, Text} from "@mantine/core";
import {IconPlayerPlayFilled, IconTrashFilled} from "@tabler/icons-react";
import {apiRequest} from "../api";


import * as Phaser from 'phaser';
import {SimulationScene} from "../game/SimulationScene";

interface ListSimulationsResultEntry {
  simulationId: string
}

interface ListSimulationsResult {
  simulations: ListSimulationsResultEntry[]
}

interface SelectSimulationProps {
  simulationSelected: (simulationId: string) => void
  shouldAllowWebGl: boolean
  shouldForceWebGl: boolean

  setShouldAllowWebGl: (value: boolean) => void
  setShouldForceWebGl: (value: boolean) => void
}

interface CreateSimulationResponse {
  simulationId: string
}


export const SelectSimulationComponent = (props: SelectSimulationProps) => {
  const [isCreatingSimulation, setIsCreatingSimulation] = useState(false)
  const [listResult, setListResult] = useState<ListSimulationsResult | null>(null);

  const refreshList = () => {
    setListResult(null)

    apiRequest("list-simulations", {}, (response: ListSimulationsResult) => {
      setListResult(response)
    })
  }

  useEffect(() => {
    refreshList()
  }, []);


  return <div
    style={{
      display: "flex",
      flexDirection: "column",
      padding: 20,
      gap: 5,
      height: "100%",
      bottom: 0
    }}
  >
    <Text>Simulations</Text>

    <Button
      rightIcon={<IconPlayerPlayFilled size="1.05rem" stroke={1.5}/>}
      pr={12}
      disabled={isCreatingSimulation}
      loading={isCreatingSimulation}
      onClick={() => {
        setIsCreatingSimulation(true)
        apiRequest("create-simulation", {}, (response: CreateSimulationResponse) => {
          props.simulationSelected(response.simulationId)
        })
      }}
    >
      Create New Simulation
    </Button>

    <div
      style={{
        display: "flex",
        flexDirection: "column",
        borderRadius: 3,
        background: "#DDD",
        padding: 5
      }}
    >
      {(listResult == null) ? (
        <Text>Loading simulations...</Text>
      ) : (
        listResult.simulations.length == 0 ?
          <Text>No simulations found.</Text> :
          listResult.simulations.map((simulation, index) => <div
            key={index + ":" + simulation.simulationId}
            style={{
              display: "flex",
              flexDirection: "row",
              gap: 10,
              alignItems: "center",
              borderRadius: 3,
              background: "#FFF",
              padding: 5
            }}
          >
            <Text>{simulation.simulationId}</Text>

            <div
              key={index + ":" + simulation.simulationId}
              style={{
                flexGrow: 1.0,
                flexBasis: 0
              }}
            />

            <ActionIcon
              variant="filled"
              color={"red"}
              onClick={() => {
                apiRequest("terminate-simulation", {
                  simulationId: simulation.simulationId
                }, (response) => {
                  refreshList()
                })
              }}
            >
              <IconTrashFilled size="1rem"/>
            </ActionIcon>

            <Button
              rightIcon={<IconPlayerPlayFilled size="1.05rem" stroke={1.5}/>}
              pr={12}
              onClick={() => {
                props.simulationSelected(simulation.simulationId)
              }}
            >
              Enter
            </Button>
          </div>)
      )}
    </div>


    <div
      style={{
        flexGrow: 1.0
      }}
    />

    <div
      style={{
        display: "flex",
        flexDirection: "row",
        gap: 20
      }}
    >

      <Switch checked={props.shouldAllowWebGl}
              onChange={(event) => props.setShouldAllowWebGl(event.currentTarget.checked)}
              label={"Allow WebGL"}
      />
      <Switch checked={props.shouldForceWebGl}
              onChange={(event) => props.setShouldForceWebGl(event.currentTarget.checked)}
              label={"Force WebGL"}
      />
    </div>
  </div>
}

