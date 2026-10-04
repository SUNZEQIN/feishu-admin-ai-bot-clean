# Drive Skill

分层：第一层「飞书原生能力」，只描述 lark-cli 在该业务域能做什么、调用约定是什么。

能力来源：以 `lark-cli <domain> --help` 的实时输出为准（编写时参考 CLI 版本 1.0.95）。

## 适用场景

- 云盘文件上传、下载、复制、移动、删除。
- 文档权限成员管理。
- 评论、回复、版本历史、导入导出。
- 文件搜索、预览、同步。

## 执行原则

1. 先查帮助，不要猜命令。
2. 高风险写操作，例如授权、删除、转移所有者，必须等待用户明确确认。
3. 涉及文件类型时，必须确认 `type` 和 token 匹配。
4. 写操作失败时不要说成功。

## 手册命令索引

```bash
lark-cli drive --help
lark-cli drive +upload --help
lark-cli drive +download --help
lark-cli drive +search --help
lark-cli drive +copy --help
lark-cli drive +move --help
lark-cli drive +delete --help
lark-cli drive +import --help
lark-cli drive +export --help
lark-cli drive +export-download --help
lark-cli drive +member-add --help
lark-cli drive +member-list --help
lark-cli drive +member-remove --help
lark-cli drive +permission-get-setting --help
lark-cli drive +update-title --help
lark-cli drive +list-comments --help
lark-cli drive +add-comment --help
lark-cli drive +add-reply --help
lark-cli drive +version-history --help
lark-cli drive +version-get --help
lark-cli drive +version-revert --help
lark-cli drive permission.members auth --help
lark-cli drive permission.members create --help
lark-cli drive permission.members transfer_owner --help
lark-cli drive permission.public get --help
lark-cli drive permission.public patch --help
```
