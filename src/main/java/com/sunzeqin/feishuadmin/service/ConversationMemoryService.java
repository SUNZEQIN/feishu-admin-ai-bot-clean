package com.sunzeqin.feishuadmin.service;

import com.sunzeqin.feishuadmin.config.FeishuProperties;
import com.sunzeqin.feishuadmin.pojo.ConversationMemoryMessage;
import com.sunzeqin.feishuadmin.pojo.FeishuMessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 会话记忆服务。
 *
 * <p>作用：把真实对话消息保存到 MySQL。用户消息保存一条，机器人回复保存一条。</p>
 *
 * @author sunzeqin
 */
@Service
public class ConversationMemoryService {
    // 当前服务使用的日志对象。
    private static final Logger log = LoggerFactory.getLogger(ConversationMemoryService.class);

    // 群聊记忆范围。
    private static final String GROUP_SCOPE = "GROUP";

    // 私聊记忆范围。
    private static final String PRIVATE_SCOPE = "PRIVATE";

    // 飞书配置，用来读取记忆开关和最大记忆条数。
    private final FeishuProperties properties;

    // Spring JDBC 工具，用来读写 MySQL。
    private final JdbcTemplate jdbcTemplate;

    public ConversationMemoryService(FeishuProperties properties, JdbcTemplate jdbcTemplate) {
        // 保存飞书配置。
        this.properties = properties;

        // 保存 JDBC 工具。
        this.jdbcTemplate = jdbcTemplate;
    }

    public String readMemoryText(FeishuMessageEvent event) {
        // 如果记忆功能关闭，就返回空字符串。
        if (!properties.isMemoryEnabled()) {
            return "";
        }

        // 按当前会话读取最近记忆。群聊和私聊都以 chat_id 为主线，不重复存储两份。
        List<ConversationMemoryMessage> messages = readMessages(chatMemoryScope(event), chatMemoryKey(event));

        // 保存提示词里的记忆文本。
        StringBuilder builder = new StringBuilder();

        // 写入会话记忆标题。
        builder.append("【会话记忆】\n");

        // 追加会话记忆内容。
        appendMessages(builder, messages);

        // 打印记忆读取日志。
        log.info("会话记忆读取：消息ID={}，范围={}，记忆Key={}，历史条数={}",
                event.messageId(), chatMemoryScope(event), chatMemoryKey(event), messages.size());

        // 返回历史记忆文本。
        return builder.toString();
    }

    public void saveUserMessage(FeishuMessageEvent event) {
        // 保存用户真实消息。只落一行，不再同时写个人记忆和群聊记忆。
        save(event, "user", event.text());
    }

    public void saveAssistantMessage(FeishuMessageEvent event, String reply) {
        // 保存机器人真实回复。只落一行，不再同时写个人记忆和群聊记忆。
        save(event, "assistant", reply);
    }

    private List<ConversationMemoryMessage> readMessages(String scope, String memoryKey) {
        // 查询最近 N 条记忆，先倒序取，后面再反转成时间正序。
        List<ConversationMemoryMessage> messages = jdbcTemplate.query("""
                        SELECT role, content, created_at
                        FROM agent_conversation_memory
                        WHERE memory_scope = ? AND memory_key = ?
                        ORDER BY id DESC
                        LIMIT ?
                        """,
                (resultSet, rowNumber) -> new ConversationMemoryMessage(
                        resultSet.getString("role"),
                        resultSet.getString("content"),
                        toInstant(resultSet.getTimestamp("created_at"))
                ),
                scope,
                memoryKey,
                properties.getMemoryMaxMessages());

        // 反转成从旧到新的顺序，让模型按正常对话顺序阅读。
        Collections.reverse(messages);

        // 返回记忆列表。
        return messages;
    }

    private void save(FeishuMessageEvent event, String role, String content) {
        // 如果记忆功能关闭，就不保存。
        if (!properties.isMemoryEnabled()) {
            return;
        }

        // 空内容不保存。
        if (content == null || content.isBlank()) {
            return;
        }

        // 获取当前会话的记忆范围。
        String scope = chatMemoryScope(event);

        // 获取当前会话的记忆键。
        String memoryKey = chatMemoryKey(event);

        // 写入一条真实消息。
        jdbcTemplate.update("""
                        INSERT INTO agent_conversation_memory
                        (memory_scope, memory_key, chat_id, user_open_id, user_id, role, content, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
                        """,
                scope,
                memoryKey,
                safe(event.chatId()),
                safe(event.openId()),
                safe(event.userId()),
                role,
                content);

        // 清理超过最大条数的旧记忆。
        trimOldMessages(scope, memoryKey);

        // 打印记忆写入日志。
        log.info("会话记忆写入：消息ID={}，范围={}，记忆Key={}，角色={}，说明=真实消息只保存一条",
                event.messageId(), scope, memoryKey, role);
    }

    private void trimOldMessages(String scope, String memoryKey) {
        // 删除超过最大保留条数的旧数据。
        jdbcTemplate.update("""
                DELETE FROM agent_conversation_memory
                WHERE id IN (
                    SELECT id FROM (
                        SELECT id
                        FROM agent_conversation_memory
                        WHERE memory_scope = ? AND memory_key = ?
                        ORDER BY id DESC
                        LIMIT 18446744073709551615 OFFSET ?
                    ) old_rows
                )
                """, scope, memoryKey, properties.getMemoryMaxMessages());
    }

    private void appendMessages(StringBuilder builder, List<ConversationMemoryMessage> messages) {
        // 没有历史记忆时写入“无”。
        if (messages.isEmpty()) {
            builder.append("无\n");
            return;
        }

        // 遍历历史消息。
        for (ConversationMemoryMessage message : messages) {
            // 拼接角色和内容。
            builder.append(message.role()).append("：").append(message.content()).append("\n");
        }
    }

    private String chatMemoryKey(FeishuMessageEvent event) {
        // 群聊和私聊都用 chatId 作为会话记忆键。
        return safe(event.chatId());
    }

    private String chatMemoryScope(FeishuMessageEvent event) {
        // group 表示群聊，共享群上下文。
        if ("group".equalsIgnoreCase(event.chatType())) {
            return GROUP_SCOPE;
        }

        // 非 group 都按私聊处理。
        return PRIVATE_SCOPE;
    }

    private String safe(String value) {
        // 空字符串保护，避免数据库 NOT NULL 字段写入 null。
        return value == null ? "" : value;
    }

    private Instant toInstant(Timestamp timestamp) {
        // 数据库时间为空时使用当前时间兜底。
        if (timestamp == null) {
            return Instant.now();
        }

        // 转成 Instant。
        return timestamp.toInstant();
    }
}
