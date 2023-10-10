import {MantineProvider} from '@mantine/core';
import {SelectSimulationComponent} from "./SelectSimulationComponent";
import {useState} from "react";
import {GameSimulationComponent} from "./GameSimulationComponent";
import {createBrowserRouter, RouterProvider} from "react-router-dom";
import {UserId, UserSecret} from "../simulation/Simulation";
import {generateId} from "../misc/utils";
import {AdminRequest, buildAdminRequestForSecret} from "./AdminRequest";


export default function App() {
  const [shouldAllowWebGl, setShouldAllowWebGl] = useState(true)
  const [shouldForceWebGl, setShouldForceWebGl] = useState(false)

  const storedUserId = localStorage.getItem("userId")
  const storedUserSecret = localStorage.getItem("userSecret")
  const storedAdminSecret = localStorage.getItem("adminSecret")

  const [adminSecret, setAdminSecret] = useState(storedAdminSecret ?? "")

  let userId: UserId
  if (storedUserId == null) {
    userId = generateId()
    console.log("Storing userId", userId)
    localStorage.setItem("userId", userId)
  } else {
    userId = storedUserId
  }

  let userSecret: UserSecret
  if (storedUserSecret == null) {
    userSecret = generateId()
    localStorage.setItem("userSecret", userSecret)
  } else {
    userSecret = storedUserSecret
  }

  const buildAdminRequest: () => AdminRequest | null = () => {
    if (adminSecret.length > 0) {
      return buildAdminRequestForSecret(adminSecret)
    } else {
      return null
    }
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
        userSecret={userSecret}
        adminSecret={adminSecret}
        setAdminSecret={value => {
          localStorage.setItem("adminSecret", value)
          setAdminSecret(value)
        }}
        buildAdminRequest={buildAdminRequest}
      />
    },
    {
      path: "/simulation/:simulationId",
      element: <GameSimulationComponent
        shouldAllowWebGl={shouldAllowWebGl}
        shouldForceWebGl={shouldForceWebGl}
        userId={userId}
        userSecret={userSecret}
        buildAdminRequest={buildAdminRequest}
      />
    }
  ]);

  return (
    <MantineProvider
      withGlobalStyles
      withNormalizeCSS
      theme={{}}
    >
      <RouterProvider router={router}/>
    </MantineProvider>
  );
}
