# Whiteboard Skill

分层：第一层「飞书原生能力」，只描述 lark-cli 在该业务域能做什么、调用约定是什么。

能力来源：以 `lark-cli <domain> --help` 的实时输出为准（编写时参考 CLI 版本 1.0.95）。

## 适用场景

- 导出画板。
- 更新文档中的画板。

## 执行原则

1. 先查帮助，不要猜命令。
2. 更新画板前必须确认目标文档和 whiteboard id。

## 手册命令索引

```bash
lark-cli whiteboard --help
lark-cli whiteboard +export --help
lark-cli whiteboard +update --help
```
