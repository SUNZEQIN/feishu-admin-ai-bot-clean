# Wiki Skill

分层：第一层「飞书原生能力」，只描述 lark-cli 在该业务域能做什么、调用约定是什么。

能力来源：以 `lark-cli <domain> --help` 的实时输出为准（编写时参考 CLI 版本 1.0.95）。

## 适用场景

- 查询、创建、复制、移动、删除知识库节点。
- 查询、创建知识库空间。
- 管理知识库成员。

## 执行原则

1. 先查帮助，不要猜命令。
2. 删除、移动、成员变更等写操作必须谨慎。
3. 操作节点前必须确认 node token 或 space id。

## 手册命令索引

```bash
lark-cli wiki --help
lark-cli wiki +space-list --help
lark-cli wiki +space-create --help
lark-cli wiki +node-list --help
lark-cli wiki +node-get --help
lark-cli wiki +node-create --help
lark-cli wiki +node-copy --help
lark-cli wiki +node-delete --help
lark-cli wiki +move --help
lark-cli wiki +move-to-drive --help
lark-cli wiki +member-list --help
lark-cli wiki +member-add --help
lark-cli wiki +member-remove --help
```
