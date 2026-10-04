# Base Skill

分层：第一层「飞书原生能力」，只描述 lark-cli 在该业务域能做什么、调用约定是什么。

能力来源：以 `lark-cli <domain> --help` 的实时输出为准（编写时参考 CLI 版本 1.0.95）。

## 适用场景

- 创建多维表格。
- 创建、查询、修改数据表。
- 创建、查询、修改字段。
- 新增、更新、查询、删除记录。
- 查询、创建、修改视图。
- 查询多维表格数据并做汇总分析。
- 管理仪表盘、表单、权限角色、工作流、空间等高级能力。

## 执行原则

1. 先查帮助，不要猜命令。
2. 优先使用 `+` 快捷命令。
3. 创建多维表格后，必须保存 `base_token` 或链接。
4. 写入记录前，必须确认 `base_token`、`table_id`、字段名称或字段 ID。
5. 查询记录时优先分页取完，不能只看第一页就下结论。
6. 批量写入优先使用批量命令。
7. 高风险写操作必须等待用户明确确认。
8. 「列出/搜索/删除我名下的多维表格」是云盘文件级操作，走 `drive` 域；本域只操作某个多维表格内部的表、字段、记录。

## 手册命令索引

```bash
lark-cli base --help
lark-cli base +base-create --help
lark-cli base +base-get --help
lark-cli base +base-copy --help
lark-cli base +app-create --help
lark-cli base +app-get --help
lark-cli base +table-create --help
lark-cli base +table-list --help
lark-cli base +table-get --help
lark-cli base +table-update --help
lark-cli base +table-delete --help
lark-cli base +table-copy --help
lark-cli base +table-copy-status --help
lark-cli base +field-create --help
lark-cli base +field-list --help
lark-cli base +field-get --help
lark-cli base +field-update --help
lark-cli base +field-delete --help
lark-cli base +field-search-options --help
lark-cli base +record-batch-create --help
lark-cli base +record-batch-update --help
lark-cli base +record-list --help
lark-cli base +record-search --help
lark-cli base +record-get --help
lark-cli base +record-delete --help
lark-cli base +record-upsert --help
lark-cli base +record-history-list --help
lark-cli base +record-share-link-create --help
lark-cli base +record-upload-attachment --help
lark-cli base +record-download-attachment --help
lark-cli base +record-remove-attachment --help
lark-cli base +data-query --help
lark-cli base +view-create --help
lark-cli base +view-list --help
lark-cli base +view-get --help
lark-cli base +view-rename --help
lark-cli base +view-delete --help
lark-cli base +view-set-filter --help
lark-cli base +view-get-filter --help
lark-cli base +view-set-sort --help
lark-cli base +view-get-sort --help
lark-cli base +view-set-group --help
lark-cli base +view-get-group --help
lark-cli base +view-set-visible-fields --help
lark-cli base +view-get-visible-fields --help
lark-cli base +form-create --help
lark-cli base +form-list --help
lark-cli base +form-get --help
lark-cli base +form-update --help
lark-cli base +form-delete --help
lark-cli base +dashboard-create --help
lark-cli base +dashboard-list --help
lark-cli base +dashboard-get --help
lark-cli base +dashboard-update --help
lark-cli base +dashboard-delete --help
lark-cli base +role-create --help
lark-cli base +role-list --help
lark-cli base +role-get --help
lark-cli base +role-update --help
lark-cli base +role-delete --help
lark-cli base +workflow-create --help
lark-cli base +workflow-list --help
lark-cli base +workflow-get --help
lark-cli base +workflow-update --help
lark-cli base +workflow-enable --help
lark-cli base +workflow-disable --help
lark-cli base +workspace-create --help
lark-cli base +workspace-entity-list --help
lark-cli base +workspace-move-in --help
lark-cli base +url-resolve --help
lark-cli base +title-resolve --help
lark-cli base +template-list --help
lark-cli base +template-search --help
```

## 常用命令选择

- 创建多维表格：`lark-cli base +base-create --help`
- 读取多维表格：`lark-cli base +base-get --help`
- 创建数据表：`lark-cli base +table-create --help`
- 查询数据表：`lark-cli base +table-list --help`
- 创建字段：`lark-cli base +field-create --help`
- 查询字段：`lark-cli base +field-list --help`
- 批量新增记录：`lark-cli base +record-batch-create --help`
- 批量更新记录：`lark-cli base +record-batch-update --help`
- 查询记录：`lark-cli base +record-list --help`
- 搜索记录：`lark-cli base +record-search --help`
- 新增或更新记录：`lark-cli base +record-upsert --help`
- 查询数据：`lark-cli base +data-query --help`

## 回复要求

- 创建成功时返回多维表格名称、链接、表名、字段和记录数量。
- 查询成功时说明查询条件、命中数量、是否分页取完。
- 失败时返回真实错误和下一步建议。
