package com.sunzeqin.feishuadmin.pojo.agent;

import com.sunzeqin.feishuadmin.pojo.tool.ToolCall;

/**
 * Agent 每一轮的决策结果。
 *
 * <p>作用：LLM 每轮只做一个决定：要么调用一个工具，要么输出最终回复。</p>
 *
 * @param type       决策类型，tool_call 或 final_answer
 * @param reason     决策原因
 * @param toolCall   工具调用，type=tool_call 时使用
 * @param finalReply 最终回复，type=final_answer 时使用
 *
 * @author sunzeqin
 */
public record AgentDecision(String type, String reason, ToolCall toolCall, String finalReply) {

    public AgentDecision {
        // type 为空时默认最终回复，避免循环乱跑。
        type = type == null || type.isBlank() ? "final_answer" : type;

        // reason 为空时转成空字符串。
        reason = reason == null ? "" : reason;

        // finalReply 为空时转成空字符串。
        finalReply = finalReply == null ? "" : finalReply;
    }

    public boolean toolCallDecision() {
        // 判断当前决策是否为工具调用。
        return "tool_call".equals(type);
    }
}
