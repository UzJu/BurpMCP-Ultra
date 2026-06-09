package com.burpmcp.ultra.transport

import burp.api.montoya.logging.Logging
import com.burpmcp.ultra.bridge.BridgeFactory
import com.burpmcp.ultra.events.EventBus
import com.burpmcp.ultra.state.StateManager
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Manages the lifecycle of MCP server instances and their underlying
 * Ktor HTTP servers.
 *
 * Two SSE transports are exposed on separate ports so that multiple
 * MCP clients can connect independently:
 * - **Primary SSE** on [ssePort] (default 9876).
 * - **Secondary SSE** on [httpPort] (default 9877).
 *
 * A third transport (stdio) is available but handled externally by the
 * process launcher, not by this manager.
 *
 * @param bridges All bridge instances for tool/resource registration.
 * @param eventBus Shared event bus for event-related tools/resources.
 * @param stateManager Shared state for stateful tools.
 * @param ssePort TCP port for the primary SSE transport (default 9876).
 * @param httpPort TCP port for the secondary SSE transport (default 9877).
 * @param logging Burp Suite logging API for startup/error messages.
 */
class McpServerManager(
    private val bridges: BridgeFactory.Bridges,
    private val eventBus: EventBus,
    private val stateManager: StateManager,
    private val ssePort: Int = 9876,
    private val httpPort: Int = 9877,
    private val logging: Logging
) {
    private var sseServer: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var httpServer: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Track whether each transport is actually running (port bound and accepting connections). */
    @Volatile var isSseRunning: Boolean = false
        private set
    @Volatile var isHttpRunning: Boolean = false
        private set

    /**
     * Maximum number of retries when a server fails to bind (port conflict, etc.).
     * Each retry waits with exponential backoff: 1s, 2s, 4s.
     */
    private val maxBindRetries = 3

    /**
     * Creates a fresh MCP [Server] instance with all tools and resources
     * registered. Each transport gets its own server instance so they
     * maintain independent session state.
     */
    fun createMcpServer(): Server {
        val server = Server(
            serverInfo = Implementation(
                name = "burpmcp-ultra",
                version = "2.0.2"
            ),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = true),
                    resources = ServerCapabilities.Resources(
                        subscribe = true,
                        listChanged = true
                    )
                )
            )
        )

        // Register all 137 tools and 14 resources on this server instance
        ToolRegistry.registerAll(server, bridges, eventBus, stateManager)
        ResourceRegistry.registerAll(server, bridges, eventBus, stateManager)

        return server
    }

    /**
     * Returns a factory function compatible with the MCP SDK's `mcp()` extension.
     * The SDK expects `(ServerSSESession) -> Server`.
     */
    private fun serverFactory(): (ServerSSESession) -> Server = { _ -> createMcpServer() }

    /**
     * Returns a factory function compatible with the MCP SDK's `mcpStreamableHttp()` extension.
     * The Streamable HTTP transport expects `RoutingContext.() -> Server`.
     */
    private fun routingContextFactory(): RoutingContext.() -> Server = { createMcpServer() }

    /**
     * Synchronously check whether a TCP port is actually listening on localhost.
     * Used to verify that the Ktor server has successfully bound before
     * reporting "started" to the user.
     */
    private fun isPortListening(port: Int, timeoutMs: Int = 500): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Wait for a port to become available, polling every [intervalMs] ms,
     * up to [maxWaitMs] ms total. Returns true if the port came up.
     */
    private suspend fun waitForPort(port: Int, maxWaitMs: Long = 5000, intervalMs: Long = 200): Boolean {
        val deadline = System.currentTimeMillis() + maxWaitMs
        while (System.currentTimeMillis() < deadline) {
            if (isPortListening(port)) return true
            delay(intervalMs)
        }
        return false
    }

    /**
     * Installs CORS on a Ktor [Application] so that browser-based MCP
     * clients (and tools like MCP Inspector) can connect to the SSE
     * endpoints.
     */
    private fun Application.installCors() {
        install(CORS) {
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Delete)
            allowNonSimpleContentTypes = true
            anyHost()
        }
    }

    /**
     * Starts both transport servers asynchronously with health verification.
     *
     * Each server is started with retry logic for port-binding failures, and
     * the port is verified to be actually listening before reporting success.
     * Failures on one transport do not prevent the other from starting.
     *
     * **Primary SSE transport** (port 9876): registers `GET /sse` (SSE stream)
     * and `POST /message` (back-channel) via the Kotlin MCP SDK's `mcp()`
     * scoped to `/sse`.
     *
     * **Secondary transport** (port 9877): registers both SSE (at `/sse`) AND
     * Streamable HTTP endpoints at the root `/` so that clients using either
     * transport protocol can connect. This is the **Hermes compatibility fix**:
     * Hermes defaults to Streamable HTTP and needs `POST /` with
     * `Mcp-Session-Id` header support.
     */
    fun start() {
        // Launch primary SSE transport (port 9876)
        scope.launch {
            var backoff = 1000L
            for (attempt in 1..maxBindRetries) {
                try {
                    val server = embeddedServer(CIO, port = ssePort, host = "127.0.0.1") {
                        installCors()
                        install(SSE)
                        routing {
                            route("sse") {
                                mcp(serverFactory())
                            }
                        }
                    }
                    server.start(wait = false)

                    if (waitForPort(ssePort)) {
                        sseServer = server
                        isSseRunning = true
                        logging.logToOutput("BurpMCP-Ultra: SSE transport confirmed on port $ssePort")
                        logging.logToOutput("BurpMCP-Ultra: Connect MCP clients to http://127.0.0.1:$ssePort/sse")
                        return@launch
                    }

                    // Port didn't come up — stop and retry
                    logging.logToError(
                        "BurpMCP-Ultra: SSE port $ssePort not reachable (attempt $attempt/$maxBindRetries)"
                    )
                    server.stop(gracePeriodMillis = 500, timeoutMillis = 1000)
                } catch (e: Exception) {
                    logging.logToError(
                        "BurpMCP-Ultra: SSE transport attempt $attempt/$maxBindRetries failed: ${e.message}"
                    )
                }
                if (attempt < maxBindRetries) {
                    delay(backoff)
                    backoff = (backoff * 2).coerceAtMost(8000)
                }
            }
            logging.logToError("BurpMCP-Ultra: SSE transport FAILED to start on port $ssePort after $maxBindRetries attempts")
        }

        // Launch secondary transport (port 9877) with Streamable HTTP + SSE
        scope.launch {
            var backoff = 1000L
            for (attempt in 1..maxBindRetries) {
                try {
                    val server = embeddedServer(CIO, port = httpPort, host = "127.0.0.1") {
                        installCors()
                        // ContentNegotiation is REQUIRED for mcpStreamableHttp to serialize
                        // JSON-RPC responses (InitializeResult, JSONRPCError, etc.). Without it,
                        // Ktor's call.respond() fails and returns 406 Not Acceptable.
                        install(ContentNegotiation) {
                            json()
                        }
                        // NOTE: do NOT install(SSE) here — mcpStreamableHttp installs it internally

                        // Streamable HTTP at /mcp (default path for mcpStreamableHttp).
                        // Hermes connects here by default when url ends with /mcp.
                        mcpStreamableHttp(block = routingContextFactory())

                        // SSE fallback at /sse for clients that explicitly use SSE transport
                        routing {
                            route("sse") {
                                mcp(serverFactory())
                            }
                        }
                    }
                    server.start(wait = false)

                    if (waitForPort(httpPort)) {
                        httpServer = server
                        isHttpRunning = true
                        logging.logToOutput("BurpMCP-Ultra: Streamable HTTP confirmed on port $httpPort")
                        logging.logToOutput("BurpMCP-Ultra: Connect Hermes clients to http://127.0.0.1:$httpPort/")
                        logging.logToOutput("BurpMCP-Ultra: SSE fallback: http://127.0.0.1:$httpPort/sse")
                        return@launch
                    }

                    logging.logToError(
                        "BurpMCP-Ultra: HTTP port $httpPort not reachable (attempt $attempt/$maxBindRetries)"
                    )
                    server.stop(gracePeriodMillis = 500, timeoutMillis = 1000)
                } catch (e: Exception) {
                    logging.logToError(
                        "BurpMCP-Ultra: HTTP transport attempt $attempt/$maxBindRetries failed: ${e.message}"
                    )
                }
                if (attempt < maxBindRetries) {
                    delay(backoff)
                    backoff = (backoff * 2).coerceAtMost(8000)
                }
            }
            logging.logToError("BurpMCP-Ultra: HTTP transport FAILED to start on port $httpPort after $maxBindRetries attempts")
        }
    }

    /**
     * Gracefully stops both transport servers and cancels the coroutine scope.
     * Called from the extension unload handler.
     */
    fun stop() {
        isSseRunning = false
        isHttpRunning = false
        sseServer?.stop(gracePeriodMillis = 1000, timeoutMillis = 2000)
        httpServer?.stop(gracePeriodMillis = 1000, timeoutMillis = 2000)
        scope.cancel()
        logging.logToOutput("BurpMCP-Ultra: MCP servers stopped")
    }
}
