# Docs Skill

作者：sunzeqin

依据：`D:/codex项目/lark-project/飞书CLI命令手册.md`，CLI 版本 `1.0.95`。

## 适用场景

- 创建云文档。
- 读取云文档内容。
- 更新云文档内容。
- 搜索云文档、知识库、电子表格文件。
- 上传、插入、下载文档媒体资源。
- 管理文档历史版本。

## 执行原则

1. 先查帮助，不要猜命令。
2. 优先使用 `+` 快捷命令。
3. 文档正文优先用 `@file` 或 `-` 输入，避免长文本被 shell 转义破坏。
4. 创建或更新文档前，先确认 `--doc-format` 是 `xml` 还是 `markdown`。
5. 写操作失败时不要说成功，必须返回真实错误。
6. 高风险或破坏性操作必须等待用户明确确认。

## 手册命令索引

```bash
lark-cli docs --help
lark-cli docs +create --help
lark-cli docs +fetch --help
lark-cli docs +history-list --help
lark-cli docs +history-revert --help
lark-cli docs +history-revert-status --help
lark-cli docs +media-download --help
lark-cli docs +media-insert --help
lark-cli docs +media-preview --help
lark-cli docs +media-upload --help
lark-cli docs +resource-delete --help
lark-cli docs +resource-download --help
lark-cli docs +resource-update --help
lark-cli docs +script --help
lark-cli docs +search --help
lark-cli docs +update --help
lark-cli docs +whiteboard-update --help
```

## 常用命令选择

- 创建文档：`lark-cli docs +create --help`
- 读取文档：`lark-cli docs +fetch --help`
- 更新文档：`lark-cli docs +update --help`
- 搜索文档：`lark-cli docs +search --help`
- 插入图片或附件：`lark-cli docs +media-insert --help`
- 上传媒体：`lark-cli docs +media-upload --help`
- 下载媒体：`lark-cli docs +media-download --help`
- 历史版本：`lark-cli docs +history-list --help`

## 回复要求

- 成功时返回文档标题、文档 token 或链接。
- 如果已经发送给用户或群，要说明发送目标。
- 失败时返回真实错误、缺少权限或缺少参数。
