> **A Note on This Document**
>
> This `README.md` file was generated with the assistance of AI, specifically using the `aider` tool with Google's Gemini 2.5 Pro model.
>
> I chose to use an AI for a few reasons: it's an efficient way to produce comprehensive documentation, and it helps ensure the tone is clear and professional, which can sometimes be a challenge.
>
> While the process was guided by me, the content is largely AI-written and may contain inaccuracies or outdated information. If you find something that doesn't work as described or is unclear, please feel free to open an issue to ask for clarification or submit a pull request with a fix. Your contributions to improving the documentation are highly appreciated.

# Meshtastic Forwarder

Expose your Meshtastic device's serial API over HTTP and WebSockets, enabling remote access, automation, and powerful extensions like AI chatbots.

## Overview

This project is composed of two main components:

*   **Server**: A Spring Boot application that connects to a Meshtastic device via a serial port. It exposes the device's functionality through a secure RESTful API and a WebSocket for real-time packet streaming.
*   **Client**: A client application that consumes the server's API to provide value-added features. The primary feature is an AI-powered chatbot using Google's Gemini models, which can respond to private messages and participate in public channels.

### Architecture

The system follows a simple client-server architecture:

```
+-------------------+      (Serial/USB)      +-----------------+      (HTTP/WebSocket)      +-----------------+
| Meshtastic Device | <--------------------> | Forwarder Server| <------------------------> | Forwarder Client|
+-------------------+                        +-----------------+                            +-----------------+
                                                                                                     | (HTTPS)
                                                                                                     |
                                                                                           +-------------------+
                                                                                           | Google Gemini API |
                                                                                           +-------------------+
```

## Features

### Server

*   **Remote Access**: Access your Meshtastic device's information from anywhere over the network.
*   **RESTful API**: A comprehensive set of endpoints to:
    *   Get device channels, configs, and node information.
    *   Get your node's user info.
    *   Generate new packet IDs.
    *   Send mesh packets.
*   **Real-time Packet Stream**: A WebSocket endpoint (`/ws/meshPacket`) provides a live feed of all packets received from the device.
*   **Secure**: Protects API access with token-based authentication.

### Client

*   **Google Gemini Chatbot**: Integrates with the Google Gemini API to bring a powerful AI chatbot to your mesh network.
*   **Flexible Interaction**:
    *   Responds to direct messages sent to the node.
    *   Participates in designated public channels.
*   **Highly Configurable**: Easily enable or disable features and configure API endpoints using environment variables.
*   **Proxy Support**: Includes a pre-configured Caddy reverse proxy to route Gemini API requests through an HTTP proxy if needed.

## Deployment Guide

This project is designed to be deployed using Docker and Docker Compose. The following guide assumes you have a Meshtastic device connected to the machine that will run the `server` component.

### Prerequisites

*   [Docker](https://docs.docker.com/get-docker/) and [Docker Compose](https://docs.docker.com/compose/install/)
*   A Meshtastic device connected via USB.
*   A Google Gemini API Key. You can get one from [Google AI Studio](https://ai.google.dev/).

### Configuration

To deploy, you will need to create a project directory containing `compose.yaml`, `caddy-conf/Caddyfile`, and `secrets.env`.

1.  **Prepare Files**

    First, create a project directory for your deployment. Then, download the [`compose.yaml`](./compose.yaml) and [`caddy-conf/Caddyfile`](./caddy-conf/Caddyfile) from this repository into that directory, preserving the `caddy-conf` subdirectory for the Caddyfile.

    Your directory structure should look like this:
    ```
    .
    ├── caddy-conf
    │   └── Caddyfile
    └── compose.yaml
    ```

2.  **Create `secrets.env` File**

    In your project directory, create a file named `secrets.env` to store your Google API key.

    ```
    # secrets.env
    GOOGLE_API_KEY=your_gemini_api_key_here
    ```

3.  **Customize `compose.yaml`**

    Open `compose.yaml` and review the environment variables for each service, customizing them as needed.

    > **Why do the environment variables have long, strange names?**
    > 
    > The application uses Spring Boot, which automatically maps configuration properties (e.g., `meshtastic.client.features.enable-gemini-on-private-chat`) to environment variables. This conversion involves removing dots/hyphens and making the name uppercase, resulting in long names like `MESHTASTIC_CLIENT_FEATURES_ENABLEGEMINIONPRIVATECHAT`.
    > 
    > **Important**: Due to their specific format, these variable names are prone to typos. It is strongly recommended to copy and paste them to avoid configuration errors.

    *   **`server` service**:
        *   `MESHTASTIC_SERVER_RWTOKENS`: A comma-separated list of tokens for read-write access. Only clients using these tokens can send messages. **You should change the default `dev-token` to a secure, private token.**
        *   `MESHTASTIC_SERVER_ROTOKENS`: An optional comma-separated list of tokens for read-only access (e.g., getting device info). These tokens cannot be used to send messages.
        *   `MESHTASTIC_CLIENT_PORTURI`: Set this to your Meshtastic device's serial port (e.g., `serial:///dev/ttyACM0`). Ensure the `devices` mapping matches.
    *   **`client` service**:
        *   `MESHTASTIC_CLIENT_TOKEN`: This must match one of the `RW` tokens set for the `server`, as the client needs permission to send messages.
        *   Adjust `MESHTASTIC_CLIENT_FEATURES_*` flags to enable or disable chatbot features.
    *   **`gemini-proxy` service (Optional)**:
        *   `HTTP_PROXY`: Set your proxy address here. If you don't need a proxy, you can remove this service and the `GOOGLE_GEMINI_BASE_URL` environment variable from the `client` service in `compose.yaml`. The Gemini SDK will then use its default endpoint.

### Running the Services

Once configured, you can start all services with a single command:

```bash
docker compose up -d
```

The `compose.yaml` is configured to always pull the latest pre-built images from `ghcr.io`. If you wish to build the images from local source code, use:

```bash
docker compose up -d --build
```

To check the logs:
```bash
docker compose logs -f
```

## API Documentation

The `server` provides a simple RESTful API and a WebSocket for programmatic access.

### Authentication

All API requests (except for the WebSocket connection) must include an `Authorization` header with a bearer token.

`Authorization: Bearer your_token_here`

### HTTP Endpoints

*   **GET `/device/channels`**: Get a list of the device's channels. (Requires RO token)
*   **GET `/device/configs`**: Get the device's configuration settings. (Requires RO token)
*   **GET `/device/myNodeInfo`**: Get information about the local node. (Requires RO token)
*   **GET `/device/myUserInfo`**: Get the user info of the local node. (Requires RO token)
*   **GET `/device/generateNewPacketId`**: Generate a unique packet ID for sending a message. (Requires RO token)
*   **GET `/device/nodes`**: Get a list of nodes in the mesh. (Requires RO token)
*   **POST `/send/meshPacket`**: Send a `MeshPacket` protobuf message. The message should be Base64 encoded. (Requires RW token)

_Note: All protobuf objects in responses are Base64 encoded strings._

### WebSocket Endpoint

*   **`ws://<server_address>/ws/meshPacket`** (Requires RO token)

This endpoint provides a one-way broadcast stream of `MeshPacket` messages received by the device. Unlike the HTTP endpoints, messages are sent as raw binary `MeshPacket` protobufs. The initial connection (HTTP upgrade request) must be authenticated with a valid token in the `Authorization` header.

Note that this is a broadcast-only endpoint. The server does not accept incoming messages from clients; any attempt to send a message will result in the connection being closed.

## Client Library (`client-lib`)

The `client-lib` module is a Kotlin library designed to simplify interaction with the server's API. It's the same library used by the `client` application and provides a higher-level abstraction over the raw HTTP and WebSocket endpoints.

Key components include:
*   `MFHttpClient`: A client class offering simple, direct methods for all REST API calls (e.g., `getMyNodeInfo()`, `sendMeshPacket()`).
*   `MeshPacketWebSocketListener`: A listener that integrates with `okhttp` and uses Kotlin Coroutines to expose a `Channel<MeshPacket>`. This provides a clean, asynchronous way to stream real-time packets from the server.

This library is recommended for anyone building custom clients or integrations in a JVM environment.

### Usage Example

Here is a brief example of how to use `client-lib` to connect to the server, fetch node info, and listen for incoming packets. This approach is based on the Spring configuration used in the `client` module.

```kotlin
import info.skyblond.meshtastic.forwarder.lib.MeshtasticForwarderClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient

fun main(): Unit = runBlocking(Dispatchers.IO) {
    val serverUrl = "your-server-url" // e.g., 127.0.0.1:8080
    val token = "your-token"
    val useTls = false

    // 1. Create the main MeshtasticForwarderClient
    // This single client manages both HTTP and WebSocket connections.
    val client = MeshtasticForwarderClient(
        OkHttpClient(),
        serverUrl,
        useTls,
        token
    )

    // 2. Use the client in a 'use' block for automatic resource management
    client.use { mfClient ->
        // Make an API call via the httpService
        val myNodeInfo = mfClient.httpService.getMyNodeInfo()
        println("Successfully fetched node info: ${myNodeInfo.myNodeNum}")

        // Launch a coroutine to consume packets from the wsClient's channel
        val wsJob = launch {
            mfClient.wsClient.meshPacketChannel.consumeAsFlow()
                .onCompletion { println("WebSocket connection closed.") }
                .onEach { packet ->
                    println("Received packet: ${packet.id} from ${packet.from}")
                }
                .collect() // Start collecting
        }

        // Do other work... (e.g. wait for 10 seconds in this demo)
        delay(10_000)

        // No need to manually close wsJob or the WebSocket.
        // The client.close() call at the end of the 'use' block
        // handles all necessary cleanup.
    }
}
```

For a more comprehensive, real-world example of how `client-lib` is integrated and used, see the source code in the [`client`](./client) module. The components in that module demonstrate how to build features on top of the library.

## Contributing and Support

**A Note on Expectations**

Please keep in mind that this is a personal side project, developed and maintained in my free time for my own use and enjoyment. Like all free and open-source software, it is provided as-is with absolutely no warranty. I am not paid to maintain this project, and therefore I am under no obligation to fix bugs or add features at your request.

If you have a pressing need for a specific feature, sponsored development can be discussed. The cost would need to cover the development time and the opportunity cost of my professional work.

With that said, your contributions and feedback are still welcome! Please follow these guidelines when opening an issue or pull request.

#### Bug Reports

If you encounter a bug, please open an issue on GitHub. A good bug report should include:
*   A clear and descriptive title.
*   A detailed description of the problem.
*   **Crucially, step-by-step instructions on how to reproduce the bug.** Without clear reproduction steps, it is very difficult to identify and fix the issue.

#### General Issues & Questions

For general questions or discussions, feel free to open an issue. Please provide as much context as possible to help others understand your query.

#### Feature Requests

This is a personal project created primarily for my own use. As such, I will likely not implement feature requests that do not align with my own needs.

However, I strongly encourage community contributions. If you have a feature you'd like to see, you are always welcome to **open a Pull Request**. Please note that for significant new features, especially those that serve a niche use case I do not use myself, the original contributor will be expected to provide ongoing maintenance. This ensures that every part of the project remains healthy and up-to-date long-term.

## Development Guide

### Project Modules

*   `common`: Shared data classes and utility functions used by other modules.
*   `server`: The Spring Boot application that connects to the Meshtastic device and exposes the web API.
*   `client-lib`: A Kotlin library for interacting with the `server`'s API.
*   `client`: The client application that uses `client-lib` to implement features like the Gemini chatbot.

### Build from Source

**Prerequisites:**
*   JDK 21 or newer
*   Gradle

**Build command:**
```bash
./gradlew build
```

This will compile the source, run tests, and build the JAR files for each module.

## License

This project is licensed under the GNU Affero General Public License v3.0. See the `LICENSE` file for details.
