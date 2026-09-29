# Contact Skill

作者：sunzeqin

依据：`D:/codex项目/lark-project/飞书CLI命令手册.md`，CLI 版本 `1.0.95`。

## 适用场景

- 根据姓名、邮箱、手机号查询用户。
- 把自然语言里的人员名称解析成 open_id 或 user_id。
- 给其它业务域提供人员 ID。

## 执行原则

1. 先查帮助，不要猜命令。
2. 如果用户只给姓名，要先查询通讯录或当前群成员。
3. 同名用户必须把候选项返回给外层 Agent，不要随机选择。
4. 不要让用户手动提供 open_id，除非工具确实查不到。

## 常见查询

优先尝试：

```bash
lark-cli contact --help
lark-cli contact +get-user --help
lark-cli contact +search-user --help
lark-cli contact +search-bot --help
lark-cli contact user_profiles batch_query --help
lark-cli im +chat-members-list --help
```

## 回复要求

- 成功时返回姓名、open_id、user_id。
- 多候选时说明候选列表。
- 失败时说明查不到还是权限不足。
