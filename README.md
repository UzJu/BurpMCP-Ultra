<p align="center">
  <h1 align="center">BurpMCP-Ultra</h1>
  <p align="center">
    <strong>Burp Suite Professional 最强大的 MCP 服务器</strong>
  </p>
  <p align="center">
    137 个工具 &bull; 14 个资源 &bull; 12 种事件类型 &bull; 实时仪表盘 &bull; 完整 Montoya API 覆盖
  </p>
  <p align="center">
    <a href="#fork--修复说明">Fork & 修复</a> &bull;
    <a href="#快速开始">快速开始</a> &bull;
    <a href="#全部-137-个工具">全部工具</a> &bull;
    <a href="#重点功能">重点功能</a> &bull;
    <a href="#web-仪表盘">仪表盘</a> &bull;
    <a href="#接入指南">接入指南</a>
  </p>
</p>

---

> **BurpMCP-Ultra** 是一个原生 Kotlin Burp Suite 扩展，内置 MCP (Model Context Protocol) 服务器。将单个 JAR 文件加载至 Burp，连接 Claude Code 或任意 MCP 客户端，即可通过 AI 代理控制 Burp Suite 的方方面面。

---

## Fork & 修复说明

**本项目是 [Cy-S3c/BurpMCP-Ultra](https://github.com/Cy-S3c/BurpMCP-Ultra) (v2.0.1) 的 Fork 版本，修复了原始项目中的多个关键 Bug。**

原始项目存在以下问题，导致无法正常使用：

### 已修复的关键问题

| # | 问题 | 根因 | 修复方案 | 状态 |
|---|------|------|---------|------|
| A | 端口显示"Running"但实际未监听 | `start(wait=false)` 吞掉了绑定失败异常 | 新增 `waitForPort()` TCP 健康检查，UI 三态指示器 | ✅ |
| B | Hermes 客户端报 `400 Bad Request` | 原项目仅支持 SSE；Hermes 默认使用 Streamable HTTP | SDK 0.8.3→0.9.0，端口 9877 新增 `mcpStreamableHttp()` | ✅ |
| C | 端口 9876 SSE 插件未安装 | SSE 传输配置中缺少 `install(SSE)` | 端口 9876 添加 `install(SSE)` | ✅ |
| D | 端口 9877 SSE 插件重复安装 | `mcpStreamableHttp` 内部会自动安装 SSE | 移除端口 9877 的显式 `install(SSE)` | ✅ |
| **E** | **Hermes 连接报 `406 Not Acceptable`** | `ContentNegotiation` 插件未安装，Ktor 无法序列化 JSON-RPC 响应 | 端口 9877 添加 `install(ContentNegotiation) { json() }` | ✅（经 Hermes 端到端实测验证） |
| F | `proxy_history_search` 抛空指针异常 | Montoya API 返回惰性代理列表，内部对象可能被回收 | `.toList()` 强制在 try-catch 内完成列表实体化 | ✅ |
| G | `sitemap_query` 忽略 `limit` 参数 | 参数名为 `max_results`，用户传 `limit` 被忽略 | 新增 `limit` 别名回退 | ✅ |
| H | `bcheck_create` 忽略 `content`/`script` 参数 | 总是自动生成脚本，未接收原始脚本参数 | 新增可选 `script` 参数，别名 `content` | ✅ |
| I | `passive_intel` 无分页，响应过大 | 仅有 `max_items`（takeLast），无 offset/limit | 新增 `offset` + `limit`，响应新增 `has_more` + `total_findings` | ✅ |
| J | `repeater_send` / `intruder_send` TLS 不自动检测 | `use_tls` 硬编码为 `false` | TLS 自动检测：`use_tls ?: https ?: (port == 443)` | ✅ |
| K | `util_compress` 要求 Base64 输入 | 对原始文本调用 `Base64.getDecoder().decode()` 报错 | `input_type` 默认改为 `"auto"`，先试 Base64 解码，失败回退 UTF-8 | ✅ |
| L | `analyze_response` headers/cookies 为空 | Montoya 解析器对部分原始响应解析失败 | 新增 `fallbackParseResponseHeaders()` 回退解析器 | ✅ |
| M | 7 个工具参数命名不一致 | 不同工具对相同概念使用了不同的参数名 | 新增 7 个参数别名 | ✅ |

### 端口规划

| 端口 | 协议 | 端点 | 适用客户端 |
|------|------|------|-----------|
| 9876 | SSE | `/sse` | Claude Code、MCP Inspector、supergateway |
| **9877** | **Streamable HTTP** | **`/mcp`** | **Hermes**（默认协议）、其他现代 MCP 客户端 |
| 9877 | SSE（备选） | `/sse` | 其他 SSE 客户端 |
| 9878 | HTTP | `/` | Web 仪表盘 |

> **Hermes 用户请注意**：Streamable HTTP 端点为 `/mcp`（不是 `/`），配置中 URL 应为 `http://127.0.0.1:9877/mcp`。

### 完整修复文档

详见 [FIXES.md](FIXES.md)。

---

## 为什么选择 BurpMCP-Ultra？

| | BurpMCP-Ultra | burp-ai-agent | PortSwigger 官方 |
|---|:---:|:---:|:---:|
| **MCP 工具数** | **137** | 53 | 12 |
| **自定义扫描检查** | BCheck + Script | - | - |
| **WebSocket 测试** | 全生命周期 | - | - |
| **内联 Fuzzer** | 3 种模式 (FUZZ/Marker/Offset) | - | - |
| **竞态条件测试** | 单包攻击 (Single-packet) | - | - |
| **权限级别差分** | IDOR/越权检测 | - | - |
| **API Schema 导入** | OpenAPI/Swagger | - | - |
| **被动信息提取** | 30+ 种模式 | - | - |
| **实时仪表盘** | Web + Swing | - | - |
| **事件流** | 12 种事件类型 | - | - |
| **响应差异分析** | 盲注入检测 | - | - |
| **请求链宏** | 多步骤 + Token 提取 | - | - |
| **Collaborator OOB** | 完整创建/轮询/关联 | 部分 | 部分 |

---

## 快速开始

### 1. 编译

```bash
git clone https://github.com/Cy-S3c/BurpMCP-Ultra.git
cd BurpMCP-Ultra
./gradlew shadowJar
```

输出：`build/libs/burpmcp-ultra-2.0.1.jar`（约 13 MB）

也可直接下载预编译版本：`burpmcp-ultra.jar`

### 2. 加载至 Burp

1. Burp Suite Pro → **Extensions** → **Add**
2. 选择 JAR 文件
3. 约 2 秒后，**BurpMCP-Ultra** 标签页状态指示灯变绿，表示两个端口均已监听

### 3. 连接 MCP 客户端

扩展开启两个端口，针对不同客户端协议优化：

| 端口 | 协议 | 端点 | 适用客户端 |
|------|------|------|-----------|
| **9876** | SSE | `http://127.0.0.1:9876/sse` | Claude Code、MCP Inspector、supergateway |
| **9877** | Streamable HTTP | `http://127.0.0.1:9877/mcp` | **Hermes**（默认协议）、其他 Streamable HTTP 客户端 |
| **9877**（备选） | SSE | `http://127.0.0.1:9877/sse` | 不希望使用 Streamable HTTP 的 SSE 客户端 |

**Claude Code** — SSE 传输，端口 9876：

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

> **重要**：URL 必须以 `/mcp` 结尾，这是 Kotlin MCP SDK `mcpStreamableHttp` 的默认路径。

也可使用 `hermes mcp add` 命令：

```bash
hermes mcp add burpmcp --url http://127.0.0.1:9877/mcp
# 提示 "Does this server require authentication?" → 输入 N
# 提示 "API key / Bearer token:" → 留空回车

# 如果之前通过 hermes mcp add 以 OAuth 方式注册过，先清理旧配置：
hermes mcp rm Burpmcp
```

**其他 SSE 客户端** — 端口 9877 的 SSE 备选端点：

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

### 4. 打开仪表盘

浏览器访问 **http://127.0.0.1:9878** 查看实时 Web 仪表盘。

---

## 接入指南

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
```

### 重要提示

- **URL 路径必须是 `/mcp`**，不是 `/`。这是 Kotlin MCP SDK `mcpStreamableHttp` 的默认路由路径。
- 端口 9877 同时支持 Streamable HTTP（`/mcp`）和 SSE（`/sse`），但 Hermes 应使用 Streamable HTTP 端点 `/mcp`。
- 如果之前用旧版本 BurpMCP 或以 OAuth 方式注册过，先执行 `hermes mcp rm Burpmcp` 清理。

### 故障排除

| 错误 | 原因 | 解决方法 |
|------|------|---------|
| `406 Not Acceptable` | BurpMCP 版本 < 2.0.2，缺少 ContentNegotiation 插件 | 更新到最新版 BurpMCP-Ultra |
| `404 Not Found` | URL 路径错误（使用了 `/` 而非 `/mcp`） | 将 URL 改为 `http://127.0.0.1:9877/mcp` |
| `Connection refused` | Burp Suite 未启动或扩展未加载 | 检查 Burp 中 BurpMCP-Ultra 扩展状态 |
| `Session terminated` | 连接过程异常中断 | 重启 Hermes，查看 Burp 日志 |

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

脚本执行：编译 JAR、可选配置 Caddy、显示需添加的 MCP 配置。

</details>

---

## 全部 137 个工具

### 代理 (13 个工具)
| 工具 | 描述 |
|------|------|
| `proxy_history` | 获取 HTTP 代理历史，支持过滤（域名、方法、状态码、MIME、范围） |
| `proxy_history_search` | 在代理历史中正则搜索（URL、头部、响应体） |
| `proxy_websocket_history` | 获取 WebSocket 代理历史 |
| `proxy_websocket_history_search` | 正则搜索 WebSocket 历史 |
| `proxy_intercept_enable` | 开启代理拦截 |
| `proxy_intercept_disable` | 关闭代理拦截 |
| `proxy_intercept_status` | 获取当前拦截状态 |
| `proxy_annotate` | 为历史记录添加高亮颜色和注释 |
| `proxy_set_request_rule` | 自动修改/丢弃/标记匹配模式的代理请求 |
| `proxy_set_response_rule` | 自动修改/丢弃/标记匹配模式的代理响应 |
| `proxy_list_rules` | 列出所有活跃的代理规则 |
| `proxy_remove_rule` | 移除代理规则 |
| `proxy_auto_auth` | 一键为所有匹配请求注入认证头 |

### HTTP (13 个工具)
| 工具 | 描述 |
|------|------|
| `http_send_request` | 发送 HTTP 请求（结构化或原始格式，HTTP/1.1 或 HTTP/2） |
| `http_send_requests_parallel` | 并行发送多个请求（竞态测试、批量操作） |
| `http_send_request_chain` | 多步骤请求序列，步骤间支持 Token 提取 |
| `http_fuzz` | 内联 Fuzzer，支持 FUZZ 关键字、段落标记、偏移量三种模式 |
| `http_send_raw_bytes` | 字节级 HTTP 请求，适用于走私和 CRLF 注入 |
| `http_race` | 竞态条件测试 — 同时发送 N 个请求 |
| `http_cookie_jar_get` | 从 Burp Cookie Jar 获取 Cookie |
| `http_cookie_jar_set` | 向 Cookie Jar 设置 Cookie |
| `http_analyze_keywords` | 分析响应中出现的关键词 |
| `http_analyze_variations` | 检测响应差异，用于盲注入 |
| `http_set_traffic_rule` | 注册全局请求/响应修改规则 |
| `http_list_traffic_rules` | 列出活跃的流量规则 |
| `http_remove_traffic_rule` | 移除流量规则 |

### 扫描器 (12 个工具)
| 工具 | 描述 |
|------|------|
| `scanner_start_crawl` | 从种子 URL 启动爬虫 |
| `scanner_start_audit` | 启动主动/被动扫描，支持认证配置 |
| `scanner_task_status` | 获取扫描任务进度（请求数、错误数、问题数） |
| `scanner_task_list` | 列出所有扫描/爬虫任务 |
| `scanner_task_delete` | 取消并移除扫描任务 |
| `scanner_task_add_request` | 向运行中的审计添加请求 |
| `scanner_task_issues` | 获取特定任务的问题 |
| `scanner_get_all_issues` | 获取所有扫描问题，支持严重程度/置信度筛选 |
| `scanner_generate_report` | 生成 HTML/XML 扫描报告 |
| `scanner_create_issue` | 创建自定义审计问题 |
| `scanner_import_bcheck` | 导入 BCheck 脚本用于自定义扫描 |
| `scanner_register_check` | 注册自定义主动/被动扫描检查 |

### 实用工具 (13 个工具)
| 工具 | 描述 |
|------|------|
| `util_url_encode` / `util_url_decode` | URL 编解码 |
| `util_base64_encode` / `util_base64_decode` | Base64 编解码 |
| `util_html_encode` | HTML 实体编码 |
| `util_hash` | 加密哈希（MD5、SHA1、SHA256、SHA384、SHA512） |
| `util_compress` / `util_decompress` | Gzip/deflate/brotli 压缩解压 |
| `util_random_string` | 生成随机字符串 |
| `util_random_bytes` | 生成随机字节 |
| `util_jwt_decode` | 解码 JWT Token（Header、Payload、过期检查） |
| `util_decode_smart` | 自动检测并解码多层编码 |
| `util_shell_execute` | 在 Burp 上下文中执行 Shell 命令 |

### BCheck 模式 (5 个工具)
| 工具 | 描述 |
|------|------|
| `bcheck_create` | 从结构化参数生成并部署 BCheck |
| `bcheck_import` | 导入原始 BCheck DSL 脚本 |
| `bcheck_templates` | 获取所有 BCheck 模板及 DSL 参考 |
| `bcheck_list` | 列出已部署的 BCheck |
| `bcheck_remove` | 移除已部署的 BCheck |

### Script 模式 (5 个工具)
| 工具 | 描述 |
|------|------|
| `scancheck_create_passive` | 创建被动检查，支持多条件匹配 |
| `scancheck_create_active` | 创建多步骤主动检查，支持 Payload 链 |
| `scancheck_templates` | 获取 Script 模式模板和条件参考 |
| `scancheck_list` | 列出已部署的 Script 检查 |
| `scancheck_remove` | 取消注册 Script 检查 |

### Collaborator (6 个工具)
| 工具 | 描述 |
|------|------|
| `collaborator_create_client` | 创建 Collaborator 客户端用于 OOB 测试 |
| `collaborator_restore_client` | 从密钥恢复客户端 |
| `collaborator_generate_payload` | 生成 Collaborator Payload |
| `collaborator_poll` | 轮询 DNS/HTTP/SMTP 交互记录 |
| `collaborator_server_info` | 获取 Collaborator 服务器地址 |
| `collaborator_get_secret` | 获取客户端密钥用于会话持久化 |

### 分析 (7 个工具) + 高级 (3 个工具)
| 工具 | 描述 |
|------|------|
| `analyze_request` | 解析 HTTP 请求为结构化组件 |
| `analyze_response` | 解析 HTTP 响应为结构化组件 |
| `analyze_find_reflected` | 查找响应中反射的参数值（XSS 检测） |
| `analyze_extract_params` | 提取所有参数（URL、Body、Cookie、Header、JSON） |
| `analyze_insertion_points` | 获取扫描器式插入点 |
| `analyze_diff` | 对比两个请求或响应 |
| `analyze_response_body_search` | 在代理历史所有响应体中搜索模式 |
| `auth_diff` | 对比不同权限级别的响应（IDOR/越权检测） |
| `api_import_openapi` | 导入 OpenAPI/Swagger 规范，生成请求，填充站点地图 |
| `passive_intel` | 从代理历史提取密钥、Token、邮箱、IP 等（30+ 种模式） |

### WebSocket (7 个工具)
| 工具 | 描述 |
|------|------|
| `websocket_create` | 创建 WebSocket 连接 |
| `websocket_send_text` | 发送文本消息 |
| `websocket_send_binary` | 发送二进制消息 |
| `websocket_close` | 关闭连接 |
| `websocket_list` | 列出活跃连接 |
| `websocket_get_messages` | 获取消息，支持方向筛选 |
| `websocket_set_intercept_rule` | 自动拦截 WebSocket 消息 |

### 配置 & BurpSuite (16 个工具)
| 工具 | 描述 |
|------|------|
| `burp_version` | 获取 Burp 版本和版本类型 |
| `burp_export_project_config` / `burp_import_project_config` | 以 JSON 格式导出/导入项目配置 |
| `burp_export_user_config` / `burp_import_user_config` | 以 JSON 格式导出/导入用户配置 |
| `burp_task_engine_state` / `burp_task_engine_set` | 暂停/恢复所有后台任务 |
| `burp_command_line_args` | 获取启动参数 |
| `burp_shutdown` | 关闭 Burp Suite |
| `config_proxy_listeners_list` / `add` / `remove` | 管理代理监听器 |
| `config_match_replace_add` / `list` / `remove` | 管理匹配替换规则 |
| `config_upstream_proxy_set` | 配置上游代理（Tor、企业代理） |

### 其他 (22 个工具)
| 工具 | 描述 |
|------|------|
| `scope_check` / `scope_include` / `scope_exclude` / `scope_get_config` | 目标范围管理 |
| `sitemap_query` / `sitemap_get_issues` / `sitemap_add_request` / `sitemap_add_issue` | 站点地图操作 |
| `repeater_send` | 发送请求到 Repeater 标签页 |
| `intruder_send` / `intruder_send_with_positions` / `intruder_register_payload_processor` | Intruder 操作 |
| `organizer_send` / `organizer_get_items` | Organizer 管理 |
| `session_create_token_rule` / `session_list_rules` / `session_remove_rule` | Session Token 处理 |
| `events_get` / `events_get_by_type` / `events_subscribe` / `events_unsubscribe` / `events_clear` | 事件系统 |
| `persistence_store` / `persistence_get` / `persistence_delete` / `persistence_list` | 项目数据持久化 |
| `preference_store` / `preference_get` | 全局偏好设置 |
| `log_message` / `log_event` | Burp 日志 |
| `decoder_send` / `comparer_send` | 发送至 Decoder/Comparer |
| `ai_status` / `ai_prompt` | Burp AI 集成 |
| `bambda_import` | 导入 Bambda 脚本 |
| `project_info` / `extension_info` | 项目和扩展元数据 |

---

## 重点功能

### 竞态条件测试
```
http_race(
  request: "POST /api/transfer HTTP/1.1\r\nHost: bank.com\r\n\r\n{\"amount\":100}",
  host: "bank.com", port: 443, count: 20
)
```
利用 Burp 并行引擎同时发送 20 个相同请求。分析响应状态码和响应体长度分布，检测 TOCTOU、重复消费、限制绕过等漏洞。

### 权限级别差分
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
以不同权限级别发送相同请求，对比响应差异，标记 IDOR、越权、缺失授权等问题。

### 内联 Fuzzer（3 种模式）
```
# 模式 1：FUZZ 关键字 — 最简单
http_fuzz(request: "GET /api?id=FUZZ HTTP/1.1\r\nHost: api.com\r\n\r\n",
          host: "api.com", port: 443,
          payloads: ["1", "2", "admin", "../../etc/passwd"])

# 模式 2：段落标记 — 类似 Burp Intruder
http_fuzz(request: "GET /api?id=§1§&role=§user§ HTTP/1.1\r\nHost: api.com\r\n\r\n",
          host: "api.com", port: 443,
          payloads: ["admin"])

# 模式 3：字节偏移 — 精确控制
http_fuzz(request: "GET /status/200 HTTP/1.1\r\nHost: httpbin.org\r\n\r\n",
          host: "httpbin.org", port: 443,
          positions: [[12, 15]], payloads: ["201", "404"])
```

### 自定义扫描检查

**BCheck 模式** — 使用 Burp 的 BCheck DSL 部署漏洞检查：
```
bcheck_create(
  name: "AWS Key Leak", type: "passive_response",
  match_pattern: "AKIA[0-9A-Z]{16}",
  severity: "high", confidence: "firm",
  issue_detail: "AWS Access Key ID found in response"
)
```

**Script 模式** — 条件 Payload 的多步骤主动检查：
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

### API Schema 导入
```
api_import_openapi(
  spec_json: "<your swagger JSON>",
  auth_header: "Authorization",
  auth_value: "Bearer your-token",
  send_requests: true,
  add_to_sitemap: true
)
```
解析 OpenAPI 3.x 和 Swagger 2.0 规范，为每个端点生成带示例参数的请求。可选将请求通过 Burp 发送，填充代理历史和站点地图。

### 被动信息提取
```
passive_intel(max_items: 2000, in_scope_only: true)
```
扫描所有代理流量中的 30+ 种敏感数据模式：
- **云凭证**：AWS Key、Google API Key、GitHub Token、Slack Token、Stripe Key
- **Token & 密钥**：JWT、Bearer Token、Basic Auth、私钥
- **个人信息**：邮箱、内网 IP、手机号
- **云资源**：S3 Bucket、Azure Storage、GCS Bucket
- **基础设施**：内部 URL、GraphQL 端点、API 路径
- **错误信息**：堆栈跟踪、SQL 错误、调试信息
- **指纹信息**：服务器版本、框架版本、PHP 版本
- **敏感路径**：/admin、/.env、/.git、/debug、/actuator

---

## Web 仪表盘

浏览器访问 **http://127.0.0.1:9878** 查看实时仪表盘：

- **实时活动流**：带噪音过滤（自动隐藏 Google/Apple/Microsoft 连接检查）
- **攻击向量检测**：AUTH、API、PARAMS、UPLOAD、ADMIN、DATA 端点分类标记
- **请求详情面板**：点击任意条目查看完整请求信息 + 一键发送至工具
- **统计栏**：实时事件数、范围内主机数、攻击向量分类计数
- **筛选控件**：类型筛选、URL 搜索、噪音开关
- **活跃规则**：查看所有代理、流量、会话规则
- **连接信息**：传输 URL 和 MCP 客户端配置

---

## 架构

```
+---------------------------------------------------+
|                BURP SUITE PRO                      |
|  +---------------------------------------------+  |
|  |        BurpMCP-Ultra 扩展                     |  |
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
|  |    /sse       HTTP :9877/mcp  :9878    |     |  |
|  |              + SSE :9877/sse           |     |  |
|  +---------------------------------------------+  |
+---------------------------------------------------+
```

## 技术栈

| 组件 | 版本 |
|------|------|
| Kotlin | 2.1.20 |
| JVM 目标 | 17 |
| Montoya API | 2026.2 |
| MCP Kotlin SDK | 0.9.0 |
| Ktor CIO | 3.2.3 |
| kotlinx.serialization | 1.8.1 |
| Shadow JAR | 8.1.1 |

## 环境要求

- Burp Suite Professional 2025.x 及以上版本
- Java 17+（Burp 内置 JRE 已包含）
- Gradle 8.x（通过 wrapper 已包含）

## 从源码编译

```bash
git clone https://github.com/Cy-S3c/BurpMCP-Ultra.git
cd BurpMCP-Ultra
./gradlew shadowJar
# 输出：build/libs/burpmcp-ultra-2.0.1.jar
```

## 项目结构

```
BurpMCP-Ultra/
├── build.gradle.kts              # 构建配置
├── configs/                      # 预置配置文件
│   ├── Caddyfile                 # Caddy 反向代理配置
│   ├── mcp-claude-code-direct.json
│   ├── mcp-claude-code-caddy.json
│   ├── mcp-claude-desktop.json
│   └── setup.sh                  # 自动化安装脚本
├── src/main/kotlin/com/burpmcp/ultra/
│   ├── core/                     # 扩展入口 + 辅助类
│   ├── bridge/                   # 22 个 Montoya API 桥接
│   ├── tools/                    # 29 个工具分类模块
│   ├── transport/                # MCP 服务器 + 仪表盘
│   ├── events/                   # 统一事件总线
│   ├── state/                    # 状态管理
│   └── ui/                       # Swing UI 标签页
└── docs/                         # 工具目录 + 方案文档
```

## 许可证

MIT

---

<p align="center">
  为漏洞赏金猎人打造的 AI 驱动 Burp Suite 自动化利器。
</p>
