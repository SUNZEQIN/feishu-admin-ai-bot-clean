CREATE TABLE IF NOT EXISTS agent_conversation_memory (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    memory_scope VARCHAR(32) NOT NULL COMMENT '记忆范围：USER=个人记忆，GROUP=群聊共享记忆',
    memory_key VARCHAR(255) NOT NULL COMMENT '记忆键：USER使用chatId:openId，GROUP使用chatId',
    chat_id VARCHAR(128) NOT NULL COMMENT '飞书会话ID',
    user_open_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '发送人open_id',
    user_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '发送人user_id',
    role VARCHAR(32) NOT NULL COMMENT '消息角色：user=用户，assistant=机器人',
    content TEXT NOT NULL COMMENT '消息内容',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_memory_scope_key_id (memory_scope, memory_key, id),
    KEY idx_memory_chat_id (chat_id),
    KEY idx_memory_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent会话记忆表';
