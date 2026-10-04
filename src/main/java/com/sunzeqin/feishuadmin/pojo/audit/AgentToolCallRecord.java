package com.sunzeqin.feishuadmin.pojo.audit;

/**
 * 一次工具调用审计记录，对应 `agent_tool_call_log` 表。
 *
 * <p>作用：回答"调了什么工具、耗时多少、成功还是被拦截"（产品规则 O-05）。
 * 入参与结果只存摘要（digest），不存原文，避免把业务数据和敏感信息抄一份到审计表。</p>
 *
 * @param taskId       所属任务 ID
 * @param stepNo       第几步
 * @param toolName     工具名称
 * @param argsDigest   入参摘要
 * @param resultDigest 结果摘要
 * @param success      是否成功
 * @param errorCode    失败错误码，成功时为空字符串
 * @param errorMsg     失败原因
 * @param costMs       本次调用耗时（毫秒）
 * @param mcpLatencyMs 电商 MCP 侧耗时（毫秒），未埋点时记 0
 *
 * @author sunzeqin
 */
public record AgentToolCallRecord(String taskId, int stepNo, String toolName, String argsDigest,
        String resultDigest, boolean success, String errorCode, String errorMsg,
        long costMs, long mcpLatencyMs) {
}
