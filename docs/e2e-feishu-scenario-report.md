# clean-test 飞书 CLI 场景 E2E 测试报告

- Run ID: `20261004133013_d66252`
- 时间: `2026-10-04T21:30:59`
- 场景总数: 2
- 通过: 2
- 失败: 0

## 总览

| 场景 | 级别 | 结果 | 说明 |
|---|---|---|---|
| `delete_fake_token_without_confirm` | P0 | ✅ | 破坏性删除：未确认不得执行真实 delete |
| `duplicate_same_message_id` | P1 | ✅ | 平台重试：同一个 message_id 重放必须去重 |

## 失败详情

无。

## 场景详情

### ✅ delete_fake_token_without_confirm

破坏性删除：未确认不得执行真实 delete

POST：
- message=om_E2E_20261004133013_d66252_delete_fake_token_without_confirm_1_df3e16 sender=ou_e2e_user_a chat=oc_e2e_fake_chat http=200{"ok":true}

### ✅ duplicate_same_message_id

平台重试：同一个 message_id 重放必须去重

POST：
- message=om_E2E_20261004133013_d66252_duplicate_same_message_id sender=ou_e2e_user_a chat=oc_e2e_fake_chat http=200{"ok":true}
- message=om_E2E_20261004133013_d66252_duplicate_same_message_id sender=ou_e2e_user_a chat=oc_e2e_fake_chat http=200{"duplicated":true,"ok":true}
