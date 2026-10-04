CREATE TABLE IF NOT EXISTS agent_conversation_memory (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    memory_scope VARCHAR(32) NOT NULL COMMENT '记忆范围：GROUP=群聊，PRIVATE=私聊',
    memory_key VARCHAR(255) NOT NULL COMMENT '记忆键：当前使用chat_id，一条真实消息只保存一行',
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

CREATE TABLE IF NOT EXISTS agent_conversation_summary (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    memory_scope VARCHAR(32) NOT NULL COMMENT '记忆范围：GROUP=群聊，PRIVATE=私聊',
    memory_key VARCHAR(255) NOT NULL COMMENT '记忆键：当前使用chat_id',
    chat_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '飞书会话ID',
    summary MEDIUMTEXT NOT NULL COMMENT '压缩后的历史上下文摘要',
    compressed_message_count INT NOT NULL DEFAULT 0 COMMENT '累计压缩消息条数',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_memory_scope_key (memory_scope, memory_key),
    KEY idx_summary_chat_id (chat_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent会话压缩摘要表';

CREATE TABLE IF NOT EXISTS feishu_user_oauth_token (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    app_id VARCHAR(128) NOT NULL COMMENT '飞书应用ID',
    user_open_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '授权用户open_id',
    user_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '授权用户user_id',
    scope_text TEXT NOT NULL COMMENT '用户已授权scope，空格分隔',
    access_token TEXT NOT NULL COMMENT '用户access_token，按需求不加密保存',
    refresh_token TEXT NOT NULL COMMENT '用户refresh_token，按需求不加密保存',
    expires_at TIMESTAMP NULL DEFAULT NULL COMMENT 'access_token过期时间',
    refresh_expires_at TIMESTAMP NULL DEFAULT NULL COMMENT 'refresh_token过期时间',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_user_open_id (app_id, user_open_id),
    KEY idx_oauth_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='飞书用户OAuth token表';

CREATE TABLE IF NOT EXISTS feishu_oauth_state (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    state VARCHAR(128) NOT NULL COMMENT 'OAuth state随机串',
    app_id VARCHAR(128) NOT NULL COMMENT '飞书应用ID',
    user_open_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '触发授权的用户open_id',
    user_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '触发授权的用户user_id',
    chat_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '触发授权的会话ID',
    message_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '触发授权的消息ID',
    scope_text TEXT NOT NULL COMMENT '本次申请的scope，空格分隔',
    used TINYINT NOT NULL DEFAULT 0 COMMENT '是否已使用：0=未使用，1=已使用',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oauth_state (state),
    KEY idx_oauth_state_user (app_id, user_open_id),
    KEY idx_oauth_state_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='飞书OAuth授权状态表';

CREATE TABLE IF NOT EXISTS bot_user_role (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    open_id VARCHAR(128) NOT NULL COMMENT '飞书用户open_id',
    role VARCHAR(8) NOT NULL DEFAULT 'L1' COMMENT '角色等级：L1普通成员，L2运营负责人，L3管理员',
    remark VARCHAR(255) NOT NULL DEFAULT '' COMMENT '备注：谁在什么时候加的这条名单',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_bot_user_role_open_id (open_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='机器人使用者角色表';

CREATE TABLE IF NOT EXISTS agent_task (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    task_id VARCHAR(64) NOT NULL COMMENT '任务ID',
    message_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '飞书消息ID，用于串联日志',
    chat_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '飞书会话ID',
    chat_type VARCHAR(32) NOT NULL DEFAULT '' COMMENT '会话类型：p2p单聊，group群聊',
    user_open_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '发送人open_id',
    user_role VARCHAR(8) NOT NULL DEFAULT 'L1' COMMENT '发送人角色：L1/L2/L3',
    raw_input TEXT NOT NULL COMMENT '用户原始输入',
    intent_domain VARCHAR(32) NOT NULL DEFAULT '' COMMENT '意图域：ecommerce或飞书业务域',
    status VARCHAR(16) NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/SUCCESS/FAILED/PARTIAL/WAITING_CONFIRM',
    stage VARCHAR(64) NOT NULL DEFAULT '' COMMENT '当前或结束阶段说明',
    start_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
    end_time TIMESTAMP NULL DEFAULT NULL COMMENT '结束时间',
    error_code VARCHAR(32) NOT NULL DEFAULT '' COMMENT '错误码',
    error_msg VARCHAR(512) NOT NULL DEFAULT '' COMMENT '错误信息',
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_task_task_id (task_id),
    KEY idx_agent_task_message_id (message_id),
    KEY idx_agent_task_start_time (start_time),
    KEY idx_agent_task_user_time (user_open_id, start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent任务表';

CREATE TABLE IF NOT EXISTS agent_tool_call_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    task_id VARCHAR(64) NOT NULL COMMENT '所属任务ID',
    step_no INT NOT NULL DEFAULT 0 COMMENT '第几步',
    tool_name VARCHAR(64) NOT NULL DEFAULT '' COMMENT '工具名称',
    args_digest VARCHAR(64) NOT NULL DEFAULT '' COMMENT '入参摘要，不存原文',
    result_digest VARCHAR(64) NOT NULL DEFAULT '' COMMENT '结果摘要，不存原文',
    success TINYINT NOT NULL DEFAULT 0 COMMENT '是否成功：0失败，1成功',
    error_code VARCHAR(32) NOT NULL DEFAULT '' COMMENT '错误码，如PERMISSION_DENIED',
    error_msg VARCHAR(512) NOT NULL DEFAULT '' COMMENT '失败原因',
    cost_ms BIGINT NOT NULL DEFAULT 0 COMMENT '本次调用耗时（毫秒）',
    mcp_latency_ms BIGINT NOT NULL DEFAULT 0 COMMENT '电商MCP侧耗时（毫秒）',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_tool_call_task_step (task_id, step_no),
    KEY idx_tool_call_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent工具调用明细表';

