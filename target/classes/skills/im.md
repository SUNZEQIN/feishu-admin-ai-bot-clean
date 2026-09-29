# IM Skill

作者：sunzeqin

## 适用场景

- 查询群成员。
- 创建群聊。
- 拉用户或机器人进群。
- 向群或用户发送消息。
- 读取群消息并做统计或总结。

## 执行原则

1. 先查帮助，不要猜命令。
2. 当前群、这个群、本群都指 `sourceChatId`。
3. 用户成员通常使用 `open_id`。
4. 机器人入群通常需要 `app_id`，也就是 `cli_` 开头的应用 ID。
5. 如果要从当前群复制成员，先查询当前群成员，再按名称筛选。
6. 如果命令失败，要根据 stderr/stdout 调整命令，不要重复执行同一条错误命令。
7. 查询列表类数据时优先使用 `--page-all`，如果命令需要 `--page-size`，不要超过 50。

## 常见查询

优先尝试：

```bash
lark-cli im --help
lark-cli im +chat-members-list --help
```

查询当前群成员时优先使用：

```bash
lark-cli im +chat-members-list --chat-id <sourceChatId> --as bot --page-all --page-size 50 --format json
```

## 回复要求

- 成功时说明做了什么、目标群或消息 ID 是什么。
- 失败时说明真实失败原因，不要假装成功。
