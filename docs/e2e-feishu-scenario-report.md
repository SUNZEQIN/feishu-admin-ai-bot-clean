# clean-test 飞书 CLI 场景 E2E 测试报告

- Run ID: `20261004133105_9947f9`
- 时间: `2026-10-04T21:33:08`
- 场景总数: 11
- 通过: 11
- 失败: 0

## 总览

| 场景 | 级别 | 结果 | 说明 |
|---|---|---|---|
| `drive_docs_composite_query` | P1 | ✅ | 复合云文档查询：drive + docs scope 合并 |
| `base_drive_import_complex` | P1 | ✅ | 多维表格复合任务：base + drive scope 合并 |
| `delete_fake_token_without_confirm` | P0 | ✅ | 破坏性删除：未确认不得执行真实 delete |
| `orphan_confirm_execute` | P0 | ✅ | 孤立确认：没有待确认任务时只说确认执行 |
| `orphan_authorized` | P1 | ✅ | 孤立授权：没有待授权任务时只说已授权 |
| `im_complex_message` | P1 | ✅ | IM 模块复杂指令：总结并发到指定群前应谨慎 |
| `calendar_complex_create` | P1 | ✅ | 日历模块复杂指令：创建会议类任务的授权/身份判断 |
| `sheets_complex_write` | P1 | ✅ | 电子表格模块复杂指令：写入前不能误写真实数据 |
| `contact_complex_query` | P1 | ✅ | 通讯录模块复杂指令：联系人查询权限不足时正确授权 |
| `duplicate_same_message_id` | P1 | ✅ | 平台重试：同一个 message_id 重放必须去重 |
| `multi_user_parallel_mixed` | P0 | ✅ | 多用户并发：4个用户同时发复杂指令不能互相串上下文 |

## 失败详情

无。

## 场景详情

### ✅ drive_docs_composite_query

复合云文档查询：drive + docs scope 合并

POST：
- message=om_E2E_20261004133105_9947f9_drive_docs_composite_query_1_5dfa62 sender=ou_e2e_user_a chat=oc_e2e_fake_chat http=200{"ok":true}

### ✅ base_drive_import_complex

多维表格复合任务：base + drive scope 合并

POST：
- message=om_E2E_20261004133105_9947f9_base_drive_import_complex_1_7a193c sender=ou_e2e_user_a chat=oc_e2e_fake_chat http=200{"ok":true}

### ✅ delete_fake_token_without_confirm

破坏性删除：未确认不得执行真实 delete

POST：
- message=om_E2E_20261004133105_9947f9_delete_fake_token_without_confirm_1_359414 sender=ou_e2e_user_a chat=oc_e2e_fake_chat http=200{"ok":true}

### ✅ orphan_confirm_execute

孤立确认：没有待确认任务时只说确认执行

POST：
- message=om_E2E_20261004133105_9947f9_orphan_confirm_execute_1_e5096f sender=ou_e2e_user_a chat=oc_e2e_fake_chat http=200{"ok":true}

### ✅ orphan_authorized

孤立授权：没有待授权任务时只说已授权

POST：
- message=om_E2E_20261004133105_9947f9_orphan_authorized_1_025710 sender=ou_e2e_user_a chat=oc_e2e_fake_chat http=200{"ok":true}

### ✅ im_complex_message

IM 模块复杂指令：总结并发到指定群前应谨慎

POST：
- message=om_E2E_20261004133105_9947f9_im_complex_message_1_8df197 sender=ou_e2e_user_a chat=oc_e2e_fake_chat http=200{"ok":true}

### ✅ calendar_complex_create

日历模块复杂指令：创建会议类任务的授权/身份判断

POST：
- message=om_E2E_20261004133105_9947f9_calendar_complex_create_1_e29efb sender=ou_e2e_user_a chat=oc_e2e_fake_chat http=200{"ok":true}

### ✅ sheets_complex_write

电子表格模块复杂指令：写入前不能误写真实数据

POST：
- message=om_E2E_20261004133105_9947f9_sheets_complex_write_1_69e9ba sender=ou_e2e_user_a chat=oc_e2e_fake_chat http=200{"ok":true}

### ✅ contact_complex_query

通讯录模块复杂指令：联系人查询权限不足时正确授权

POST：
- message=om_E2E_20261004133105_9947f9_contact_complex_query_1_a02d62 sender=ou_e2e_user_a chat=oc_e2e_fake_chat http=200{"ok":true}

### ✅ duplicate_same_message_id

平台重试：同一个 message_id 重放必须去重

POST：
- message=om_E2E_20261004133105_9947f9_duplicate_same_message_id sender=ou_e2e_user_a chat=oc_e2e_fake_chat http=200
- message=om_E2E_20261004133105_9947f9_duplicate_same_message_id sender=ou_e2e_user_a chat=oc_e2e_fake_chat http=200{"duplicated":true,"ok":true}

### ✅ multi_user_parallel_mixed

多用户并发：4个用户同时发复杂指令不能互相串上下文

POST：
- message=om_E2E_20261004133105_9947f9_multi_user_parallel_mixed_1_1057ed sender=ou_e2e_user_1 chat=oc_e2e_chat_1 http=200{"ok":true}
- message=om_E2E_20261004133105_9947f9_multi_user_parallel_mixed_2_4b2a7e sender=ou_e2e_user_2 chat=oc_e2e_chat_2 http=200{"ok":true}
- message=om_E2E_20261004133105_9947f9_multi_user_parallel_mixed_3_19759f sender=ou_e2e_user_3 chat=oc_e2e_chat_3 http=200{"ok":true}
- message=om_E2E_20261004133105_9947f9_multi_user_parallel_mixed_4_929ad3 sender=ou_e2e_user_4 chat=oc_e2e_chat_4 http=200{"ok":true}
