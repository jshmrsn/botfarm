import {Simulation} from "../simulation/Simulation";
import {ClientSimulationData} from "../simulation/EntityData";

export class GameSimulation extends Simulation {
  constructor(
    initialSimulationData: ClientSimulationData,
    onSimulationDataChanged: (newData: ClientSimulationData) => void,
    sendMessageImplementation: (type: string, data: any) => void
  ) {
    super(initialSimulationData, onSimulationDataChanged, sendMessageImplementation)
  }
}