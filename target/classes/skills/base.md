# Base Skill

作者：sunzeqin

## 适用场景

- 创建多维表格。
- 创建数据表、字段、视图。
- 批量写入测试数据。
- 查询、过滤、统计多维表格记录。
- 给业务数据做汇总分析。

## 执行原则

1. 先查帮助，不要猜命令。
2. 优先读取官方 Base skill：`lark-cli skills read lark-base`。
3. 优先使用 `+` 开头的 shortcut；shortcut 不覆盖时，再按 `lark-cli base --help` 里的真实资源命令执行。
4. 创建多维表格前，先确认创建入口和参数。
5. 写入数据前，先确认表、字段创建成功并拿到 `base_token`、`table_id`、字段名称或字段 ID。
6. 查询数据时注意分页，不要只看第一页就下结论。
7. 写操作失败时，根据 CLI 返回的参数校验错误调整命令，不要重复执行同一条错误命令。

## 常见查询

优先尝试：

```bash
lark-cli skills read lark-base
lark-cli base --help
lark-cli base +base-create --help
lark-cli base +table-list --help
lark-cli base +field-list --help
lark-cli base +record-list --help
lark-cli base +record-upsert --help
```

常见能力优先按这些 shortcut 探测：

- 创建多维表格：`+base-create`
- 读取多维表格信息：`+base-get`
- 查询数据表：`+table-list`
- 查询字段：`+field-list`
- 查询记录：`+record-list`
- 新增或更新记录：`+record-upsert`

如果上述命令不存在或参数不匹配，以当前环境 `lark-cli base --help` 和具体命令 `--help` 输出为准。

## 回复要求

- 创建成功时返回多维表格名称、链接、表名、字段、记录数量。
- 查询成功时说明查询条件、命中数量、是否分页取完。
- 失败时返回真实错误和下一步建议。
