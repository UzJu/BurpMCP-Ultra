<p align="center">
  <h1 align="center">BurpMCP-Ultra</h1>
  <p align="center">
    <strong>The most powerful MCP server for Burp Suite Professional</strong>
  </p>
  <p align="center">
    137 Tools &bull; 14 Resources &bull; 12 Event Types &bull; Real-time Dashboard &bull; Full Montoya API Coverage
  </p>
  <p align="center">
    <a href="#fork--fixes">Fork & Fixes</a> &bull;
    <a href="#quick-start">Quick Start</a> &bull;
    <a href="#all-137-tools">All Tools</a> &bull;
    <a href="#highlight-features">Features</a> &bull;
    <a href="#web-dashboard">Dashboard</a> &bull;
    <a href="#setup-guides">Setup Guides</a>
  </p>
</p>

---

> **BurpMCP-Ultra** is a native Kotlin Burp Suite extension with an embedded MCP (Model Context Protocol) server. Drop a single JAR into Burp, connect Claude Code or any MCP client, and control every aspect of Burp Suite programmatically through AI agents.

---

## Fork & Fixes

**This is a fork of [Cy-S3c/BurpMCP-Ultra](https://github.com/Cy-S3c/BurpMCP-Ultra) (v2.0.1) with critical bug fixes.**

The original project had several issues that prevented it from working correctly:

### Critical Fixes Applied

| # | Issue | Root Cause | Fix | Status |
|---|-------|------------|-----|--------|
| A | Port appeared "Running" but not actually listening | `start(wait=false)` swallowed bind failures | Added `waitForPort()` TCP health check, UI tri-state indicator | ✅ |
| B | Hermes clients got `400 Bad Request` | Original only supported SSE; Hermes requires Streamable HTTP | Upgraded MCP SDK 0.8.3→0.9.0, added `mcpStreamableHttp()` on port 9877 | ✅ |
| C | SSE plugin not installed on port 9876 | Missing `install(SSE)` in SSE transport configuration | Added `install(SSE)` on port 9876 | ✅ |
| D | Duplicate SSE plugin on port 9877 | `mcpStreamableHttp` internally installs SSE | Removed explicit `install(SSE)` from port 9877 | ✅ |
| **E** | **Hermes `406 Not Acceptable`** | `ContentNegotiation` plugin not installed; Ktor couldn't serialize JSON-RPC responses | Added `install(ContentNegotiation) { json() }` on port 9877 | ✅ (verified by Hermes end-to-end testing) |
| F | `proxy_history_search` NPE | Montoya API returns lazy proxy lists; internal objects can be reclaimed | Force materialization with `.toList()` inside try-catch | ✅ |
| G | `sitemap_query` ignored `limit` parameter | Parameter named `max_results`, user passed `limit` | Added `limit` alias fallback | ✅ |
| H | `bcheck_create` ignored `content`/`script` | Always auto-generated script, no raw script input | Added optional `script` parameter with `content` alias | ✅ |
| I | `passive_intel` no pagination, response too large | Only `max_items` (takeLast), no offset/limit | Added `offset` + `limit`, responses include `has_more` + `total_findings` | ✅ |
| J | `repeater_send` / `intruder_send` no TLS auto-detect | `use_tls` hardcoded to `false` | TLS detection: `use_tls ?: https ?: (port == 443)` | ✅ |
| K | `util_compress` required Base64 input | Called `Base64.getDecoder().decode()` on plain text | `input_type` default changed to `"auto"` — tries base64 first, falls back to UTF-8 | ✅ |
| L | `analyze_response` empty headers/cookies | Montoya parser failed on some raw responses | Added `fallbackParseResponseHeaders()` | ✅ |
| M | Parameter naming inconsistencies across 7 tools | Different tools used different parameter names for same concepts | Added 7 parameter aliases | ✅ |

### Port Layout

| Port | Protocol | Endpoint | Clients |
|------|----------|----------|---------|
| 9876 | SSE | `/sse` | Claude Code, MCP Inspector, supergateway |
| **9877** | **Streamable HTTP** | **`/mcp`** | **Hermes** (default protocol), modern MCP clients |
| 9877 | SSE (fallback) | `/sse` | Other SSE clients |
| 9878 | HTTP | `/` | Web Dashboard |

> **Important for Hermes users:** The Streamable HTTP endpoint is at `/mcp` (not `/`). Your Hermes config should use `url: http://127.0.0.1:9877/mcp`.

### Full Fix Documentation

See [FIXES.md](FIXES.md) for the complete investigation and fix history.

---

## Why BurpMCP-Ultra?

| | BurpMCP-Ultra | burp-ai-agent | PortSwigger Official |
|---|:---:|:---:|:---:|
| **MCP Tools** | **137** | 53 | 12 |
| **Custom Scan Checks** | BCheck + Script | - | - |
| **WebSocket Testing** | Full lifecycle | - | - |
| **Inline Fuzzer** | 3 modes (FUZZ/Marker/Offset) | - | - |
| **Race Condition Testing** | Single-packet attack | - | - |
| **Auth Level Diffing** | IDOR/privesc detection | - | - |
| **API Schema Import** | OpenAPI/Swagger | - | - |
| **Passive Intel Extraction** | 30+ patterns | - | - |
| **Real-time Dashboard** | Web + Swing | - | - |
| **Event Streaming** | 12 event types | - | - |
| **Response Variation Analysis** | Blind injection detect | - | - |
| **Request Chain Macros** | Multi-step with token extraction | - | - |
| **Collaborator OOB** | Full create/poll/correlate | Partial | Partial |

---

## Quick Start

### 1. Build

```bash
git clone https://github.com/Cy-S3c/BurpMCP-Ultra.git
cd BurpMCP-Ultra
./gradlew shadowJar
```

Output: `build/libs/burpmcp-ultra-2.0.1.jar` (13 MB)

### 2. Load into Burp

1. Burp Suite Pro > **Extensions** > **Add**
2. Select the JAR
3. After ~2 seconds, the **BurpMCP-Ultra** tab status indicator turns green, confirming both ports are listening

### 3. Connect MCP Clients

Two ports are exposed, each optimized for different client protocols:

| Port | Protocol | Endpoint | Best For |
|------|----------|----------|----------|
| **9876** | SSE | `http://127.0.0.1:9876/sse` | Claude Code, MCP Inspector, supergateway |
| **9877** | Streamable HTTP + SSE | `http://127.0.0.1:9877/mcp` | **Hermes**（默认协议）、其他 Streamable HTTP 客户端 |
| **9877** (备选) | SSE | `http://127.0.0.1:9877/sse` | 不希望使用 Streamable HTTP 的 SSE 客户端 |

**Claude Code** — SSE transport on port 9876:

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

**Hermes** — Streamable HTTP，端口 9877（添加至 `~/.hermes/config.yaml`）：

```yaml
mcp_servers:
  burpmcp:
    enabled: true
    url: http://127.0.0.1:9877/mcp
```

> **重要**：URL 必须以 `/mcp` 结尾，这是 `mcpStreamableHttp` 的默认路径。

也可使用 `hermes mcp add` 命令：

```bash
hermes mcp add burpmcp --url http://127.0.0.1:9877/mcp
# 提示认证时选择 N（不需要认证）

# 如果之前通过 hermes mcp add 注册过（以 OAuth 模式），先清理旧配置：
hermes mcp rm Burpmcp
```

**Other SSE clients** — SSE fallback on port 9877:

```json
{
  "mcpServers": {
    "burp": {
      "type": "sse",
      "url": "http://127.0.0.1:9877/sse"
    }
  }
}
```

### 4. Open Dashboard

Browse to **http://127.0.0.1:9878** for the real-time web dashboard.

---

## Setup Guides

<details>
<summary><strong>Claude Code（直接 SSE）</strong></summary>

最简配置。添加至 MCP 配置文件：

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

配置文件位置：
- 全局：`~/.claude.json`
- 项目级：项目根目录下的 `.mcp.json`

</details>

<details>
<summary><strong>Hermes（Streamable HTTP）</strong></summary>

Hermes 默认使用 Streamable HTTP 协议，连接 BurpMCP-Ultra 的端口 9877。

### 方式一：手动编辑配置文件

在 `~/.hermes/config.yaml` 中添加：

```yaml
mcp_servers:
  burpmcp:
    enabled: true
    url: http://127.0.0.1:9877/mcp
```

### 方式二：使用 hermes mcp add 命令

```bash
hermes mcp add burpmcp --url http://127.0.0.1:9877/mcp
# 提示 "Does this server require authentication?" → 输入 N
# 提示 "API key / Bearer token:" → 留空回车
# 看到 "Failed to connect: 406 Not Acceptable" 可以忽略，重启 Hermes 即可
```

### 重要提示

- **URL 路径必须是 `/mcp`**，不是 `/`。这是 Kotlin MCP SDK `mcpStreamableHttp` 的默认路由路径。
- 端口 9877 同时支持 Streamable HTTP（`/mcp`）和 SSE（`/sse`），但 Hermes 应使用 Streamable HTTP 端点 `/mcp`。
- 如果之前用旧版本 BurpMCP 或 `hermes mcp add` 以 OAuth 方式注册过，先执行 `hermes mcp rm Burpmcp` 清理。

### 故障排除

| 错误 | 原因 | 解决 |
|------|------|------|
| `406 Not Acceptable` | BurpMCP 版本 < 2.0.2，缺少 ContentNegotiation 插件 | 更新到最新版 BurpMCP-Ultra |
| `404 Not Found` | URL 路径错误（使用了 `/` 而非 `/mcp`） | 将 URL 改为 `http://127.0.0.1:9877/mcp` |
| `Connection refused` | Burp Suite 未启动或扩展未加载 | 检查 Burp 中 BurpMCP-Ultra 扩展状态 |
| `Session terminated` | 连接过程异常中断 | 重启 Hermes，检查 Burp 日志 |

</details>

<details>
<summary><strong>Claude Desktop（stdio 代理）</strong></summary>

Claude Desktop 仅支持 stdio 传输。使用 supergateway 桥接：

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

</details>

<details>
<summary><strong>其他 SSE 客户端（端口 9877）</strong></summary>

使用端口 9877 的 SSE 备选端点：

```json
{
  "mcpServers": {
    "burp": {
      "type": "sse",
      "url": "http://127.0.0.1:9877/sse"
    }
  }
}
```

</details>

<details>
<summary><strong>Caddy 反向代理（提高稳定性）</strong></summary>

Caddy 可防止 SSE 超时断连，提供可靠缓冲。

安装 Caddy：
```bash
sudo apt install caddy
```

创建 `/etc/caddy/Caddyfile`：
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

```bash
sudo systemctl restart caddy
```

MCP 配置使用端口 9900：
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

预置 Caddyfile：`configs/Caddyfile`

</details>

<details>
<summary><strong>自动化安装脚本</strong></summary>

```bash
chmod +x configs/setup.sh
./configs/setup.sh
```

脚本执行：构建 JAR、可选配置 Caddy、显示需添加的 MCP 配置。

</details>

---

## All 137 Tools

### Proxy (13 tools)
| Tool | Description |
|------|-------------|
| `proxy_history` | Get HTTP proxy history with filtering (host, method, status, MIME, scope) |
| `proxy_history_search` | Regex search across proxy history (URL, headers, body) |
| `proxy_websocket_history` | Get WebSocket proxy history |
| `proxy_websocket_history_search` | Regex search WebSocket history |
| `proxy_intercept_enable` | Enable proxy interception |
| `proxy_intercept_disable` | Disable proxy interception |
| `proxy_intercept_status` | Get current intercept state |
| `proxy_annotate` | Add highlight color and comment to history item |
| `proxy_set_request_rule` | Auto-modify/drop/tag proxy requests matching patterns |
| `proxy_set_response_rule` | Auto-modify/drop/tag proxy responses matching patterns |
| `proxy_list_rules` | List all active proxy rules |
| `proxy_remove_rule` | Remove a proxy rule |
| `proxy_auto_auth` | One-command auth header injection for all matching requests |

### HTTP (13 tools)
| Tool | Description |
|------|-------------|
| `http_send_request` | Send HTTP request (structured or raw, HTTP/1.1 or HTTP/2) |
| `http_send_requests_parallel` | Send multiple requests in parallel (race conditions, batch ops) |
| `http_send_request_chain` | Multi-step request sequence with token extraction between steps |
| `http_fuzz` | Inline fuzzer with FUZZ keyword, section marker, and offset modes |
| `http_send_raw_bytes` | Byte-level HTTP request for smuggling and CRLF injection |
| `http_race` | Race condition testing — send N requests simultaneously |
| `http_cookie_jar_get` | Get cookies from Burp's cookie jar |
| `http_cookie_jar_set` | Set a cookie in the cookie jar |
| `http_analyze_keywords` | Analyze response for keyword occurrences |
| `http_analyze_variations` | Detect response variations for blind injection |
| `http_set_traffic_rule` | Register global request/response modification rule |
| `http_list_traffic_rules` | List active traffic rules |
| `http_remove_traffic_rule` | Remove a traffic rule |

### Scanner (12 tools)
| Tool | Description |
|------|-------------|
| `scanner_start_crawl` | Start a web crawl from seed URLs |
| `scanner_start_audit` | Start active/passive scan with optional auth config |
| `scanner_task_status` | Get scan task progress (requests, errors, issues) |
| `scanner_task_list` | List all scan/crawl tasks |
| `scanner_task_delete` | Cancel and remove a scan task |
| `scanner_task_add_request` | Add request to running audit |
| `scanner_task_issues` | Get issues from specific task |
| `scanner_get_all_issues` | Get all scanner issues with severity/confidence filter |
| `scanner_generate_report` | Generate HTML/XML scan report |
| `scanner_create_issue` | Create custom audit issue |
| `scanner_import_bcheck` | Import BCheck script for custom scanning |
| `scanner_register_check` | Register custom active/passive scan check |

### Utilities (13 tools)
| Tool | Description |
|------|-------------|
| `util_url_encode` / `util_url_decode` | URL encoding/decoding |
| `util_base64_encode` / `util_base64_decode` | Base64 encoding/decoding |
| `util_html_encode` | HTML entity encoding |
| `util_hash` | Cryptographic hashing (MD5, SHA1, SHA256, SHA384, SHA512) |
| `util_compress` / `util_decompress` | Gzip/deflate/brotli compression |
| `util_random_string` | Generate random strings |
| `util_random_bytes` | Generate random bytes |
| `util_jwt_decode` | Decode JWT tokens (header, payload, expiration check) |
| `util_decode_smart` | Auto-detect and decode multi-layer encoding |
| `util_shell_execute` | Execute shell commands from Burp's context |

### BCheck Mode (5 tools)
| Tool | Description |
|------|-------------|
| `bcheck_create` | Generate and deploy BCheck from structured parameters |
| `bcheck_import` | Import raw BCheck DSL script |
| `bcheck_templates` | Get all BCheck templates with DSL reference |
| `bcheck_list` | List deployed BChecks |
| `bcheck_remove` | Remove a deployed BCheck |

### Script Mode (5 tools)
| Tool | Description |
|------|-------------|
| `scancheck_create_passive` | Create passive check with multi-condition matching |
| `scancheck_create_active` | Create multi-step active check with payload chains |
| `scancheck_templates` | Get script mode templates and condition reference |
| `scancheck_list` | List deployed script checks |
| `scancheck_remove` | Deregister a script check |

### Collaborator (6 tools)
| Tool | Description |
|------|-------------|
| `collaborator_create_client` | Create Collaborator client for OOB testing |
| `collaborator_restore_client` | Restore client from secret key |
| `collaborator_generate_payload` | Generate Collaborator payload |
| `collaborator_poll` | Poll for DNS/HTTP/SMTP interactions |
| `collaborator_server_info` | Get Collaborator server address |
| `collaborator_get_secret` | Get client secret key for session persistence |

### Analysis (7 tools) + Advanced (3 tools)
| Tool | Description |
|------|-------------|
| `analyze_request` | Parse HTTP request into structured components |
| `analyze_response` | Parse HTTP response into structured components |
| `analyze_find_reflected` | Find parameter values reflected in response (XSS detection) |
| `analyze_extract_params` | Extract all parameters (URL, body, cookie, header, JSON) |
| `analyze_insertion_points` | Get scanner-style insertion points |
| `analyze_diff` | Compare two requests or responses |
| `analyze_response_body_search` | Search all proxy response bodies for pattern |
| `auth_diff` | Compare responses across auth levels (IDOR/privesc detection) |
| `api_import_openapi` | Import OpenAPI/Swagger spec, generate requests, populate sitemap |
| `passive_intel` | Extract secrets, tokens, emails, IPs from proxy history (30+ patterns) |

### WebSocket (7 tools)
| Tool | Description |
|------|-------------|
| `websocket_create` | Create WebSocket connection |
| `websocket_send_text` | Send text message |
| `websocket_send_binary` | Send binary message |
| `websocket_close` | Close connection |
| `websocket_list` | List active connections |
| `websocket_get_messages` | Get messages with direction filter |
| `websocket_set_intercept_rule` | Auto-intercept WebSocket messages |

### Config & BurpSuite (16 tools)
| Tool | Description |
|------|-------------|
| `burp_version` | Get Burp version and edition |
| `burp_export_project_config` / `burp_import_project_config` | Project config as JSON |
| `burp_export_user_config` / `burp_import_user_config` | User config as JSON |
| `burp_task_engine_state` / `burp_task_engine_set` | Pause/resume all background tasks |
| `burp_command_line_args` | Get startup arguments |
| `burp_shutdown` | Shutdown Burp Suite |
| `config_proxy_listeners_list` / `add` / `remove` | Manage proxy listeners |
| `config_match_replace_add` / `list` / `remove` | Manage match-and-replace rules |
| `config_upstream_proxy_set` | Configure upstream proxy (Tor, corporate) |

### Other (22 tools)
| Tool | Description |
|------|-------------|
| `scope_check` / `scope_include` / `scope_exclude` / `scope_get_config` | Target scope management |
| `sitemap_query` / `sitemap_get_issues` / `sitemap_add_request` / `sitemap_add_issue` | Sitemap operations |
| `repeater_send` | Send request to Repeater tab |
| `intruder_send` / `intruder_send_with_positions` / `intruder_register_payload_processor` | Intruder operations |
| `organizer_send` / `organizer_get_items` | Organizer management |
| `session_create_token_rule` / `session_list_rules` / `session_remove_rule` | Session token handling |
| `events_get` / `events_get_by_type` / `events_subscribe` / `events_unsubscribe` / `events_clear` | Event system |
| `persistence_store` / `persistence_get` / `persistence_delete` / `persistence_list` | Project data storage |
| `preference_store` / `preference_get` | Global preferences |
| `log_message` / `log_event` | Burp logging |
| `decoder_send` / `comparer_send` | Send to Decoder/Comparer |
| `ai_status` / `ai_prompt` | Burp AI integration |
| `bambda_import` | Import Bambda scripts |
| `project_info` / `extension_info` | Project and extension metadata |

---

## Highlight Features

### Race Condition Testing
```
http_race(
  request: "POST /api/transfer HTTP/1.1\r\nHost: bank.com\r\n\r\n{\"amount\":100}",
  host: "bank.com", port: 443, count: 20
)
```
Sends 20 identical requests simultaneously using Burp's parallel engine. Analyzes response status code and body length distributions to detect TOCTOU, double-spend, and limit bypass vulnerabilities.

### Auth Level Diffing
```
auth_diff(
  request: "GET /api/users/1 HTTP/1.1\r\nHost: api.com\r\n\r\n",
  host: "api.com", port: 443,
  auth_levels: [
    {"name": "admin", "header_name": "Authorization", "header_value": "Bearer admin-token"},
    {"name": "user", "header_name": "Authorization", "header_value": "Bearer user-token"},
    {"name": "none"}
  ]
)
```
Sends the same request with different auth levels, compares responses, and flags IDOR, privilege escalation, and missing authorization.

### Inline Fuzzer (3 Modes)
```
# Mode 1: FUZZ keyword — simplest
http_fuzz(request: "GET /api?id=FUZZ HTTP/1.1\r\nHost: api.com\r\n\r\n",
          host: "api.com", port: 443,
          payloads: ["1", "2", "admin", "../../etc/passwd"])

# Mode 2: Section markers — like Burp Intruder
http_fuzz(request: "GET /api?id=§1§&role=§user§ HTTP/1.1\r\nHost: api.com\r\n\r\n",
          host: "api.com", port: 443,
          payloads: ["admin"])

# Mode 3: Byte offsets — precise control
http_fuzz(request: "GET /status/200 HTTP/1.1\r\nHost: httpbin.org\r\n\r\n",
          host: "httpbin.org", port: 443,
          positions: [[12, 15]], payloads: ["201", "404"])
```

### Custom Scan Checks

**BCheck mode** — deploy vulnerability checks using Burp's BCheck DSL:
```
bcheck_create(
  name: "AWS Key Leak", type: "passive_response",
  match_pattern: "AKIA[0-9A-Z]{16}",
  severity: "high", confidence: "firm",
  issue_detail: "AWS Access Key ID found in response"
)
```

**Script mode** — multi-step active checks with conditional payloads:
```
scancheck_create_active(
  name: "SSTI Detection",
  steps: [
    {"payload": "{{7*7}}", "response_conditions": [
      {"location": "response_body", "pattern": "49", "condition_type": "contains"}
    ]},
    {"payload": "{{7*6}}", "response_conditions": [
      {"location": "response_body", "pattern": "42", "condition_type": "contains"}
    ]}
  ],
  severity: "high", confidence: "firm"
)
```

### API Schema Import
```
api_import_openapi(
  spec_json: "<your swagger JSON>",
  auth_header: "Authorization",
  auth_value: "Bearer your-token",
  send_requests: true,
  add_to_sitemap: true
)
```
Parses OpenAPI 3.x and Swagger 2.0 specs. Generates requests with sample parameters and bodies for every endpoint. Optionally sends them through Burp, populating proxy history and sitemap.

### Passive Intelligence Extraction
```
passive_intel(max_items: 2000, in_scope_only: true)
```
Scans all captured proxy traffic for 30+ sensitive data patterns:
- **Cloud credentials**: AWS keys, Google API keys, GitHub tokens, Slack tokens, Stripe keys
- **Tokens & secrets**: JWTs, Bearer tokens, Basic auth, private keys
- **Personal data**: Emails, internal IPs, phone numbers
- **Cloud resources**: S3 buckets, Azure storage, GCS buckets
- **Infrastructure**: Internal URLs, GraphQL endpoints, API paths
- **Errors**: Stack traces, SQL errors, debug info
- **Fingerprints**: Server versions, framework versions, PHP versions
- **Sensitive paths**: /admin, /.env, /.git, /debug, /actuator

---

## Web Dashboard

Open **http://127.0.0.1:9878** for the real-time dashboard:

- **Live Activity Stream** with noise filtering (auto-hides Google/Apple/Microsoft connectivity checks)
- **Attack Vector Detection** — badges for AUTH, API, PARAMS, UPLOAD, ADMIN, DATA endpoints
- **Request Detail Panel** — click any item for full request info + send-to-tool actions
- **Stats Bar** — live counters for events, in-scope hosts, and attack vector categories
- **Filter Controls** — type filters, URL search, noise toggle
- **Active Rules** — view all proxy, traffic, and session rules
- **Connection Info** — transport URLs and MCP client config

---

## Architecture

```
+---------------------------------------------------+
|                BURP SUITE PRO                      |
|  +---------------------------------------------+  |
|  |        BurpMCP-Ultra Extension               |  |
|  |                                              |  |
|  |  Montoya API --> Bridge Layer (22 bridges)   |  |
|  |       |                |                     |  |
|  |  Event Bus    Tool Registry (137 tools)      |  |
|  |       |                |                     |  |
|  |       +------- MCP Server Core --------+     |  |
|  |               (Kotlin SDK 0.9.0)       |     |  |
|  |                    |                   |     |  |
|  |         +----------+----------+        |     |  |
|  |    SSE :9876   Streamable   Dashboard  |     |  |
|  |    /sse       HTTP :9877/   :9878      |     |  |
|  |              + SSE :9877/sse           |     |  |
|  +---------------------------------------------+  |
+---------------------------------------------------+
```

## Tech Stack

| Component | Version |
|-----------|---------|
| Kotlin | 2.1.20 |
| JVM Target | 17 |
| Montoya API | 2026.2 |
| MCP Kotlin SDK | 0.9.0 |
| Ktor CIO | 3.2.3 |
| kotlinx.serialization | 1.8.1 |
| Shadow JAR | 8.1.1 |

## Requirements

- Burp Suite Professional 2025.x or later
- Java 17+ (included in Burp's bundled JRE)
- Gradle 8.x (included via wrapper)

## Building from Source

```bash
git clone https://github.com/Cy-S3c/BurpMCP-Ultra.git
cd BurpMCP-Ultra
./gradlew shadowJar
# Output: build/libs/burpmcp-ultra-2.0.1.jar
```

## Project Structure

```
BurpMCP-Ultra/
├── build.gradle.kts              # Build configuration
├── configs/                      # Ready-to-use config files
│   ├── Caddyfile                 # Caddy reverse proxy
│   ├── mcp-claude-code-direct.json
│   ├── mcp-claude-code-caddy.json
│   ├── mcp-claude-desktop.json
│   └── setup.sh                  # Automated setup
├── src/main/kotlin/com/burpmcp/ultra/
│   ├── core/                     # Extension entry point + helpers
│   ├── bridge/                   # 22 Montoya API bridges
│   ├── tools/                    # 29 tool category modules
│   ├── transport/                # MCP server + dashboard
│   ├── events/                   # Unified event bus
│   ├── state/                    # State management
│   └── ui/                       # Swing UI tab
└── docs/                         # Tool catalog + plans
```

## License

MIT

---

<p align="center">
  Built for bug bounty hunters who want AI-powered Burp Suite automation.
</p>
