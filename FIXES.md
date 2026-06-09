# BurpMCP-Ultra 问题修复报告

## 日期

2026-06-09

---

## 第一部分：Hermes 连接兼容性问题（已修复）

### 关联 Issue

- [GitHub Issue #2](https://github.com/Cy-S3c/BurpMCP-Ultra/issues/2)
- [GitHub Issue #3](https://github.com/Cy-S3c/BurpMCP-Ultra/issues/3) — "端口并未开启"

### 排查过程

1. **Hermes 日志分析**（`~/.hermes/logs/errors.log`）：发现 `[Errno 61] Connect call failed`、`400 Bad Request`、`Event loop is closed` 等错误模式
2. **Hermes MCP 客户端源码分析**（`~/.hermes/hermes-agent/tools/mcp_tool.py`）：追踪了 `_load_mcp_config()` → `MCPServerTask.run()` → `_run_http()` 完整连接流程，确认 Hermes 默认使用 Streamable HTTP，仅当 `config["transport"] == "sse"` 时才走 SSE
3. **Kotlin MCP SDK JAR 反编译**：确认 SDK 0.8.3 仅有 `mcp()`（SSE），SDK 0.9.0 新增 `mcpStreamableHttp()`

### 根因与修复

| # | 问题 | 根因 | 修复 | 状态 |
|---|------|------|------|------|
| A | 端口未开但 UI 显示"Running" | `scope.launch` + `start(wait=false)`，绑定失败不可见 | 新增 `waitForPort()` TCP 健康检查，UI 三态指示器 | ✅ |
| B | Hermes 400 Bad Request | BurpMCP 仅 SSE，Hermes 默认 Streamable HTTP | SDK 0.8.3→0.9.0，端口 9877 新增 `mcpStreamableHttp()` | ✅ |
| C | SSE 插件未安装 | `routing { route("sse") { mcp() } }` 需手动 `install(SSE)` | 端口 9876 添加 `install(SSE)` | ✅ |
| D | SSE 插件重复安装 | `mcpStreamableHttp` 内部已安装 SSE | 端口 9877 移除显式 `install(SSE)` | ✅ |
| E | `mcpStreamableHttp` 路由不生效 | 嵌套在 `routing {}` 内部导致 Application 级别路由未注册 | 移到 `routing {}` 外部 | ✅ |
| **F** | **Hermes 406 Not Acceptable** | `ContentNegotiation` 插件未安装，`call.respond(JSONRPCError/InitializeResult)` 序列化失败，Ktor 返回 406 | 端口 9877 添加 `install(ContentNegotiation) { json() }` | ✅ 已验证 |

### 端口规划

| 端口 | 协议 | 端点 | 适用客户端 |
|------|------|------|-----------|
| 9876 | SSE | `/sse` | Claude Code、MCP Inspector、supergateway |
| 9877 | Streamable HTTP + SSE | `/mcp` (Streamable HTTP) + `/sse` (SSE) | Hermes、其他默认 Streamable HTTP 的客户端 |

### Hermes 配置

```yaml
# ~/.hermes/config.yaml
mcp_servers:
  burpmcp:
    enabled: true
    url: http://127.0.0.1:9877/mcp
```

清理旧 OAuth 配置：`hermes mcp rm Burpmcp`

### 未解决问题 → 已修复

~~**Hermes "Session terminated"**：Streamable HTTP 握手建立后会话终止。排查方向：Python MCP SDK 与 Kotlin MCP SDK 0.9.0 之间的 `mcpStreamableHttp` 实现兼容性。待继续排查。~~

→ **根因已定位（Issue #F）**：见下方第三部分。

---

## 第二部分：10 个工具 Bug 修复

### Bug 1（严重）：proxy_history_search / proxy_annotate NPE — MCP Error -32603

- **文件**：`src/main/kotlin/com/burpmcp/ultra/bridge/ProxyBridge.kt`
- **根因**：Burp Montoya API 返回的 `List<ProxyHttpRequestResponse>` 是惰性代理列表。底层 Burp 内部对象被回收后，调用 `.id()` 抛出 NPE。修复经历三次迭代：
  1. 第一次：仅包裹 `serializeHistoryItem()` 内的属性访问 → **无效**（NPE 发生在序列化之前）
  2. 第二次：包裹 `ProxyHistoryFilter` lambda 和 `api.proxy().history()` 调用 → **无效**（列表惰性求值，NPE 在 `.take()`/`.forEach` 迭代时触发，不在 try-catch 内）
  3. 第三次：`api.proxy().history(filter).toList()` 强制在 try-catch 内完成列表实体化，同时修复 `annotateHistoryItem()`、`serializeWebSocketItem()` 的未保护路径 → **待验证**
- **状态**：🟡 第三次修复待验证

### Bug 2：sitemap_query limit 参数无效

- **文件**：`src/main/kotlin/com/burpmcp/ultra/tools/sitemap/SitemapTools.kt`
- **根因**：参数名为 `max_results`，用户传 `limit` 被忽略回退到默认值 100
- **修复**：第 27 行添加 `args["limit"]?.jsonPrimitive?.intOrNull` 回退
- **状态**：✅ 已验证

### Bug 3：bcheck_create content/script 参数被忽略

- **文件**：`src/main/kotlin/com/burpmcp/ultra/tools/bcheck/BCheckTools.kt`
- **根因**：`bcheck_create` 总是调用 `generateScript()` 自动生成，没有接收原始脚本的参数
- **修复**：新增可选 `script` 参数（别名 `content`），提供时调用 `bridge.importRaw(script)`
- **状态**：✅ 已验证

### Bug 4：passive_intel 无分页，返回过大

- **文件**：`src/main/kotlin/com/burpmcp/ultra/tools/passiveintel/PassiveIntelTools.kt`、`PassiveIntelBridge.kt`
- **根因**：仅有 `max_items` 控制扫描范围（`takeLast`），无分页
- **修复**：新增 `offset`（默认 0）、`limit`（默认 200）；`max_items` 默认 1000→200；响应新增 `has_more`、`total_findings` 字段
- **状态**：✅ 已验证

### Bug 5：analyze_response reason_phrase 被 HTTP 头污染

- **文件**：`src/main/kotlin/com/burpmcp/ultra/bridge/AnalysisBridge.kt`
- **根因**：Montoya `HttpResponse.httpResponse()` 对非标准行尾解析异常
- **修复**：解析前行尾规范化（`\r\n`）；对 `reasonPhrase()` 含换行符时截取首行
- **状态**：✅ 已验证

### Bug 6：analyze_response_body_search 搜索代理历史而非传入响应

- **文件**：`src/main/kotlin/com/burpmcp/ultra/tools/analysis/AnalysisTools.kt`、`AnalysisBridge.kt`
- **根因**：`searchResponseBodies()` 总是遍历 `api.proxy().history()`
- **修复**：新增可选 `response` 参数；提供时搜索该响应体，否则保持代理历史搜索
- **状态**：✅ 已验证

### Bug 7：util_compress 要求 Base64 输入

- **文件**：`src/main/kotlin/com/burpmcp/ultra/tools/utilities/UtilitiesTools.kt`、`UtilitiesBridge.kt`
- **根因**：`Base64.getDecoder().decode(data)` 对原始文本抛出 `IllegalArgumentException`
- **修复**：默认 `input_type` 改为 `"auto"`：先尝试 base64 解码，失败回退 UTF-8 编码
- **状态**：✅ 已验证

### Bug 8：repeater_send / intruder_send TLS 不自动检测

- **文件**：`src/main/kotlin/com/burpmcp/ultra/tools/repeater/RepeaterTools.kt`、`IntruderTools.kt`
- **根因**：`use_tls` 硬编码默认 `false`
- **修复**：复用 `http_send_raw_bytes` 已有模式：`use_tls ?: https ?: (port == 443)`
- **状态**：✅ 已验证

### Bug 9：analyze_response headers/cookies 为空

- **文件**：`src/main/kotlin/com/burpmcp/ultra/bridge/AnalysisBridge.kt`
- **根因**：Montoya 解析器对部分原始响应无法提取头部
- **修复**：新增 `fallbackParseResponseHeaders()` 回退解析器
- **状态**：✅ 已验证

### Bug 10：参数命名一致性 — 7 个别名

| 工具 | 原参数 | 新增别名 |
|------|--------|---------|
| `http_send_request` | `raw_request` | `request` |
| `config_match_replace_remove` | `rule_index` | `index` |
| `scanner_start_crawl/audit` | `urls` | `url`（单值自动包装） |
| `analyze_diff` | `item1`/`item2` | `request1`/`request2` |
| `http_analyze_keywords` | `keywords` | `keyword`（单值自动包装） |
| `comparer_send` | `data` | `item1`/`item2` |
| `http_send_request_chain` | `steps` | `requests` |

- **状态**：✅

---

## 修改文件汇总

| 文件 | 涉及问题 |
|------|---------|
| `build.gradle.kts` | MCP SDK 0.8.3 → 0.9.0 |
| `gradle.properties` | macOS JDK 路径修复 |
| `src/main/kotlin/com/burpmcp/ultra/transport/McpServerManager.kt` | Hermes 连接兼容（A-E） |
| `src/main/kotlin/com/burpmcp/ultra/core/BurpMcpUltraExtension.kt` | 启动日志 URL 更新 |
| `src/main/kotlin/com/burpmcp/ultra/ui/BurpMcpUltraTab.kt` | 三态状态指示器 |
| `src/main/kotlin/com/burpmcp/ultra/bridge/ProxyBridge.kt` | Bug 1（NPE 防护） |
| `src/main/kotlin/com/burpmcp/ultra/bridge/AnalysisBridge.kt` | Bug 5, 6, 9（分析工具修复） |
| `src/main/kotlin/com/burpmcp/ultra/bridge/PassiveIntelBridge.kt` | Bug 4（分页） |
| `src/main/kotlin/com/burpmcp/ultra/bridge/UtilitiesBridge.kt` | Bug 7（自动检测输入格式） |
| `src/main/kotlin/com/burpmcp/ultra/tools/sitemap/SitemapTools.kt` | Bug 2 |
| `src/main/kotlin/com/burpmcp/ultra/tools/bcheck/BCheckTools.kt` | Bug 3 |
| `src/main/kotlin/com/burpmcp/ultra/tools/passiveintel/PassiveIntelTools.kt` | Bug 4 |
| `src/main/kotlin/com/burpmcp/ultra/tools/analysis/AnalysisTools.kt` | Bug 6, 10d |
| `src/main/kotlin/com/burpmcp/ultra/tools/utilities/UtilitiesTools.kt` | Bug 7 |
| `src/main/kotlin/com/burpmcp/ultra/tools/repeater/RepeaterTools.kt` | Bug 8 |
| `src/main/kotlin/com/burpmcp/ultra/tools/intruder/IntruderTools.kt` | Bug 8 |
| `src/main/kotlin/com/burpmcp/ultra/tools/http/HttpTools.kt` | Bug 10a, 10e, 10g |
| `src/main/kotlin/com/burpmcp/ultra/tools/config/ConfigTools.kt` | Bug 10b |
| `src/main/kotlin/com/burpmcp/ultra/tools/scanner/ScannerTools.kt` | Bug 10c |
| `src/main/kotlin/com/burpmcp/ultra/tools/comparer/ComparerTools.kt` | Bug 10f |
| `src/main/kotlin/com/burpmcp/ultra/transport/McpServerManager.kt` | Hermes 连接兼容（A-F） + ContentNegotiation |

---

## 第三部分：Hermes 406 Not Acceptable 详细分析（2026-06-09）

### 排查方法

并行派出 3 个 agent 从不同方向排查：
1. **Hermes Python 客户端源码**（`~/.hermes/hermes-agent/`）
2. **Kotlin MCP SDK 0.9.0 字节码**（反编译 `StreamableHttpServerTransport.class`）
3. **BurpMCP-Ultra HTTP 层配置**（CORS、ContentNegotiation、路由）

### 实验验证

通过 curl 模拟 Hermes 请求，确认所有 POST /mcp 请求（无论 Accept 头如何设置）都返回 406，且响应体为空（Content-Length: 0）。

### 根因

**ContentNegotiation 插件缺失**。`mcpStreamableHttp` 在处理 POST 请求时，无论是成功响应（`InitializeResult`）还是错误响应（`JSONRPCError`），都通过 Ktor 的 `call.respond(object, typeInfo)` 序列化 JSON。但端口 9877 的 Application 配置中只安装了 CORS，没有安装 `ContentNegotiation`。

Ktor 在无法找到合适的序列化器时，返回 406 Not Acceptable（因为无法产生匹配 Accept 头的内容类型）。

Claude 不受影响的原因是：Claude 使用端口 9876 的 SSE 传输，SSE 直接通过 `ServerSSESession.send()` 发送事件，不经过 Ktor 的 ContentNegotiation 管道。

### 修复

在 `McpServerManager.kt` 端口 9877 的 Application 配置中添加：
```kotlin
install(ContentNegotiation) {
    json()
}
```

### Hermes 配置注意事项

`mcpStreamableHttp` 默认路径为 `/mcp`（非 `/`）：
```yaml
# ~/.hermes/config.yaml
mcp_servers:
  burpmcp:
    enabled: true
    url: http://127.0.0.1:9877/mcp
```

---

## 当前状态

| 类别 | 已修复 | 待验证 | 未修复 |
|------|--------|--------|--------|
| Hermes 连接 | 6 | 0 | 0 |
| 工具 Bug | 8 | 1（Bug 1 NPE 第三次修复） | 0 |
| 参数别名 | 7 | 0 | 0 |
| **总计** | **21** | **1** | **0** |
