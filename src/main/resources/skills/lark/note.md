# Note Skill

分层：第一层「飞书原生能力」，只描述 lark-cli 在该业务域能做什么、调用约定是什么。

能力来源：以 `lark-cli <domain> --help` 的实时输出为准（编写时参考 CLI 版本 1.0.95）。

## 适用场景

- 查询会议纪要详情。
- 获取会议纪要转写内容。

## 执行原则

1. 先查帮助，不要猜命令。
2. 操作前必须确认 note token 或 URL。

## 手册命令索引

```bash
lark-cli note --help
lark-cli note +detail --help
lark-cli note +transcript --help
```
