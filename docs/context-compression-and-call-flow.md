# clean 项目上下文压缩和代码调用顺序

本文档记录 `feishu-admin-ai-bot-clean` 项目里会话记忆优化后的实现方式，以及用户在飞书里发消息后，代码内部的主要执行顺序。

## 1. 优化目标

原来的记忆方式只保存最近 N 条消息，旧消息超过数量后会被直接删除。

优化后改成：

```text
旧消息超过阈值
  ↓
把旧消息压缩进摘要表
  ↓
保留最近几条真实对话
  ↓
下次调用大模型时读取：历史摘要 + 近期对话
```

这样做的好处：

- 长期对话不会无限膨胀；
- 旧信息不会直接丢失；
- 大模型每次读取的上下文更短；
- 群聊和私聊都能保持连续上下文；
- 后续迁移到向量记忆或 LLM 摘要时有明确扩展点。

## 2. 核心配置

配置位置：

```text
src/main/resources/application.yml
.env.example
```

配置项：

```ini
FEISHU_MEMORY_ENABLED=true
FEISHU_MEMORY_MAX_MESSAGES=20
FEISHU_MEMORY_COMPRESS_THRESHOLD=20
FEISHU_MEMORY_RECENT_MESSAGES=8
FEISHU_MEMORY_COMPRESS_BATCH_SIZE=12
FEISHU_MEMORY_SUMMARY_MAX_CHARS=4000
```

字段说明：

| 配置项 | 说明 |
| --- | --- |
| `FEISHU_MEMORY_ENABLED` | 是否开启会话记忆 |
| `FEISHU_MEMORY_MAX_MESSAGES` | 兼容旧配置，当前主要由压缩配置接管 |
| `FEISHU_MEMORY_COMPRESS_THRESHOLD` | 当前会话消息超过多少条后触发压缩 |
| `FEISHU_MEMORY_RECENT_MESSAGES` | 压缩后继续保留多少条最近真实消息 |
| `FEISHU_MEMORY_COMPRESS_BATCH_SIZE` | 单次最多压缩多少条旧消息 |
| `FEISHU_MEMORY_SUMMARY_MAX_CHARS` | 压缩摘要最大字符数，超过后保留最近内容 |

## 3. 数据库表

原始消息表：

```sql
agent_conversation_memory
```

作用：

- 保存真实用户消息；
- 保存真实机器人回复；
- 群聊和私聊都只保存一份当前会话记忆；
- 每轮对话正常情况下保存 2 条：用户一条，机器人一条。

压缩摘要表：

```sql
agent_conversation_summary
```

作用：

- 保存旧消息压缩后的摘要；
- 通过 `memory_scope + memory_key` 唯一定位一个会话；
- 记录累计压缩消息条数；
- 避免旧消息直接删除后完全丢失上下文。

## 4. 代码调用顺序

### 4.1 飞书事件入口

```text
FeishuEventController
  ↓
FeishuEventParserService
  ↓
AdminAgentService.handleMessageAsync
```

说明：

- Controller 只负责接收飞书事件；
- Parser 负责把飞书原始 JSON 转成 `FeishuMessageEvent`；
- `AdminAgentService` 负责异步处理消息。

### 4.2 主流程编排

```text
AdminAgentService
  ↓
发送处理中表情或处理中提示
  ↓
ConversationMemoryService.saveUserMessage
  ↓
AgentOrchestratorService.run
  ↓
ConversationMemoryService.saveAssistantMessage
  ↓
FeishuOpenApiService 回复飞书
```

说明：

- 用户消息先保存；
- Agent 编排器再规划和调用工具；
- 机器人最终回复再保存；
- 处理中表情和临时提示不进入记忆。

### 4.3 Agent 调用上下文

```text
AgentOrchestratorService
  ↓
ConversationMemoryService.readMemoryText
  ↓
读取 agent_conversation_summary
  ↓
读取 agent_conversation_memory 最近 N 条
  ↓
拼成「历史摘要 + 近期对话」
  ↓
交给 AgentPlannerService / LLM
```

最终给模型的记忆格式：

```text
【会话记忆】
历史摘要：
...
近期对话：
user：...
assistant：...
```

## 5. 压缩逻辑

代码位置：

```text
src/main/java/com/sunzeqin/feishuadmin/service/ConversationMemoryService.java
```

核心流程：

```text
saveUserMessage / saveAssistantMessage
  ↓
save
  ↓
compressOldMessages
  ↓
查询当前会话消息总数
  ↓
未超过阈值：结束
  ↓
超过阈值：读取最旧的一批消息
  ↓
读取旧摘要
  ↓
mergeSummary 合并摘要
  ↓
upsertSummary 保存摘要
  ↓
deleteCompressedRows 删除已压缩旧消息
```

当前压缩方式是确定性文本压缩，不额外调用大模型。

原因：

- 不增加 LLM 成本；
- 不受余额不足影响；
- 压缩结果稳定；
- 适合当前演示和排查。

后续可升级为：

```text
确定性摘要
  ↓
LLM 摘要
  ↓
向量召回 + 摘要
```

## 6. 日志观察点

重点看这些日志：

```text
会话记忆读取
会话记忆写入
会话记忆压缩
```

日志含义：

| 日志 | 说明 |
| --- | --- |
| 会话记忆读取 | 当前请求读取了多少摘要和近期消息 |
| 会话记忆写入 | 用户或机器人真实消息已保存 |
| 会话记忆压缩 | 旧消息进入摘要表，原始旧消息被清理 |

## 7. 部署注意

上线前确认 MySQL 已执行最新表结构：

```text
src/main/resources/db/schema-mysql.sql
```

如果表没有自动生成，可以手动执行其中的 `agent_conversation_summary` 建表语句。

提交代码时建议只提交源码和配置，不提交 `target`：

```powershell
git add .env.example src/main/java src/main/resources docs
git commit -m "新增会话上下文压缩文档"
```
