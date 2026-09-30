# 电商 MCP 工具调用 Skill

## 作用

当用户在飞书里提出电商业务相关需求时，优先调用电商 MCP 工具拿真实结构化数据，再由 LLM 整理成中文结论。

不要让 LLM 自己编造电商数据。
不要让 LLM 直接写 SQL。
不要把电商分析需求交给 `cli.run_skill`，除非需求涉及飞书文档、飞书卡片、飞书消息发送等飞书侧动作。

## 可用工具

### ecommerce.list_tools

作用：查询电商 MCP 服务当前可用工具。

参数：无。

使用场景：

- 不确定有哪些电商能力；
- 需要先确认工具名称；
- MCP 服务刚接入时需要探测能力。

### ecommerce.call_tool

作用：调用电商 MCP 服务里的具体工具。

参数：

```json
{
  "toolName": "ecommerce.query_top_products",
  "arguments": {}
}
```

## 电商意图识别规则

用户提到下面关键词时，优先认为是电商业务需求：

- 订单；
- 商品；
- 库存；
- 补货；
- 退款；
- 售后；
- 客户；
- 消费；
- 销售额；
- GMV；
- 活动复盘；
- 电商数据；
- 商品排行；
- 低库存；
- 异常订单。

## 工具选择规则

1. 用户要查销售额最高、销量最高、TOP 商品时，调用：

```text
ecommerce.call_tool
toolName=ecommerce.query_top_products
arguments.limit=用户要求条数，默认 5
```

2. 用户要查低库存、库存预警、补货建议时，调用：

```text
ecommerce.call_tool
toolName=ecommerce.query_low_inventory
arguments.threshold=库存阈值，默认 50
```

3. 用户要查某个客户订单、消费偏好、客户画像时，调用：

```text
ecommerce.call_tool
toolName=ecommerce.query_customer_orders
arguments.customerName=客户名称
arguments.months=最近几个月，默认 12
```

4. 用户要查退款最多、售后率、退款原因时，调用：

```text
ecommerce.call_tool
toolName=ecommerce.query_refund_top_products
arguments.limit=用户要求条数，默认 5
```

5. 用户要做活动复盘、经营日报、整体分析时，调用：

```text
ecommerce.call_tool
toolName=ecommerce.query_activity_report
arguments.activityName=活动名称，缺省时用“日常经营分析”
```

## 多步协作规则

如果用户需求同时包含电商分析和飞书动作，按下面顺序执行：

```text
先调用 ecommerce.call_tool 获取电商数据
  ↓
LLM 根据结构化数据形成分析结论
  ↓
如果用户要求生成文档、发消息、建群、开会，再调用飞书工具或 cli.run_skill
```

示例：

```text
用户：查一下最近退款最多的商品，整理成文档发给测试账号。
步骤1：调用 ecommerce.query_refund_top_products。
步骤2：根据返回数据生成分析结论。
步骤3：调用 cli.run_skill，domain=docs 或 im，完成飞书文档和发送动作。
```

## 回复要求

最终回复必须基于工具返回的数据。

推荐格式：

```text
✅ 已完成电商数据分析

📊 核心结果
- xxx
- xxx

🔎 业务判断
- xxx

💡 建议动作
- xxx
```
