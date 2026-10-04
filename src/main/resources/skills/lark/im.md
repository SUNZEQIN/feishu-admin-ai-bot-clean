# IM Skill

分层：第一层「飞书原生能力」，只描述 lark-cli 在该业务域能做什么、调用约定是什么。

能力来源：以 `lark-cli <domain> --help` 的实时输出为准（编写时参考 CLI 版本 1.0.95）。

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
7. **本域**的群/消息类查询命令支持 `--page-all`（例如 `+chat-members-list`、`+chat-messages-list`）；**不要把 `--page-all` 套到别的业务域**——例如 `drive +search` 没有这个参数，写了会直接退出码 2。其他域是否支持以该域自己的说明/help 为准。
8. `--page-size` 以该命令 help 给的范围为准，不要按习惯套别的域的上限。
9. 向当前群返回结果、卡片、Markdown 或文本时，优先使用 `lark-cli im +messages-reply` 引用 `originalMessageId`，不要直接 `+messages-send`。
10. 群聊回复内容开头要 @ `senderOpenId` 对应的人，避免群里多人同时使用时看不清是谁的结果。
11. 如果 `+messages-reply --help` 显示参数名和下面示例不一致，以当前 CLI help 为准。

## 常见查询

优先尝试：

```bash
lark-cli im --help
lark-cli im +chat-create --help
lark-cli im +chat-list --help
lark-cli im +chat-members-list --help
lark-cli im +chat-messages-list --help
lark-cli im +chat-search --help
lark-cli im +chat-update --help
lark-cli im +messages-send --help
lark-cli im +messages-reply --help
lark-cli im +messages-mget --help
lark-cli im +messages-search --help
lark-cli im +messages-edit --help
lark-cli im +messages-read-status --help
lark-cli im +message-read-users --help
lark-cli im +threads-messages-list --help
lark-cli im chat.members get --help
lark-cli im chat.members create --help
lark-cli im chat.members delete --help
lark-cli im chat.members bots --help
lark-cli im chats create --help
lark-cli im chats get --help
lark-cli im chats update --help
```

查询当前群成员时优先使用：

```bash
lark-cli im +chat-members-list --chat-id <sourceChatId> --as bot --page-all --page-size 50 --format json
```

读取当前群聊天记录时优先使用：

```bash
lark-cli im +chat-messages-list --chat-id <sourceChatId> --as bot --page-all --page-size 20 --format json
```

按关键词搜索消息时使用：

```bash
lark-cli im +messages-search --chat-id <sourceChatId> --query <关键词> --as bot --page-all --page-size 20 --format json
```

发送结果到当前群时优先使用引用回复：

```bash
lark-cli im +messages-reply --message-id <originalMessageId> --text "<at user_id=\"<senderOpenId>\"></at> 处理结果" --as bot --format json
```

发送飞书卡片时，也优先用引用回复。如果 `+messages-reply` 支持 `--content`，就把卡片 JSON 放到 `--content` 或 `@file`，并引用 `originalMessageId`。

## 回复要求

- 成功时说明做了什么、目标群或消息 ID 是什么。
- 失败时说明真实失败原因，不要假装成功。
