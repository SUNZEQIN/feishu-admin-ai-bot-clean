# Docs Skill

作者：sunzeqin

## 适用场景

- 创建云文档。
- 写入或更新云文档内容。
- 读取云文档内容。
- 把已经整理好的内容写成云文档。

## 执行原则

1. 先查帮助，不要猜命令。
2. 优先读取官方 Docs skill：`lark-cli skills read lark-docs`。
3. 只处理云文档相关能力，不在这里编排“读群消息、总结、发送给用户”等业务流程。
4. 创建文档后必须拿到文档 token 或 URL。
5. 写入文档前确认写入命令的内容参数格式。
6. 如果文档写入失败，不要说创建完成。
7. 读取文档列表或文档内容时优先使用分页参数，如果命令需要 `--page-size`，不要超过 50。

## 常见查询

优先尝试：

```bash
lark-cli skills read lark-docs
lark-cli docs --help
lark-cli docs +fetch --help
lark-cli docs +create --help
lark-cli docs +update --help
```

常见能力优先按这些 shortcut 探测：

- 读取文档：`+fetch`
- 创建文档：`+create`
- 更新文档：`+update`

如果上述命令不存在或参数不匹配，以当前环境 `lark-cli docs --help` 和具体命令 `--help` 输出为准。

## 回复要求

- 成功时返回文档标题和链接。
- 如果已经发回群里，要说明已发送。
- 失败时返回真实错误和缺少的权限或参数。
