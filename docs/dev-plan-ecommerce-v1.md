# 电商开放 V1 · 开发计划（切片式）

- 角色：后端开发 AI（绑定 skill：`tdd` + `backend-developer/`）
- 需求来源：`docs/prd-ecommerce-v1.md`（产品经理，已评审）
- 验收来源：`docs/test-cases-ecommerce-v1.md`（测试 / QA）
- 规则总账：`docs/product-rules.md`（80 条项目级规则）
- 差距清单：`docs/dev-backlog-product-rules.md`（32 条待开发/修改，含 P0 5 条）
- 判断原则：一片一验收。每片都要有真实测试输出，不允许“看起来做完了”。

## 0. 项目基线

| 项 | 值 |
|---|---|
| 新仓库 | `/root/projects/feishu-ecommerce-open-v1`（从 `~/ai-life-os/inbox/feishu-admin` 克隆，保留历史） |
| 技术栈 | Spring Boot 3.3.5 / Java 17 / MySQL 8 / JdbcTemplate / langchain4j |
| 构建命令 | `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn -o test` |
| 基线测试 | 30 个，全绿（改造前） |
| 当前测试 | 52 个，全绿（切片 1 完成后） |
| 部署目标 | 腾讯云测试环境 `feishu-admin-ai-bot-clean-test`(8091) + 电商 MCP(8090) |

> 本机坑：阿里云机器的 `java-21` 是 JRE-only（没有 javac），不加 `JAVA_HOME` 会报
> `release version 17 not supported`。必须显式指定 JDK 17。

## 1. 切片 1 ✅ 角色分级 + 电商权限矩阵

新增：

- `pojo/role/BotRole.java`：L1/L2/L3，默认 L1，识别失败回落 L1
- `pojo/tool/ToolErrorCode.java`：8 个错误码 + PRD 4.5 定稿话术
- `service/role/`：`BotRoleRepository` / `JdbcBotRoleRepository` / `BotRoleResolver`
- `service/tool/RoleToolPolicy.java`：PRD 4.1 权限矩阵，唯一判定入口
- `service/tool/RoleToolPermissionService.java`：网关式电商调用深入 `toolName` 判定
- `db/schema-mysql.sql`：新增 `bot_user_role` 表
- `ToolRegistryService`：执行链新增「角色分级校验」，拒绝发生在调用电商服务之前

关键设计：电商工具是网关式调用（`ecommerce.call_tool` + 参数 `toolName`），
只校验顶层工具名会让 L1 绕过分级拿到客户明细，所以判定必须深入参数。

验收对照（已有单测）：EC-11、EC-12、EC-14、EC-15、EC-16 的判定逻辑。
证据：`Tests run: 52, Failures: 0, Errors: 0`。

## 2. 切片 2 ✅ 审计落库（覆盖规则 O-01/O-02/O-05、P-05）

新增：

- `agent_task` + `agent_tool_call_log` 两张表（PRD 4.3）
- `service/audit/`：`TaskAuditRepository` / `JdbcTaskAuditRepository` / `TaskAuditService`
- `ToolResult` 增加 `errorCode`；`ToolErrorCode` 增加 `INTERNAL_ERROR`
- 编排器落任务状态：SUCCESS / FAILED / PARTIAL / WAITING_CONFIRM（高风险闸门拦下时不再记成功）
- 每次工具调用一行明细，入参/结果只存 SHA-256 摘要，不存原文

验收对照：EC-41、EC-42、EC-43。
证据：单测 8 条（假仓库）+ **真库验证**（腾讯云测试环境 MySQL `feishu_admin_bot`）：
建表成功、越权任务落 `FAILED / PERMISSION_DENIED / intent_domain=ecommerce`、
工具明细落 `success=0 / PERMISSION_DENIED`、验证数据已清理（leftover=0）。

## 3. 切片 3 🟡 脱敏与数据可信（脱敏与来源标注已完成）

已完成：

- `ResultMaskingService`：L1 去成本价/客户字段，L2 去手机号/地址，密钥类字段全角色隐藏；
  字符串里的手机号兜底打码；不修改原始数据
- `DataSourceNoticeService`：用到电商数据的回复统一追加「数据来源：测试数据」（幂等）
- 验收对照：EC-13 已有单测

仍待做（P1）：

- 空值与异常区分：空结果走 `EMPTY_RESULT`、电商不可用走 `MCP_UNAVAILABLE`，不得当 0 处理
- 失败话术落代码：`ToolErrorCode` 已有 9 条定稿话术，但非权限路径仍由模型自由组织
- 验收对照：EC-21、EC-22、EC-23、EC-24、EC-25

## 4. 切片 4 ⏳ 协作闭环

- 查数据 → 生成飞书文档 → 发给指定人
- 验收对照：EC-06

## 5. 切片 5 ⏳ 并发与幂等复核

- 复用已有 `MessageDedupService`，补 EC-31 ~ EC-35 的自动化用例
- 20 并发压测（在测试环境跑）

## 6. 切片 6 ⏳ 试点上线

- `FEISHU_ECOMMERCE_MCP_ENABLED=true` + 试点群 / 试点人白名单
- 一周观察指标与回滚开关（PRD 第 8 节）

## 7. 需要你拍板

1. 新仓库要不要推 GitHub？用什么名字？（现在只在本机）
2. 审计层的验证方式：本机没有 docker / mysql →
   A. 单测用 H2 内存库（快，离真实 MySQL 有差距）
   B. 直接部署到腾讯云测试环境，连真库验证（慢，但接近真实）
3. 角色名单运维：V1 按 PRD 用只读 SQL 维护，要不要我同时写一份《角色名单维护手册》？
