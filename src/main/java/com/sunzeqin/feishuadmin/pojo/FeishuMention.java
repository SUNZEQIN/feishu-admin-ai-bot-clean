package com.sunzeqin.feishuadmin.pojo;

/**
 * 飞书消息里的 @ 提及对象。
 *
 * <p>作用：保存事件中 mentions 数组里的真实成员信息，避免只看到 @_user_1 占位符。</p>
 *
 * @param key     消息文本中的占位符
 * @param name    展示名称
 * @param openId  open_id
 * @param userId  user_id
 * @param unionId union_id
 *
 * @author sunzeqin
 */
public record FeishuMention(String key, String name, String openId, String userId, String unionId) {
}
