<div align="center">

<img src="https://github.com/jshmrsn/botfarm/blob/main/client/public/logo192.png?raw=true" width="120" />
<h1>Botfarm</h1>
<h3>Multiplayer Game Platform for Embodying Generative Agents</h3>
</div>

## Mission
Provide a game simulation platform that can be easily used to embody generative agents built with any technology stack via an API.

## Long-Term Research Motivation
Imagine a simulation where characters need both food and shelter to survive, but individual characters are only able to specialize in the production of a certain subset of goods.

Would a society of one hundred generative agents develop some form of an economy in this scenario? Would agents demonstrate a desire to survive as individuals? Would reputation-based relationships form? Are current LLM technologies capable of these kinds of intelligent social behaviors?

Could emergent moral dilemmas in these kind of social simulations pose challenges to alignment techniques?

By embodying generative agents in relatable social simulations and allowing players to interact with agents in real-time, could we uncover latent artificial intelligence capabilities in current models?

Given service costs, rate limits, and generation speeds, is it practical to run these types of simulations? What kind of techniques can be developed to mitigate current costs and limitations?

## Demo
You can [play in the browser](https://botfarm.app) (with AI agents disabled due to high API costs).

You can also [watch a replay in the browser](https://botfarm.app/simulation/744F5DA1) of an example interaction with a GPT-4 powered agent (Joe is an agent in this replay).


![Demo Screenshot](https://github.com/jshmrsn/botfarm/blob/main/docs/screenshots/screenshot-1.png?raw=true)

## How to Play
See [How to Play](docs/how-to-play.md)

## Current Features
- A playable browser-based 2D game with a basic implementation of harvesting, crafting, and farming mechanics.
- Characters in the game can be controlled either by human players or generative agents.
- Two systems for agents to communicate actions to take in the simulations:
    - Simple: JSON list of actions.
    - Advanced: JavaScript behavior script that calls into provided TypeScript delcarations, which allows agents to express desired behavior with conditional multi-step logic.
- Server-authorative real-time multiplayer.
- Switch between viewing the simulation from the perspective of a selected agent, or from spectator's perspective.
- Many quality-of-life features for investigating agent behavior, such as a detailed in-game timeline of an agent's observations, prompts, and prompt response, errors, etc..
- Suport for automatically pausing agents once a configurable total simulation cost is reached.
- Repeatable scenario system (using Kotlin code as configuration).
- Replay system with timeline scrubbing support.
- Playable on both desktop and mobile browsers.
- Full example agents written in Kotlin based on GPT-4, one based on using GPT-4 function calling to generate lists of actions, and another using GPT-4 to generate JavaScript behavior scripts.
- Minimal example agent written in Python

## Project Architecture

![Service Diagram](https://github.com/jshmrsn/botfarm/blob/main/docs/images/service-diagram.png?raw=true)

Both client and simulation server are written in type-safe languages (Kotlin and Typescript).
The first agent implementation is also written in Kotlin, but it communicates with the simulation over HTTP, so other languages could be used to implement agents.

A lightweight entity-component-system architecture is used to implement the game simulation.
Entity component state is synchronized to the client.
React is used to render the game's UI, with the goal being to represent the game UI as a function of the game state.
Similarly, the rendering of the game world uses a thin layer above Phaser3 to represent game visuals as a function of game state.
Basically, every frame the game describes what sprites should exist and how they should be configured for the current game state, vs. manually creating, updating, and destroying sprites in response to events.

The project is composed of three major components.
* **Simulation Server**
    - Runs the game simulation
    - Integrates with external agent servers via an HTTP API
        - Frequently sends requests to the agent server for each character controlled by an agent in a simulation
        - Each request includes a subset of game state that is observable by the agent's in-game character
        - The response can contain zero or more interactions that the agent would like its in-game character to carry out
    - Game simulation is written in a simple vanilla Kotlin framework (no dependency on a complex game engine)
    - Game state is represented as a list of entities with components
    - Game state sent to clients with websockets using JSON with delta compression
    - Game state is updated using tick-based and coroutine-based component systems
    - Written in Kotlin
    - Uses Ktor for serving HTTP and websocket connections
* **Agent servers**
    - Agent servers implement the HTTP API defined by the simulation server
    - Agent servers could be written in any tech stack / language
    - An agent server can provide multiple agent implementations
    - The current example agent server in this project server is written in Kotlin/Ktor using OpenAI for LLM-based agent logic
* **Client**
    - Written in Typescript
    - UI built with React and the Mantine UI component library
    - 2D game rendering built with Phaser3 (WebGL with Canvas fallback)

## License 
This project is licensed under GPLv3 (see the LICENSE file).
If this license is prohibitive for your use case, please open an issue to discuss further.

## Development Environment

This project was developed with IntelliJ IDEA for both the Kotlin and TypeScript portions. For iterating on Kotlin code, IntelliJ IDEA Community Edition is available for free. For the TypeScript portions of this project, it should be easy to use other IDEs, such as Visual Studio Code, etc..


### Build System Dependencies
- gradle
- JDK ([this project was developed with Amazon Corretto 11](https://docs.aws.amazon.com/corretto/latest/corretto-11-ug/downloads-list.html))
- npm


## Running Locally

> ## API Cost Warning
> If you provide an OpenAI API key and run a simulation with agents, the API costs can add up very quickly. 
> GPT-4 based agents currently cost about $0.50 USD per minute, per agent. 
> The cost multiplies for each agent that you add to a simulation.  
> That said, you can initially play the game without any agents to avoid incurring API costs.

### 1. **Simulation Server**
#### Running from IntelliJ (recommended)
Open the `simulation-server` directory as an IntelliJ project.
Use the Gradle tab to reload all gradle projects.
Run the "Game Simulation Server" configuration.

#### Running from the terminal (slower iteration)
From the `simulation-server` directory, run:
```console
gradle :shadowJar
java -jar ./build/libs/botfarm-simulation-server-all.jar
```

### 2.A **Agent Server (Full Kotlin Example)**
To run the full Kotlin example which includes a working agent built on GPT-4, follow this step.
Alternatively, skip to step 2.B to run the minimal Python example.

#### Running from IntelliJ (recommended)
Open the `agent-servers/agent-server-kotlin` directory as an IntelliJ project.
Use the Gradle tab to reload all gradle projects.
Run the "Game Agent Server" configuration.
> **NOTE:** If you want to integrate your OpenAI key, then duplicate the Game Agent Server run configuration, and add BOTFARM_OPENAI_API_KEY environment variable to your new run configuration. To avoid checking your API key into the repository, leave the "Store as project file" checkbox ***disabled*** for this run configuration.

#### Running from the terminal (slower iteration)
From the `agent-servers/agent-server-kotlin` directory, run:
```console
gradle :shadowJar
java -jar ./build/libs/botfarm-agent-server-kotlin-all.jar
```

### 2.B **Agent Server (Minimal Python Example)**
NOTE: Skip this step if you are running the Full Kotlin Example from 2.A  
This minimal Python agent server example simply defines the Pydantic types for the Agent API and then implements `/api/sync` with a response that instructs the agent's character to say "Hello from Python!"

#### Running from the terminal
From the `agent-servers/agent-server-python` directory, run:
```console
./run.sh
```

### 3. **Client**
From the `client` directory, run:
```console
npm install
npm start
```
This should start a React/Webpack development server on port `3005`.  
This will automatically recompile and reload the client whenever you change any client source code (Typescript) or assets.

## Agent API
Agent servers are expected to implement a JSON POST handler at `/api/sync`  

The sync endpoint will be called frequently (currently once per second) by the simulation server to make the agent aware of the current state of the simulation, and to optionally respond with actions for the agent's character to perform.

### The sync request includes:
- The state of entities near the agent's characters
- Events that the agent's character has observed since the last time the sync endpoint was called
- Results of previous character actions requested by the agent

### The sync response can include:
- A list of actions to perform OR a small JavaScript program to execute character actions using provided TypeScript declarations
- Debugging information regarding prompts, errors, API costs, etc.

So far, the best documentation available for the specific request and response formats is the Kotlin classes that define them. Additionally, you can view the Python/Pydantic definitions in the example minimal Python agent server project.

Probably the weakest and least self-documenting portion of the Agent API is the overly flexible `ActivityStreamEntry` class.  
This class is used to communicate events to agents, as well as to help the browser client populate the `Activity Panel`.  
The `ActivityStreamEntry` has a large number of semi-generic fields, of which different subsets are used to encode different types of in-game events. Until further documentation is provided, you can look at the [example request](docs/example-requests/agent-sync-request-1.json), and otherwise search through the simulation server Kotlin codebase for calls to the `addActivityStreamEntry` function.

### Kotlin Definitions:
- [AgentSyncRequest.kt](shared/kotlin/botfarmshared/game/apidata/AgentSyncRequest.kt)  
- [AgentSyncResponse.kt](shared/kotlin/botfarmshared/game/apidata/AgentSyncResponse.kt)  
- And a few additional classes from [engine/apidata](shared/kotlin/botfarmshared/engine/apidata)
- Vector2 is serialized as JSON object with x and y number fields `{"x": 3, "y": 4}`
- Kotlin value classes (e.g. `SimulationId`, `AgentId`, `EntityId`) are directly serialized as their underlying string values (e.g. `"abc123"`)
- Kotlin enums are serialized as strings, e.g. `AgentStatus.Running` is serialized as `"Running"`

### Python Definitions:
- [request.py](agent-servers/agent-server-python/api_data/request.py)  
- [response.py](agent-servers/agent-server-python/api_data/response.py)  

### Example Request
- [docs/example-requests/agent-sync-request-1.json](docs/example-requests/agent-sync-request-1.json)

## Deployment
See [Deployment](docs/deployment.md)

## Areas to Develop
Along with continuing broad development on the existing functionality, the following are some areas the project could develop in.

- Developing example agents using other technology stacks, especially those that are popular in the AI/ML community (e.g. Python), would be a huge help for the project. Both as a way to make it easier for other people to get started building an agent with other technology stacks, but also as a way to evaluate and iterate on the simulation/agent integration APIs.

- Building additional simulation features that could be useful for exploring emergent social behavior of generative agents.
    - Basic combat mechanics (so we can evaluate which situations agents would choose to engage in violence)
    - Basic hunger/shelter/survival mechanics to drive problem-solving of food productions
    - Clothing items, and integration into entity description provided to agents
    
- Database backup of active game simulations, so they can be resumed if a server is restarted. Useful for local development iteration on simulation logic, handling spot instance interruptions, or long living simulations that live through multiple deployments.

- Add support for signed requests from simulation server to the agent servers, using a secret key known to both servers.

- Add an index server in front of the simulation servers, and ultimately support an autoscaling fleet of simulation servers.

- Deployment streamlining. Currently, the project has been deployed via AWS Elastic Beanstalk. Initial setup for this deployment currently requires manual work on the AWS console across Elastic Beanstalk, EC2, CloudFront, S3, Certificate Manager, etc..

- Windows/Linux development environment testing. Theoretically should just work, but has not been tested.

- Robust player authentication system. Currently this project using a simplistic admin secret system, and all non-admin connections are essentially anonymous and unauthenticated.

- Support for multiple regions within a simulation, which could be used to implement isolated building interiors and caves

- It would be nice if people could experience generative agents in game worlds before they become mainstream in commercial products without having to manually run these systems locally. Conceivably, these systems could just be deployed on a public server, but currently running high quality agents is very expensive. Therefore, such as a service would need to support allowing users to pay for the cost of running generative agents. It would be against OpenAI’s terms of service for users to share their own API keys with the service.

- General game sound effects

- Integrate text-to-speech

- Integrate voice-to-text

## Attribution
- Liberated Pixel Cup (LPC)
    - The characters animations in this project are from the Liberated Pixel Cup (LPC) project, which is licensed under CC BY-SA 3.0 and GPLv3.
https://lpc.opengameart.org/
    - The full list of attributions for these art assets is located at client/public/assets/liberated-pixel-cup-characters/CREDITS.TXT
    - This project also uses JSON data derived from the "sheet_definitions" of the Universal LPC Spritesheet Character Generator project (also GPLv3): https://github.com/sanderfrenken/Universal-LPC-Spritesheet-Character-Generator
- Most other art in this project was derived from images generated by Midjourney/DALL·E/stability.ai

## Relevant Research/Projects

- [Voyager: An Open-Ended Embodied Agent with Large Language Models](https://github.com/MineDojo/Voyager)  
- [Generative Agents: Interactive Simulacra of Human Behavior](https://github.com/joonspk-research/generative_agents#generative-agents-interactive-simulacra-of-human-behavior)
- [AI Town: a virtual town where AI characters live, chat and socialize](https://github.com/a16z-infra/ai-town#stack)
- [AgentSims: An Open-Source Sandbox for Large Language Model Evaluation](https://github.com/py499372727/AgentSims/)

