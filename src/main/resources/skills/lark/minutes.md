# Minutes Skill

分层：第一层「飞书原生能力」，只描述 lark-cli 在该业务域能做什么、调用约定是什么。

能力来源：以 `lark-cli <domain> --help` 的实时输出为准（编写时参考 CLI 版本 1.0.95）。

## 适用场景

- 查询妙记。
- 下载或上传妙记。
- 获取纪要摘要、待办、详情。
- 替换发言人或关键词。

## 执行原则

1. 先查帮助，不要猜命令。
2. 下载、上传、更新前必须确认妙记 token 或 URL。
3. 写操作失败时不要说成功。

## 手册命令索引

```bash
lark-cli minutes --help
lark-cli minutes +search --help
lark-cli minutes +detail --help
lark-cli minutes +download --help
lark-cli minutes +upload --help
lark-cli minutes +summary --help
lark-cli minutes +todo --help
lark-cli minutes +update --help
lark-cli minutes +apply-permission --help
lark-cli minutes +speaker-replace --help
lark-cli minutes +word-replace --help
lark-cli minutes minutes get --help
```
