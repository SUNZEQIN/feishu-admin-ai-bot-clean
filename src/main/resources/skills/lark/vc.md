# VC Skill

分层：第一层「飞书原生能力」，只描述 lark-cli 在该业务域能做什么、调用约定是什么。

能力来源：以 `lark-cli <domain> --help` 的实时输出为准（编写时参考 CLI 版本 1.0.95）。

## 适用场景

- 创建或查询飞书会议。
- 预定视频会议。
- 配合日程创建带视频会议的安排。

## 执行原则

1. 先查帮助，不要猜命令。
2. 会议模块按手册包含 `vc`、`minutes`、`note`。
3. 如果用户只是要“日程安排”，优先判断是否应该走 calendar。
4. 如果用户明确要求视频会议、会议中操作、会议消息、妙记或会议纪要，再使用本模块能力。
5. 创建或查询成功后返回会议主题、时间、会议链接或妙记链接。

## 常见查询

优先尝试：

```bash
lark-cli vc --help
lark-cli minutes --help
lark-cli note --help
lark-cli vc +detail --help
lark-cli vc +meeting-countdown --help
lark-cli vc +meeting-end --help
lark-cli vc +meeting-events --help
lark-cli vc +meeting-invite --help
lark-cli vc +meeting-join --help
lark-cli vc +meeting-leave --help
lark-cli vc +meeting-list-active --help
lark-cli vc +meeting-message-send --help
lark-cli vc +meeting-screenshot --help
lark-cli vc +recording --help
lark-cli vc +search --help
lark-cli vc meeting get --help
lark-cli minutes +search --help
lark-cli minutes +detail --help
lark-cli minutes +summary --help
lark-cli minutes +download --help
lark-cli minutes +upload --help
lark-cli note +detail --help
lark-cli note +transcript --help
```

## 回复要求

- 成功时返回会议链接和关键时间。
- 失败时说明权限、参数或命令能力问题。
