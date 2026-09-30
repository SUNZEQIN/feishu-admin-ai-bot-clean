# attendance Skill

## 目标

处理飞书考勤、打卡、出勤结果查询。

参考命令手册：`D:/codex项目/lark-project/飞书CLI命令手册.md` 的「7. 考勤」章节。

## 业务域选择

1. 用户说“考勤”“打卡”“出勤”，业务域必须使用 `attendance`。
2. 不要把考勤误判成 `approval`。审批域只处理审批实例和审批任务，不处理考勤结果。
3. 当前手册里的考勤模块只有读考勤结果能力，没有写考勤、补卡审批、排班编辑能力。

## 命令索引

```bash
lark-cli attendance --help
lark-cli attendance user_tasks --help
lark-cli attendance user_tasks query --help
lark-cli schema attendance.user_tasks.query
```

## 可用命令

### 查询考勤结果

命令：

```bash
lark-cli attendance user_tasks query [flags]
```

用途：查询考勤结果。

风险级别：`write`。虽然是查询命令，但 CLI 手册标记为 write，因此要严格使用真实参数，不要编造结果。

必填参数：

```bash
--employee-type string
```

可选值：

- `employee_id`：员工 ID，也就是飞书管理后台成员详情里的用户 ID。
- `employee_no`：员工工号。

请求体：

```bash
--data string
```

`--data` 是 JSON 请求体，支持直接传 JSON，也支持 `@file`。

可选参数：

```bash
--ignore-invalid-users
--include-terminated-user
--params string
--as user|bot
--dry-run
--format json|ndjson|table|csv
--jq string
```

## 身份规则

1. “我的考勤”“查我最近一个月考勤”“用本人身份查考勤”这类需求，必须使用用户身份 `--as user`。
2. 如果用户没有明确说“用本人身份”，但内容是“我的考勤”，也应把目标理解为当前用户个人数据，走用户身份授权链路。
3. 没有用户 token 或 scope 不足时，不要继续猜命令，系统会返回授权二维码。
4. 如果用户要查其他人的考勤，需要明确员工 ID 或工号；如果只有姓名，先通过通讯录能力查用户，再确认是否能得到考勤接口需要的员工 ID 或工号。

## 查询步骤建议

1. 先查看帮助：

```bash
lark-cli attendance user_tasks query --help
```

2. 如不确定请求体结构，查看 schema：

```bash
lark-cli schema attendance.user_tasks.query
```

3. 根据 schema 组装 `--data`，并使用 JSON 输出：

```bash
lark-cli attendance user_tasks query \
  --employee-type employee_id \
  --data '<按 schema 组装的 JSON>' \
  --as user \
  --format json
```

## 输出要求

最终回复要说明：

- 查询范围；
- 查询身份；
- 考勤结果摘要；
- 异常项，例如缺卡、迟到、早退；
- 如果失败，说明真实失败原因、缺少的参数或权限。
