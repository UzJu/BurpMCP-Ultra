package com.burpmcp.ultra.tools.repeater

import com.burpmcp.ultra.bridge.RepeaterBridge
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.*

object RepeaterTools {

    fun register(server: Server, bridge: RepeaterBridge) {
        server.addTool(
            name = "repeater_send",
            description = "Send an HTTP request to Burp Suite's Repeater tool. " +
                "Creates a new Repeater tab with the specified request, allowing manual " +
                "replay and modification. Parameters: request (raw HTTP request string), " +
                "host (target hostname), port (target port number), use_tls (boolean, " +
                "whether to use HTTPS; auto-detected from https param or port 443 if omitted), " +
                "https (boolean, alias for use_tls), tab_name (optional, name for the Repeater tab)."
        ) { request ->
            try {
                val args = request.params.arguments ?: emptyMap()
                val rawRequest = args["request"]?.jsonPrimitive?.contentOrNull
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent("""{"error":"Missing required parameter: request"}""")),
                        isError = true
                    )
                val host = args["host"]?.jsonPrimitive?.contentOrNull
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent("""{"error":"Missing required parameter: host"}""")),
                        isError = true
                    )
                val port = args["port"]?.jsonPrimitive?.intOrNull
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent("""{"error":"Missing required parameter: port"}""")),
                        isError = true
                    )
                val useTls = args["use_tls"]?.jsonPrimitive?.booleanOrNull
                    ?: args["https"]?.jsonPrimitive?.booleanOrNull
                    ?: (port == 443)
                val tabName = args["tab_name"]?.jsonPrimitive?.contentOrNull

                val result = bridge.sendToRepeater(rawRequest, host, port, useTls, tabName)
                CallToolResult(content = listOf(TextContent(result.toString())))
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(TextContent("""{"error":"${e.message}"}""")),
                    isError = true
                )
            }
        }
    }
}
