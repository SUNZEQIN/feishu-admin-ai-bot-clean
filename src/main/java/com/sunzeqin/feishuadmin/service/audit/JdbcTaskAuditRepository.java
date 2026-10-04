package com.sunzeqin.feishuadmin.service.audit;

import com.sunzeqin.feishuadmin.pojo.audit.AgentTaskRecord;
import com.sunzeqin.feishuadmin.pojo.audit.AgentToolCallRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;

/**
 * 基于 MySQL 的审计仓库实现。
 *
 * <p>表结构见 `src/main/resources/db/schema-mysql.sql` 里的 `agent_task` 与 `agent_tool_call_log`。</p>
 *
 * @author sunzeqin
 */
@Repository
public class JdbcTaskAuditRepository implements TaskAuditRepository {

    // JDBC 模板，用于写入审计表。
    private final JdbcTemplate jdbcTemplate;

    public JdbcTaskAuditRepository(JdbcTemplate jdbcTemplate) {
        // 保存 JDBC 模板。
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insertTask(AgentTaskRecord record) {
        // 插入任务行，状态由调用方给出（初始为 RUNNING）。
        jdbcTemplate.update("""
                INSERT INTO agent_task (task_id, message_id, chat_id, chat_type, user_open_id, user_role,
                    raw_input, intent_domain, status, stage, start_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                record.taskId(), record.messageId(), record.chatId(), record.chatType(), record.userOpenId(),
                record.userRole(), record.rawInput(), record.intentDomain(), record.status(), record.stage(),
                Timestamp.valueOf(record.startTime()));
    }

    @Override
    public void finishTask(String taskId, String status, String stage, String errorCode, String errorMsg) {
        // 结束任务：写最终状态、阶段、错误信息和结束时间。
        jdbcTemplate.update("""
                UPDATE agent_task
                SET status = ?, stage = ?, error_code = ?, error_msg = ?, end_time = CURRENT_TIMESTAMP
                WHERE task_id = ?
                """, status, stage, errorCode, errorMsg, taskId);
    }

    @Override
    public void updateIntentDomain(String taskId, String intentDomain) {
        // 只在意图域为空时写入，保证第一次识别的结果生效。
        jdbcTemplate.update("""
                UPDATE agent_task
                SET intent_domain = ?
                WHERE task_id = ? AND (intent_domain IS NULL OR intent_domain = '')
                """, intentDomain, taskId);
    }

    @Override
    public void insertToolCall(AgentToolCallRecord record) {
        // 插入工具调用明细。
        jdbcTemplate.update("""
                INSERT INTO agent_tool_call_log (task_id, step_no, tool_name, args_digest, result_digest,
                    success, error_code, error_msg, cost_ms, mcp_latency_ms)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                record.taskId(), record.stepNo(), record.toolName(), record.argsDigest(), record.resultDigest(),
                record.success() ? 1 : 0, record.errorCode(), record.errorMsg(), record.costMs(),
                record.mcpLatencyMs());
    }
}
