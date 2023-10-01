import {MantineProvider} from '@mantine/core';
import {SelectSimulationComponent} from "./SelectSimulationComponent";
import {useState} from "react";
import {SimulationComponent} from "./SimulationComponent";
import {
  createBrowserRouter,
  RouterProvider,
  useNavigate
} from "react-router-dom";
import {UserId} from "../simulation/Simulation";
import {generateId} from "../misc/utils";

export default function App() {
  const [shouldAllowWebGl, setShouldAllowWebGl] = useState(true)
  const [shouldForceWebGl, setShouldForceWebGl] = useState(false)

  const storedUserId = localStorage.getItem("userId")

  let userId: UserId
  if (storedUserId == null) {
    userId = generateId()
    console.log("Storing userId", userId)
    localStorage.setItem("userId", userId)
  } else {
    userId = storedUserId
  }

  const router = createBrowserRouter([
    {
      path: "/",
      element: <SelectSimulationComponent
        shouldAllowWebGl={shouldAllowWebGl}
        shouldForceWebGl={shouldForceWebGl}
        setShouldAllowWebGl={setShouldAllowWebGl}
        setShouldForceWebGl={setShouldForceWebGl}
        userId={userId}
      />
    },
    {
      path: "/simulation/:simulationId",
      element: <SimulationComponent
        shouldAllowWebGl={shouldAllowWebGl}
        shouldForceWebGl={shouldForceWebGl}
        userId={userId}
        isReplay={false}
        />
    },
    {
      path: "/replay/:simulationId",
      element: <SimulationComponent
        shouldAllowWebGl={shouldAllowWebGl}
        shouldForceWebGl={shouldForceWebGl}
        userId={userId}
        isReplay={true}
      />
    },
  ]);

  return (
    <MantineProvider
      withGlobalStyles
      withNormalizeCSS
      theme={{
      }}
    >
      <RouterProvider router={router} />
    </MantineProvider>
  );
}
