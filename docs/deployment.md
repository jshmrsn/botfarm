## Building for Deployment
### Client
In the `client` directory.
```console
npm install
npm build
```
This will produce a `build` directory inside `client`.

### Simulation Server
In the `simulation-server` directory.
```console
gradle :shadowJar
```
There should now be a JAR file located at `./build/libs/botfarm-simulation-server-all.jar`
```console
export BOTFARM_SIMULATION_SERVER_PORT=<your preferred port>
export BOTFARM_AGENT_SERVER_ENDPOINT=http://youragentserver
java -jar botfarm-simulation-server-all.jar
```

### Agent Server
In the `agent-servers/agent-server-kotlin` directory.
```console
gradle :shadowJar
```
There should now be a JAR file located at `./build/libs/botfarm-agent-server-kotlin-all.jar`  
You can run this JAR on a server.  

```console
export BOTFARM_AGENT_SERVER_PORT=<your preferred port>
export BOTFARM_OPENAI_API_KEY=<your OpenAI API key>
java -jar botfarm-agent-server-kotlin-all.jar
```

## Deployment
Basically, your web server should serve the contents of `client/build` (defaulting to index.html), while proxying all requests to /api/ and /ws to a simulation server.
On AWS, I accomplish this by:
Deploying the simulation server with Elastic Beanstalk (an zip archive of the jar file on the Coretto 11 Java platform).
- Uploading the `client/build` directory to `S3`.
- Creating a CloudFront distribution
    - Note, you may want to use caching disabled policies to simplify deployment, or clear distribution caches after deployment.
    - One origin set to the S3 bucket
        - Note that if you placed the contents of `client/build` in a subdirectory of your S3 bucket you'll need to set the origin's "Origin path" to to that directory.
        - Note that you'll need to follow the instructions for Origin access control.
    - A second origin set the simulation server
    - Default object set to `/index.html`
    - A behavior with the path pattern `/api/*` and all HTTP methods set to allow (for the POST-based API).
        - This is to proxy all API requests to the simulation server origin
    - A behavior with the path pattern `/ws`
        - This is to proxy websocket traffic from the client.
    - The `Default (*)` behavior pointing at the S3 bucket.
