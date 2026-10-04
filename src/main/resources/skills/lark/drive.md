# Drive Skill

分层：第一层「飞书原生能力」，只描述 lark-cli 在该业务域能做什么、调用约定是什么。

能力来源：以 `lark-cli drive --help` 的实时输出为准（编写时参考 CLI 版本 1.0.95）。

## 适用场景

- 云盘文件上传、下载、复制、移动、删除。
- 文档搜索：按类型、我拥有/我创建、时间窗、关键词。
- 文档权限成员管理。
- 评论、回复、版本历史、导入导出。
- 文件预览、同步。

## 执行原则

1. **本文件已覆盖的常用命令，直接照「必知参数速查」和「示例」写命令，不要再跑 `--help`。** 每次 `--help` 都是一次额外的模型往返，会明显拖慢整体执行；只有做本文件没覆盖的命令时才查帮助。
2. 高风险写操作，例如授权、删除、转移所有者，必须等待用户明确确认。
3. 涉及文件类型时，必须确认 `type` 和 token 匹配。
4. 写操作失败时不要说成功。
5. 「我名下的多维表格 / 云文档」的列出、搜索、删除，是**云盘文件级**操作，走 `drive` 域；`base` 域管的是某一个多维表格内部的表、字段、记录。

## 必知参数速查（`drive +search`）

| 需求 | 正确写法 | 说明 |
| --- | --- | --- |
| 分页 | `--page-token <token>` | **本命令没有 `--page-all`**，写了直接退出码 2；page token 从上一页响应里取 |
| 每页条数 | `--page-size 1..20` | 上限 20，默认 15；填 50 无效 |
| 按创建时间排序 | `--sort create_time` | 可用值：`default` / `edit_time` / `edit_time_asc` / `open_time` / `create_time`；**没有 `create_time_asc`** |
| 我拥有的 | `--mine` | 服务端 owner 语义 |
| 我创建的 | `--created-by-me` | 原始创建者语义，与 `--mine` 不同，不要混用 |
| 按类型筛 | `--doc-types bitable` | 可用：`doc,sheet,bitable,mindnote,file,wiki,docx,folder,catalog,slides,shortcut`，逗号分隔 |
| 关键词 | `--query` | 最多 30 个字符（CJK 按 1 个算），超了服务器报 `99992402` |
| 输出 | `--format json` / `--jq` | jq 只用来裁剪字段，不要用它代替 `--sort` 排序 |

## 必知参数速查（`drive +delete`）

| 需求 | 正确写法 |
| --- | --- |
| 指定目标 | `--file-token <token>`（必填） |
| 指定类型 | `--type <file,docx,bitable,doc,sheet,mindnote,folder,shortcut,slides>`（必填） |
| 确认高风险 | `--yes`（删除不可恢复，必须在用户明确确认后才加） |

## 示例

列出我名下的多维表格。**不要用 `--page-all`**；结果多时用上一次响应里的 `--page-token` 翻页：

```bash
lark-cli drive +search --doc-types bitable --mine --as user --page-size 20 --sort create_time --format json
```

只取需要的字段，避免把整页结果灌进上下文：

```bash
lark-cli drive +search --doc-types bitable --mine --as user --page-size 20 --format json --jq '.data.results | map({title: .result_meta.title, token: .result_meta.token, create_time: .result_meta.create_time})'
```

删除一个多维表格（高风险，必须等用户明确确认后再执行）：

```bash
lark-cli drive +delete --file-token <token> --type bitable --yes --as user --format json
```

## 不要做

- 不要对 `drive +search` 加 `--page-all`（本域不支持；`im` 域的部分命令才支持）。
- 不要把 `--page-size` 写到 20 以上。
- 不要用 jq 排序代替 `--sort`。
- 不要把 `--mine` 和 `--created-by-me` 当成同一个意思。
- 不要在用户没明确确认时执行 `+delete`。

## 手册命令索引

只有需要用到下表命令、且本文件没给出参数时，才去查对应 `--help`：

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
