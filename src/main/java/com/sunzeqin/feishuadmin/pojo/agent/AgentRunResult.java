package com.sunzeqin.feishuadmin.pojo.agent;

/**
 * Agent 完整执行结果。
 *
 * <p>作用：保存 Agent 多步执行后的最终回复。</p>
 *
 * @param success 是否成功
 * @param reply   回复给飞书用户的文本
 *
 * @author sunzeqin
 */
public record AgentRunResult(boolean success, String reply) {
}
