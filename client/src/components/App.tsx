import {MantineProvider} from '@mantine/core';
import {SelectSimulationComponent} from "./SelectSimulationComponent";
import {useState} from "react";
import {SimulationComponent} from "./SimulationComponent";

export default function App() {
  const [selectedSimulationId, setSelectedSimulationId] = useState<string | null>(null)

  const [shouldAllowWebGl, setShouldAllowWebGl] = useState(true)
  const [shouldForceWebGl, setShouldForceWebGl] = useState(false)

  return (
    <MantineProvider
      withGlobalStyles
      withNormalizeCSS
      theme={{
      }}
    >
      {selectedSimulationId != null
        ? <SimulationComponent
          simulationId={selectedSimulationId}
          shouldAllowWebGl={shouldAllowWebGl}
          shouldForceWebGl={shouldForceWebGl}
          exit={() => {
            setSelectedSimulationId(null)
          }}/>
        : <SelectSimulationComponent
          simulationSelected={(simulationId) => {
            setSelectedSimulationId(simulationId)
          }}
          shouldAllowWebGl={shouldAllowWebGl}
          shouldForceWebGl={shouldForceWebGl}
          setShouldAllowWebGl={setShouldAllowWebGl}
          setShouldForceWebGl={setShouldForceWebGl}
        />
      }
    </MantineProvider>
  );
}
