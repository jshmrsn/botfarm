<div align="center">

<img src="https://github.com/jshmrsn/botfarm/blob/main/client/public/logo192.png?raw=true" width="120" />
<h1>Botfarm</h1>
<h3>Multiplayer Game Platform for Embodying Generative Agents</h3>
</div>


## Goals
- Provide a game simulation platform that can be used to embody generative agents, and to conduct evaluation of those agents in configured scenarios.

- Decouple game simulations and agent technology stacks via an HTTP API, so that generative agents can be built with any preferred technology stack.

- Support real time multiplayer, so players can interact with generative agents and other players.

- Prioritize maintainability, code quality, and game development best practices of the game simulation code base, so that it can be extended or remixed for various research use cases.

![screenshot](https://github.com/jshmrsn/botfarm/blob/main/docs/screenshots/screenshot-1.png)


## Motivations
- Research whether or not virtually embodying generative agents allows them to be more convincingly intelligent compared to disembodied agents (e.g. chat bots and virtual assistants).

- Research emergent social behavior of generative agents, both among other agents and human players.

- Research cost feasibility of using generative agents in video games.

- Explore UX and game design challenges of games that utilize generative agents.


## Current Features
- A playable browser-based 2D game with a basic implementation of harvesting, crafting, and farming mechanics.
- Characters in the game can be controlled either by human players or generative agents.
- Supports server-authorative real time multiplayer.
- Replay system with timeline scrubbing support.
- Playable on mobile browsers.

## Project Architecture
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

## !WARNING REGARDING API COSTS!
If you provide an OpenAI API key and play with agents, the API costs can add up very quickly.  
This is especially true if you play with agents that use GPT-4 (30x more expensive than GPT-3).  
The cost multiplies for each agent that you spawn into a scene.  
That said, you can initially play the game without any agents to avoid incurring API costs.

### 1. **Simulation Server**
#### Running from IntelliJ (recommended)
Open the `simulation-server` directory as an IntelliJ project.
Use the Gradle tab to reload all gradle projects.
Run the "Simulation Server" configuration.

#### Running from the terminal (slower iteration)
In the `simulation-server` directory.
```console
gradle :shadowJar
java -jar ./build/libs/botfarm-simulation-server-all.jar
```

### 2. **Agent Server**
#### Running from IntelliJ (recommended)
Open the `agent-servers/agent-server-kotlin` directory as an IntelliJ project.
Use the Gradle tab to reload all gradle projects.
Run the "Agent Server" configuration.

#### Running from the terminal (slower iteration)
In the `agent-servers/agent-server-kotlin` directory.
```console
gradle :shadowJar
java -jar ./build/libs/botfarm-agent-server-kotlin-all.jar
```

### 3. **Client**
In the `client` directory.
```console
npm install
npm start
```
This should start a React/Webpack development server on port `3005`.  
This will automatically recompile and reload the client whenever you change any client source code (Typescript) or assets.
See [How to Play](docs/how-to-play.md)

## Deployment
See [Deployment](docs/deployment.md)


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


## Areas to Develop
Along with continuing broad development on the existing functionality, the following are some discrete areas the project could grow in.

- Developing example agents using other technology stacks, especially those that are popular in the AI/ML community, would be a huge help for the project. Both as a way to make it easier for other people to get started building an agent with other technology stacks, but also as a way to evaluate and iterate on the simulation/agent integration APIs.

- Building additional simulation features that could be useful for exploring emergent social behavior of generative agents.

- More decoupling of the specific game simulation from the simulation framework, so entirely different games could be supported (note: there is already a significant amount of decoupling, just not 100% there yet).

- Database backup of active game simulations, so they can be resumed if a server is restarted. Useful for local development iteration on simulation logic, handling spot instance interruptions, or long living simulations that live through multiple deployments.

- Add support for signed requests from simulation server to the agent servers, using a secret key known to both servers.

- Decouple the simulation server from the main application servers, and ultimately support an autoscaling fleet of simulation servers.

- Deployment streamlining. Currently, the project has been deployed via AWS Elastic Beanstalk. Initial setup for this deployment currently requires manual work on the AWS console across Elastic Beanstalk, EC2, CloudFront, S3, Certificate Manager, etc..

- Windows/Linux development environment testing. Theoretically should just work, but has not been tested.

- Player authentication system. Currently, if someone is able to connect to a simulation server, they have unlimited ability to create and play simulations. In other words, it would be bad to deploy this system to the internet with access to a funded OpenAI API key.

- Not sure what the best path forward is here, but it would be nice if non-technical people could experience generative agents in game worlds before they become mainstream in commercial products. Currently running high quality agents is extremely expensive. So if a generative agent simulation was deployed as a public product, it would need to support allowing users to pay for the cost of running generative agents. Since the simulations are ran on the server, and not in the user’s browser, it would be against OpenAI’s terms of service for users to share their API keys with the server.

- Pause agents once a configurable total simulation cost is reached.

- Basic combat mechanics (so we can evaluate which situations agents would choose to engage in violence)

- Hunger mechanics to drive problem-solving of food productions

- Clothing items, and integration into entity description provided to agents

- Dynamic pathfinding grid (update path finding grid when placing blocking entities like houses/trees)

- General game sound effects

- Integrate text-to-speech

- Integrate voice-to-text
