import {Code, Text} from "@mantine/core";
import {Simulation} from "../simulation/Simulation";
import {Entity} from "../simulation/Entity";

interface RawEntityDataDebugProps {
  entity: Entity
}

export function RawEntityDataDebugComponent(
  props: RawEntityDataDebugProps
) {
  const entity = props.entity

  return <div
    key={`raw-entity-data:${entity.entityId}`}
    style={{
      display: "flex",
      flexDirection: "column",
      gap: 10,
      borderRadius: 3,
      // background: "#EEE",
      padding: 5
    }}
  >
    <Text weight={"bold"} size={20}>Raw Entity Data</Text>

    {entity.components.map((component, index) => {
      const componentType = component.serverTypeName

      const componentData = component.data
      const componentTypeSplit = componentType.split(".")
      const componentKeys = Object.keys(componentData).sort()
      const untypedComponentData = componentData as Record<any, any>

      return <div
        key={`component:${index}:${componentType}`}
        style={{
          display: "flex",
          flexDirection: "column",
          gap: 10,
          borderRadius: 3,
          background: "#FFF",
          padding: 5
        }}
      >
        <Text><b>{componentTypeSplit[componentTypeSplit.length - 1]}</b> ({componentType})</Text>

        {componentKeys.map(componentKey => {
          const componentValue: any = untypedComponentData[componentKey]

          return componentKey === "type" ? null : <div
            key={`component-key:${componentKey}`}
            style={{
              display: "flex",
              flexDirection: "row",
              gap: 10
            }}
          >
            <Text>{componentKey}</Text>
            <Code block>{JSON.stringify(componentValue, null, 2)}</Code>
          </div>
        })}
      </div>
    })}
  </div>
}

export function renderInspectMode(simulation: Simulation) {
  return <div style={{
    display: "flex",
    width: "100%",
    flexDirection: "column",
    marginTop: 20
  }}>
    <Text>Entities</Text>

    <div
      key={"entity-list"}
      style={{
        display: "flex",
        flexDirection: "column",
        // borderRadius: 3,
        // background: "#DDD",
        // padding: 5
      }}
    >
      {simulation.entities.map((entity, index) => <RawEntityDataDebugComponent
        key={`entity:${index}:${entity.entityId}`}
        entity={entity}
      />)
      }
    </div>

    <Text>Config Entries</Text>

    <div
      key={"config-entries-list"}
      style={{
        display: "flex",
        flexDirection: "column",
        // borderRadius: 3,
        // background: "#DDD",
        // padding: 5
      }}
    >
      {simulation.configs.map((config, index) => {
        const configType = config.type

        const configTypeSplit = configType.split(".")
        const configKeys = Object.keys(config).sort()
        const untypedConfig = config as Record<any, any>

        return <div
          key={`config:${index}:${config.key}`}
          style={{
            display: "flex",
            flexDirection: "column",
            gap: 10,
            borderRadius: 3,
            background: "#EEE",
            padding: 5
          }}
        >
          <Text>Config {config.key}</Text>
          {
            <div
              key={`component:${index}:${configType}`}
              style={{
                display: "flex",
                flexDirection: "column",
                gap: 10,
                borderRadius: 3,
                background: "#FFF",
                padding: 5
              }}
            >
              <Text><b>{configTypeSplit[configTypeSplit.length - 1]}</b> ({configType})</Text>

              {configKeys.map(configKey => {
                const configValue: any = untypedConfig[configKey]

                return configKey === "type" ? null : <div
                  key={`config-key:${configKey}`}
                  style={{
                    display: "flex",
                    flexDirection: "row",
                    // alignItems: "center",
                    gap: 10
                  }}
                >
                  <Text>{configKey}</Text>
                  <Code block>{JSON.stringify(configValue, null, 2)}</Code>
                </div>
              })}
            </div>
          }
        </div>;
      })
      }
    </div>
  </div>;
}