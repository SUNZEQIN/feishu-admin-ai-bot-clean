# Approval Skill

作者：sunzeqin

依据：`D:/codex项目/lark-project/飞书CLI命令手册.md`，CLI 版本 `1.0.95`。

## 适用场景

- 查询审批定义。
- 查询审批实例。
- 汇总审批状态。
- 根据审批数据做统计。

## 执行原则

1. 先查帮助，不要猜命令。
2. 审批通常涉及权限，失败时要说明缺少哪个权限或对象不可见。
3. 查询审批列表时注意分页。
4. 高风险写操作必须谨慎，如果 Skill 或 help 没说明清楚，不要执行。

## 常见查询

优先尝试：

```bash
lark-cli approval --help
lark-cli approval approvals --help
lark-cli approval instances --help
lark-cli approval tasks --help
lark-cli approval approvals get --help
lark-cli approval approvals search --help
lark-cli approval instances get --help
lark-cli approval instances initiated --help
lark-cli approval instances create --help
lark-cli approval instances cancel --help
lark-cli approval instances cc --help
lark-cli approval tasks query --help
lark-cli approval tasks approve --help
lark-cli approval tasks reject --help
lark-cli approval tasks add_sign --help
lark-cli approval tasks transfer --help
lark-cli approval tasks rollback --help
lark-cli approval tasks remind --help
```

## 回复要求

- 查询成功时说明查询条件、命中数量、关键状态。
- 失败时说明权限、参数或对象不存在。
