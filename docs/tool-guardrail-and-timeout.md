# 工具层护栏：超时、权限、线程池

本文记录对 `ToolRegistryService` 这一层做的一轮工程化加固，回答的问题是：
**大模型选完工具之后，系统怎么保证这次执行是安全的、可控的、不会把整条链路拖死。**

## 1. 优化项清单

| 编号 | 优化项 | 本次是否实现 |
| --- | --- | --- |
| 1 | if-else 分发改为注册表（`Map<String, ToolHandler>`） | 未做，先保稳定，见第 5 节 |
| 2 | 工具说明与执行逻辑分离（说明/可执行不一致） | 部分实现：启动自检对账 |
| 3 | 缺少工具层超时控制 | 已实现 |
| 4 | 缺少工具层调用者权限校验 | 已实现 |
| 5 | `@Async` 使用默认执行器，缺少业务线程池 | 已实现 |

## 2. 超时控制

问题：工具卡住时，外层 Agent 循环会一直等下去，用户只看到“正在处理”。

实现分三层：

```text
第一层：ToolRegistryService 工具总超时（FEISHU_TOOL_TIMEOUT_SECONDS，默认 90 秒）
第二层：Skill + CLI 单条命令超时（FEISHU_CLI_TIMEOUT_SECONDS，默认 60 秒）
第三层：HTTP 连接/读取超时（飞书 OpenAPI 与电商 MCP）
```

- 工具调用被提交到独立线程池执行，主线程只负责 `future.get(timeout)`；
- 超时后取消任务，并返回 `ToolResult.failed("工具执行超时…")`，让 Agent 自己决定重试还是换方案；
- HTTP 层补上超时：飞书 OpenAPI 默认 连接 3 秒 / 读取 20 秒，电商 MCP 默认 连接 3 秒 / 读取 15 秒。

## 3. 权限校验

问题：任何能给机器人发消息的人，都能通过群聊驱动机器人执行飞书操作和电商查询。

实现：`ToolPermissionService`

- `FEISHU_TOOL_ALLOWED_OPEN_IDS`：允许触发工具执行的用户白名单（open_id 或 user_id）；
- `FEISHU_TOOL_ALLOWED_CHAT_IDS`：允许触发工具执行的会话白名单；
- 两个白名单都为空时保持原有行为（不限制），并在启动日志里给出 WARN 提示，避免上线即打断现有机器人；
- 任一白名单非空即进入强校验，拒绝时返回可读原因，并只打印脱敏身份。

配套改动：`AgentOrchestratorService` 给**所有**工具注入真实事件上下文
（`sourceChatId`、`originalMessageId`、`senderOpenId`、`senderUserId`）。
这样调用者身份来自飞书事件本身，而不是模型自己填的参数。

已存在的互补校验：`cli.run_skill` 的业务域白名单在 `SkillCliExecutorService.ensureDomainAllowed` 中强校验，
以及 `ensureCommandDomainAllowed` 对单条命令的业务域校验。本次新增的是“调用者/会话”这一层。

## 4. 业务线程池

问题：`AdminAgentService.handleMessageAsync` 使用 `@Async` 默认执行器，缺少明确的线程上限与拒绝策略。

实现：`FeishuAsyncConfig.feishuAgentExecutor`

```text
核心线程 4 / 最大线程 8 / 队列 100 / 线程名前缀 feishu-agent- / AbortPolicy
```

- 明确使用 `@Async("feishuAgentExecutor")`，不再依赖默认执行器；
- 拒绝策略用 `AbortPolicy`，不用 `CallerRunsPolicy`，避免慢任务阻塞飞书回调线程；
- 队列满时 `FeishuEventController` 捕获 `TaskRejectedException`，回复用户“当前任务较多，请稍后再发一次”，
  保证用户不会只看到“已收到”却永远等不到结果。

## 5. 为什么这轮不改 if-else 分发

`execute()` 里的 if-else 分发确实会在工具变多后变得臃肿，正确方向是
`Map<String, ToolHandler>` 或 `ToolDefinition` 注册表。

本轮先不动它，原因是：注册表重构会同时改动工具说明、参数校验和执行分发三处，
属于行为等价但面较大的改造，需要单独的提交和单独的回归验证。
先补齐“超时、权限、线程池”这三个纯增量护栏，风险更低、收益更直接。

## 6. 这轮新增的配置项

```text
FEISHU_TOOL_TIMEOUT_SECONDS=90
FEISHU_TOOL_EXECUTOR_THREADS=8
FEISHU_TOOL_ALLOWED_OPEN_IDS=
FEISHU_TOOL_ALLOWED_CHAT_IDS=
FEISHU_AGENT_CORE_POOL_SIZE=4
FEISHU_AGENT_MAX_POOL_SIZE=8
FEISHU_AGENT_QUEUE_CAPACITY=100
FEISHU_OPEN_API_CONNECT_TIMEOUT_SECONDS=3
FEISHU_OPEN_API_READ_TIMEOUT_SECONDS=20
FEISHU_ECOMMERCE_MCP_CONNECT_TIMEOUT_SECONDS=3
FEISHU_ECOMMERCE_MCP_READ_TIMEOUT_SECONDS=15
```

## 7. 验收方式

- `ToolRegistryServiceTest`：未知工具拒绝、白名单外调用者拒绝、白名单内放行、工具超时返回失败、工具说明自检对账；
- `ToolPermissionServiceTest`：未配置白名单不限制、用户白名单、会话白名单、身份脱敏；
- 启动日志确认：工具清单自检通过、业务线程池参数、工具调用白名单是否启用。
