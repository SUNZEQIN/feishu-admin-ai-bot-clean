# Markdown Skill

分层：第一层「飞书原生能力」，只描述 lark-cli 在该业务域能做什么、调用约定是什么。

能力来源：以 `lark-cli <domain> --help` 的实时输出为准（编写时参考 CLI 版本 1.0.95）。

## 适用场景

- 用 Markdown 创建文档。
- 拉取文档为 Markdown。
- 覆盖、增量修改或比较 Markdown 文档。

## 执行原则

1. 先查帮助，不要猜命令。
2. 长 Markdown 内容优先用 `@file` 或 `-` 输入。
3. 覆盖写入前必须确认目标文档。

## 手册命令索引

```bash
lark-cli markdown --help
lark-cli markdown +create --help
lark-cli markdown +fetch --help
lark-cli markdown +overwrite --help
lark-cli markdown +patch --help
lark-cli markdown +diff --help
```
