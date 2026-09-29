package com.sunzeqin.feishuadmin.pojo.cli;

import java.util.List;

/**
 * CLI Skill 单步决策。
 *
 * <p>作用：保存 LLM 在 Skill + CLI 执行器里规划出来的下一步动作。</p>
 *
 * @param type       动作类型：cli_command 或 final_answer
 * @param reason     决策原因
 * @param command    CLI 命令参数数组
 * @param finalReply 最终回复
 *
 * @author sunzeqin
 */
public record CliStepDecision(String type, String reason, List<String> command, String finalReply) {

    public CliStepDecision {
        // type 为空时默认走最终回复，避免误执行。
        type = type == null ? "final_answer" : type;

        // reason 为空时转成空字符串。
        reason = reason == null ? "" : reason;

        // command 为空时转成空列表。
        command = command == null ? List.of() : List.copyOf(command);

        // finalReply 为空时转成空字符串。
        finalReply = finalReply == null ? "" : finalReply;
    }

    public boolean commandDecision() {
        // 判断当前决策是否要执行 CLI 命令。
        return "cli_command".equals(type);
    }
}
