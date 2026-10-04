# Skill 分层说明

本目录是 clean 项目的 Skill 仓库。Skill 按**两层**组织，两层职责不同、读取者不同、修改原因也不同。

```text
src/main/resources/skills/
├── README.md            分层契约（本文件）
├── lark/                第一层：飞书原生能力 Skill
│   ├── im.md  base.md  docs.md  calendar.md  vc.md  minutes.md  note.md
│   ├── contact.md  approval.md  attendance.md  drive.md  wiki.md
│   └── markdown.md  mindnotes.md  whiteboard.md
└── business/            第二层：业务能力 Skill
    ├── _template.md     新增业务域的模板
    └── ecommerce-agent.md
```

---

## 一、为什么要分两层

如果把「飞书 CLI 怎么用」和「业务规则怎么判断」写在同一个文档里，会出现三个问题：

1. **职责混在一起**：改一条业务规则，要动到描述 CLI 命令的文档。
2. **上下文变长**：外层规划器会把大量 CLI 命令细节读进去，关键约束被淹没。
3. **共享变难**：飞书命令细节是所有业务域共用的，业务规则只属于某个业务。

分层后，每一层只回答一个问题：

| 层 | 目录 | 回答的问题 | 谁读取 | 什么时候改 |
| --- | --- | --- | --- | --- |
| 第一层：飞书原生能力 | `skills/lark/` | lark-cli 在这个业务域**能做什么**、怎么调 | `SkillCliExecutorService.readSkill(domain)` | 飞书 CLI 能力变化时 |
| 第二层：业务能力 | `skills/business/` | 业务需求**该走哪条工具链**、怎么判断 | `AgentPlannerService` 构造外层 prompt 时 | 业务规则变化时 |

---

## 二、第一层：飞书原生能力（`skills/lark/`）

**放什么：**

- 该业务域对应的 lark-cli 命令索引；
- 调用约定，例如「先查 help 不要猜命令」「当前群指 `sourceChatId`」「列表优先 `--page-all`」；
- 该域的能力边界，例如「考勤域只读，不能写考勤、补卡、排班」。

**不放什么：**

- 业务规则。例如「电商订单优先走 MCP」属于第二层，不写在这里。
- 具体业务系统的字段、表名、SQL。

**命名规则：** 文件名必须等于业务域名称，且必须出现在 `FEISHU_CLI_ALLOWED_DOMAINS` 配置里。

| 文件名 | 业务域 | 典型场景 |
| --- | --- | --- |
| `im.md` | `im` | 群聊、发消息、查群成员 |
| `base.md` | `base` | 多维表格读写 |
| `docs.md` | `docs` | 云文档 |
| `calendar.md` | `calendar` | 日程 |
| `attendance.md` | `attendance` | 考勤结果查询 |
| `approval.md` | `approval` | 审批实例与任务 |

**读取路径：** `ClassPathResource("skills/lark/" + domain + ".md")`，由 `SkillCliExecutorService` 在进入 CLI 循环前读取，主业务域优先、其余白名单业务域追加，让复合任务可以跨域执行。

---

## 三、第二层：业务能力（`skills/business/`）

**放什么：**

- 业务意图识别规则，例如「用户提到订单、库存、退款、GMV 时算电商需求」；
- 工具链路由规则，例如「电商数据走 `ecommerce.call_tool`，不要走 `cli.run_skill`」；
- 该业务**不要**做什么，例如「不要让 LLM 自己编造电商数据」「不要让 LLM 直接写 SQL」。

**不放什么：**

- lark-cli 命令细节（属于第一层）；
- 任何会过期的临时数据。

**读取路径：** `ClassPathResource("skills/business/<name>.md")`，由 `AgentPlannerService` 在构造外层规划 prompt 时读取，作为业务知识注入。

---

## 四、两层之间的边界规则

一句话：

> **第一层回答「怎么执行」，第二层回答「该不该执行、走哪条路」。**

具体判断方式：

| 你写的内容 | 应该放哪层 |
| --- | --- |
| `lark-cli base +table-list --help` 的用法 | 第一层 |
| 「多维表格用 `base` 域」 | 第一层（域路由） |
| 「用户说本群，指当前 `sourceChatId`」 | 第一层 |
| 「电商退款分析优先用 MCP 拿结构化数据」 | 第二层 |
| 「查低库存要传 `threshold` 参数」 | 第二层 |
| 「审批实例和考勤不要混在一个域」 | 第一层 |

外层 `AgentPlannerService` 只负责**判断意图 + 选粗粒度工具 + 传最小参数**，不负责描述飞书 API 细节。具体飞书命令由 lark-cli 的 `--help` 和第一层 Skill 兜住。

---

## 五、新增一个业务域的步骤

1. 在 `skills/business/` 下复制 `_template.md`，改名为业务名。
2. 只写业务规则和工具链路由，**不要**复制 lark-cli 命令。
3. 如果该业务需要新的飞书域，先在 `skills/lark/` 补齐对应的第一层 Skill，并加入 `FEISHU_CLI_ALLOWED_DOMAINS`。
4. 在 `AgentPlannerService` 里按同样方式读取该业务 Skill，或在需要时改成按业务名动态加载。
5. 用一条真实飞书消息跑一遍，确认模型选对了工具链。

---

## 六、验收标准

分层整理完成的标准是这三条：

1. 只改业务规则时，不需要动 `skills/lark/` 下任何文件。
2. 只改 lark-cli 用法时，不需要动 `skills/business/` 下任何文件。
3. 新同学看这个 README，能说出「一条飞书消息进来后，哪一层负责把它变成哪条命令」。
