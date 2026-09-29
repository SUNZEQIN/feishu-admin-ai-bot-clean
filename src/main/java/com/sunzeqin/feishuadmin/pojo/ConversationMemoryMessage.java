package com.sunzeqin.feishuadmin.pojo;

import java.time.Instant;

/**
 * 会话记忆消息。
 *
 * <p>作用：保存某个飞书用户在某个会话里的历史输入和机器人回复。</p>
 *
 * @param role      消息角色，user 表示用户，assistant 表示机器人
 * @param content   消息内容
 * @param createdAt 写入时间
 *
 * @author sunzeqin
 */
public record ConversationMemoryMessage(String role, String content, Instant createdAt) {
}
