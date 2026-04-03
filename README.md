# BurpMCP-Ultra

The most comprehensive MCP (Model Context Protocol) server for Burp Suite Professional — **137 tools**, **14 MCP resources**, **12 event types**, real-time web dashboard, and full Montoya API coverage.

Built as a native Kotlin Burp extension with embedded MCP server. Drop a single JAR into Burp, connect your AI agent, and control every aspect of Burp Suite programmatically.

## Quick Start

### 1. Build the Extension

```bash
git clone https://github.com/YOUR_USERNAME/BurpMCP-Ultra.git
cd BurpMCP-Ultra
./gradlew shadowJar
```

The fat JAR is at `build/libs/burpmcp-ultra-2.0.1.jar`.

### 2. Load into Burp Suite

1. Open Burp Suite Professional
2. Go to **Extensions** > **Installed** > **Add**
3. Select `build/libs/burpmcp-ultra-2.0.1.jar`
4. Verify the **BurpMCP-Ultra** tab appears with "Running" status

### 3. Connect Your AI Agent

#### Option A: Claude Code (Direct SSE)

Add to your Claude Code MCP config (`~/.claude.json` or project `.mcp.json`):

```json
{
  "mcpServers": {
    "burp": {
      "type": "sse",
      "url": "http://127.0.0.1:9876/sse"
    }
  }
}
```

#### Option B: Claude Code via Caddy (Recommended for Stability)

Install and configure Caddy as a reverse proxy:

```bash
sudo apt install caddy
```

Create `/etc/caddy/Caddyfile`:

```
:9900 {
    reverse_proxy 127.0.0.1:9876 {
        transport http {
            read_timeout 0
            write_timeout 0
            response_header_timeout 0
        }
        flush_interval -1
        header_up Connection {>Connection}
        header_up Upgrade {>Upgrade}
    }
}
```

Start Caddy:

```bash
sudo systemctl restart caddy
```

Then configure Claude Code to use the Caddy proxy:

```json
{
  "mcpServers": {
    "burp": {
      "type": "sse",
      "url": "http://127.0.0.1:9900/sse"
    }
  }
}
```

Caddy prevents SSE timeout disconnections and provides reliable buffering.

#### Option C: Claude Desktop (stdio via proxy)

Claude Desktop requires stdio transport. Use the MCP proxy:

```json
{
  "mcpServers": {
    "burp": {
      "command": "npx",
      "args": ["-y", "supergateway", "--sse", "http://127.0.0.1:9876/sse"]
    }
  }
}
```

### 4. Open the Dashboard

Navigate to **http://127.0.0.1:9878** in your browser for the real-time web dashboard with:
- Live activity stream with noise filtering
- Attack vector detection (AUTH, API, PARAMS, UPLOAD, ADMIN, DATA)
- Request detail panel with send-to-tool actions
- Stats, rules, and connection info

---

## Tool Categories (137 Tools)

| Category | Count | Key Tools |
|----------|:-----:|-----------|
| **Proxy** | 13 | `proxy_history`, `proxy_history_search`, `proxy_intercept_enable/disable`, `proxy_set_request_rule`, `proxy_auto_auth` |
| **HTTP** | 13 | `http_send_request`, `http_send_requests_parallel`, `http_fuzz`, `http_send_raw_bytes`, `http_race`, `http_send_request_chain` |
| **Utilities** | 13 | `util_base64_encode/decode`, `util_url_encode/decode`, `util_hash`, `util_jwt_decode`, `util_decode_smart`, `util_shell_execute` |
| **Scanner** | 12 | `scanner_start_crawl`, `scanner_start_audit`, `scanner_get_all_issues`, `scanner_generate_report`, `scanner_import_bcheck` |
| **BurpSuite** | 9 | `burp_version`, `burp_export/import_project/user_config`, `burp_task_engine_state/set`, `burp_shutdown` |
| **Config** | 7 | `config_proxy_listeners_list/add/remove`, `config_match_replace_add/list/remove`, `config_upstream_proxy_set` |
| **WebSocket** | 7 | `websocket_create`, `websocket_send_text/binary`, `websocket_close`, `websocket_list`, `websocket_get_messages` |
| **Analysis** | 7 | `analyze_request`, `analyze_response`, `analyze_find_reflected`, `analyze_extract_params`, `analyze_diff` |
| **Collaborator** | 6 | `collaborator_create_client`, `collaborator_generate_payload`, `collaborator_poll`, `collaborator_server_info` |
| **Persistence** | 6 | `persistence_store/get/delete/list`, `preference_store/get` |
| **BCheck Mode** | 5 | `bcheck_create`, `bcheck_import`, `bcheck_templates`, `bcheck_list`, `bcheck_remove` |
| **Script Mode** | 5 | `scancheck_create_passive`, `scancheck_create_active`, `scancheck_templates`, `scancheck_list`, `scancheck_remove` |
| **Events** | 5 | `events_get`, `events_get_by_type`, `events_subscribe`, `events_unsubscribe`, `events_clear` |
| **Scope** | 4 | `scope_check`, `scope_include`, `scope_exclude`, `scope_get_config` |
| **Sitemap** | 4 | `sitemap_query`, `sitemap_get_issues`, `sitemap_add_request`, `sitemap_add_issue` |
| **Session** | 3 | `session_create_token_rule`, `session_list_rules`, `session_remove_rule` |
| **Intruder** | 3 | `intruder_send`, `intruder_send_with_positions`, `intruder_register_payload_processor` |
| **Other** | 15 | `auth_diff`, `api_import_openapi`, `passive_intel`, `repeater_send`, `organizer_send/get_items`, `decoder_send`, `comparer_send`, `log_message/event`, `ai_status/prompt`, `bambda_import`, `project_info`, `extension_info` |

---

## Highlight Features

### Race Condition Testing
```
http_race(request: "POST /api/transfer HTTP/1.1\r\nHost: bank.com\r\n...", 
          host: "bank.com", port: 443, count: 20)
```
Sends 20 identical requests simultaneously. Analyzes response variations to detect TOCTOU, double-spend, and limit bypass.

### Auth Level Diffing
```
auth_diff(request: "GET /api/users/1 HTTP/1.1\r\nHost: api.com\r\n...",
          host: "api.com", port: 443,
          auth_levels: [
            {"name": "admin", "header_name": "Authorization", "header_value": "Bearer admin-token"},
            {"name": "user", "header_name": "Authorization", "header_value": "Bearer user-token"},
            {"name": "none"}
          ])
```
Detects IDOR, privilege escalation, and broken access control by comparing responses across auth levels.

### Inline Fuzzer (3 modes)
```
# FUZZ keyword mode
http_fuzz(request: "GET /api?id=FUZZ HTTP/1.1\r\nHost: api.com\r\n\r\n",
          host: "api.com", port: 443, payloads: ["1", "2", "admin", "../../etc/passwd"])

# Marker mode (like Burp Intruder)
http_fuzz(request: "GET /api?id=§1§&role=§user§ HTTP/1.1\r\n...",
          host: "api.com", port: 443, payloads: ["admin"])
```

### Custom Scan Checks (BCheck + Script)
```
# BCheck mode — deploy a vulnerability check in seconds
bcheck_create(name: "AWS Key Leak", type: "passive_response", 
              match_pattern: "AKIA[0-9A-Z]{16}", severity: "high", confidence: "firm")

# Script mode — multi-step active check
scancheck_create_active(name: "SSTI Detection",
    steps: [
        {"payload": "{{7*7}}", "response_conditions": [{"location": "response_body", "pattern": "49", "condition_type": "contains"}]},
        {"payload": "{{7*6}}", "response_conditions": [{"location": "response_body", "pattern": "42", "condition_type": "contains"}]}
    ], severity: "high", confidence: "firm")
```

### API Schema Import
```
api_import_openapi(spec_json: "<swagger JSON>", auth_header: "Authorization", 
                   auth_value: "Bearer token", send_requests: true, add_to_sitemap: true)
```
Parses OpenAPI/Swagger specs, generates requests for every endpoint, populates Burp's sitemap.

### Passive Intelligence
```
passive_intel(max_items: 2000, in_scope_only: true)
```
Scans all proxy history for AWS keys, JWTs, emails, S3 buckets, internal IPs, stack traces, and 30+ other patterns.

---

## Architecture

```
┌─────────────────────────────────────────────────┐
│                BURP SUITE PRO                    │
│  ┌───────────────────────────────────────────┐  │
│  │        BurpMCP-Ultra Extension             │  │
│  │                                            │  │
│  │  Montoya API ──> Bridge Layer (20 bridges) │  │
│  │       │                │                   │  │
│  │  Event Bus    Tool Registry (137 tools)    │  │
│  │       │                │                   │  │
│  │       └────── MCP Server Core ─────────┐   │  │
│  │               (Kotlin SDK 0.8.3)       │   │  │
│  │                    │                   │   │  │
│  │         ┌──────────┼──────────┐        │   │  │
│  │     SSE :9876  SSE :9877  Dashboard    │   │  │
│  │                            :9878       │   │  │
│  └───────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
```

## Tech Stack

- **Kotlin** 2.1.20 / JVM 17
- **Montoya API** 2026.2
- **MCP Kotlin SDK** 0.8.3
- **Ktor CIO** 3.2.3
- **kotlinx.serialization** 1.8.1
- **Shadow JAR** 8.1.1

## Requirements

- Burp Suite Professional 2025.x or later
- Java 17+
- Gradle 8.x (included via wrapper)

## License

MIT

## Comparison

| Feature | BurpMCP-Ultra | burp-ai-agent (53 tools) | PortSwigger Official (12 tools) |
|---------|:---:|:---:|:---:|
| Total MCP Tools | **137** | 53 | 12 |
| Custom Scan Checks | **BCheck + Script** | No | No |
| WebSocket Support | **Full lifecycle** | No | No |
| Inline Fuzzer | **3 modes** | No | No |
| Race Condition Testing | **Yes** | No | No |
| Auth Level Diffing | **Yes** | No | No |
| API Schema Import | **Yes** | No | No |
| Passive Intel Extraction | **Yes** | No | No |
| Real-time Dashboard | **Web + Swing** | No | No |
| Event Streaming | **12 event types** | No | No |
| Response Variation Analysis | **Yes** | No | No |
| Request Chain Macros | **Yes** | No | No |
