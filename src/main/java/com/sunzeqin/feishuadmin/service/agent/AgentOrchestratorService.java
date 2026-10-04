package com.sunzeqin.feishuadmin.service.agent;

import com.sunzeqin.feishuadmin.pojo.FeishuMessageEvent;
import com.sunzeqin.feishuadmin.pojo.agent.AgentDecision;
import com.sunzeqin.feishuadmin.pojo.agent.AgentRunResult;
import com.sunzeqin.feishuadmin.pojo.audit.AgentTaskStatus;
import com.sunzeqin.feishuadmin.pojo.role.BotRole;
import com.sunzeqin.feishuadmin.pojo.tool.ToolCall;
import com.sunzeqin.feishuadmin.pojo.tool.ToolErrorCode;
import com.sunzeqin.feishuadmin.pojo.tool.ToolResult;
import com.sunzeqin.feishuadmin.service.ConversationMemoryService;
import com.sunzeqin.feishuadmin.service.audit.TaskAuditService;
import com.sunzeqin.feishuadmin.service.role.BotRoleResolver;
import com.sunzeqin.feishuadmin.service.tool.ToolRegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 编排服务。
 *
 * <p>作用：像 Codex 一样执行 plan -> tool -> observe -> plan 的循环。
 * LLM 负责决定下一步，Java 负责执行工具和保存观察结果。</p>
 *
 * @author sunzeqin
 */
@Service
public class AgentOrchestratorService {
    // 当前编排器使用的日志对象。
    private static final Logger log = LoggerFactory.getLogger(AgentOrchestratorService.class);

    // 最大循环步数，防止模型无限调用工具。
    private static final int MAX_STEPS = 10;

    // Agent 规划器。
    private final AgentPlannerService planner;

    // 工具注册表。
    private final ToolRegistryService toolRegistry;

    // 会话记忆服务，用来读取和保存用户上下文。
    private final ConversationMemoryService memoryService;

    // 角色解析服务，用来把发送人翻译成 L1/L2/L3。
    private final BotRoleResolver botRoleResolver;

    // 任务审计服务，用来落任务行与工具调用明细。
    private final TaskAuditService taskAuditService;

    // 数据来源标注服务，给用到电商数据的回复追加来源说明。
    private final DataSourceNoticeService dataSourceNoticeService;

    public AgentOrchestratorService(AgentPlannerService planner, ToolRegistryService toolRegistry,
            ConversationMemoryService memoryService, BotRoleResolver botRoleResolver,
            TaskAuditService taskAuditService, DataSourceNoticeService dataSourceNoticeService) {
        // 保存 Agent 规划器。
        this.planner = planner;

        // 保存工具注册表。
        this.toolRegistry = toolRegistry;

        // 保存会话记忆服务。
        this.memoryService = memoryService;

        // 保存角色解析服务。
        this.botRoleResolver = botRoleResolver;

        // 保存任务审计服务。
        this.taskAuditService = taskAuditService;

        // 保存数据来源标注服务。
        this.dataSourceNoticeService = dataSourceNoticeService;
    }

    public AgentRunResult run(FeishuMessageEvent event) {
        // 解析角色：审计与权限用同一个来源，避免两处判断不一致。
        BotRole role = botRoleResolver.resolve(event.openId());

        // 开始任务审计，任务 ID 用于把日志、任务表、工具明细串起来。
        String taskId = taskAuditService.startTask(event, role);

        try {
            // 执行 Agent 循环。
            return runAgent(event, taskId, role);
        } catch (RuntimeException e) {
            // 未分类异常也必须写结果，不能让任务永远停在 RUNNING。
            log.error("[阶段3 外层Agent规划] 未分类异常：消息ID={}，任务ID={}，错误={}",
                    event.messageId(), taskId, e.getMessage(), e);
            taskAuditService.finishTask(taskId, AgentTaskStatus.FAILED, "内部异常",
                    ToolErrorCode.INTERNAL_ERROR, e.getMessage());
            throw e;
        }
    }

    private AgentRunResult runAgent(FeishuMessageEvent event, String taskId, BotRole role) {
        // 打印 Agent 开始执行日志，方便用 messageId 串起整次请求。
        log.info("[阶段3 外层Agent规划] 开始执行：消息ID={}，任务ID={}，会话ID={}，会话类型={}，最大步骤数={}，用户文本={}",
                event.messageId(), taskId, event.chatId(), event.chatType(), MAX_STEPS, event.text());

        // 读取当前用户在当前会话里的历史记忆。
        String memoryText = memoryService.readMemoryText(event);

        // 保存当前用户输入，供下一轮对话使用。
        memoryService.saveUserMessage(event);

        // LLM Agent Loop 没启用时，使用本地稳定兜底流程。
        if (!planner.enabled()) {
            // 打印未启用日志。
            log.info("[阶段3 外层Agent规划] 停止执行：消息ID={}，原因=规划器未启用", event.messageId());

            // 没有 LLM 时无法判断应该调用哪个 CLI 业务域。
            AgentRunResult result = new AgentRunResult(false,
                    "⚠️ LLM 规划器未启用，无法判断要调用哪个飞书 CLI 能力。请先启用大模型配置。");
            memoryService.saveAssistantMessage(event, result.reply());
            taskAuditService.finishTask(taskId, AgentTaskStatus.FAILED, "规划器未启用",
                    ToolErrorCode.INTERNAL_ERROR, "LLM 规划器未启用");
            return result;
        }

        // 保存每一步工具观察结果。
        List<ToolResult> observations = new ArrayList<>();

        // 是否出现过"等待用户确认"的高风险操作拦截。
        boolean pendingConfirm = false;

        // 最多执行 MAX_STEPS 轮。
        for (int step = 1; step <= MAX_STEPS; step++) {
            // 打印每一轮开始日志。
            log.info("[阶段3 外层Agent规划] 步骤开始：消息ID={}，步骤={}，已有观察结果数量={}",
                    event.messageId(), step, observations.size());

            // 让 LLM 基于当前 observations 决定下一步。
            AgentDecision decision = planner.decide(event.messageId(), step, event.text(), event.chatId(),
                    memoryText, observations);

            // 打印当前轮规划结果。
            log.info("[阶段3 外层Agent规划] 步骤决策：消息ID={}，步骤={}，决策类型={}，工具={}，原因={}，最终回复长度={}",
                    event.messageId(),
                    step,
                    decision.type(),
                    decision.toolCall() == null ? "" : decision.toolCall().name(),
                    decision.reason(),
                    decision.finalReply() == null ? 0 : decision.finalReply().length());

            // 如果 LLM 输出最终回复，就结束循环。
            if (!decision.toolCallDecision()) {
                // 用到电商数据时统一追加数据来源标注（产品规则 C-08）。
                String finalReply = dataSourceNoticeService.apply(decision.finalReply(), observations);

                // 打印最终回复日志。
                log.info("[阶段3 外层Agent规划] 生成最终回复：消息ID={}，步骤={}，回复长度={}",
                        event.messageId(), step, finalReply == null ? 0 : finalReply.length());
                AgentRunResult result = new AgentRunResult(true, finalReply);
                memoryService.saveAssistantMessage(event, result.reply());

                // 被高风险闸门拦下的任务记成等待确认，其余记成功。
                taskAuditService.finishTask(taskId,
                        pendingConfirm ? AgentTaskStatus.WAITING_CONFIRM : AgentTaskStatus.SUCCESS,
                        pendingConfirm ? "等待用户确认" : "生成最终回复", null, "");
                return result;
            }

            // 如果 LLM 说要调用工具但没给工具参数，直接结束。
            if (decision.toolCall() == null) {
                // 打印缺少工具调用日志。
                log.warn("[阶段3 外层Agent规划] 执行失败：消息ID={}，步骤={}，原因=缺少工具调用参数", event.messageId(), step);
                AgentRunResult result = new AgentRunResult(false, "⚠️ Agent 没有给出可执行工具。");
                memoryService.saveAssistantMessage(event, result.reply());
                taskAuditService.finishTask(taskId, AgentTaskStatus.FAILED, "缺少工具调用参数",
                        ToolErrorCode.INTERNAL_ERROR, "Agent 没有给出可执行工具");
                return result;
            }

            // 注入当前飞书事件上下文，方便工具引用原消息和@触发人。
            ToolCall toolCall = enrichToolCall(event, decision.toolCall());

            // 打印工具执行前日志。
            log.info("[阶段4 工具调用] 准备执行工具：消息ID={}，步骤={}，工具={}，入参={}",
                    event.messageId(), step, toolCall.name(), toolCall.params());

            // 执行工具，并记录真实耗时。
            long startedAt = System.currentTimeMillis();
            ToolResult result = toolRegistry.execute(toolCall);
            long costMs = System.currentTimeMillis() - startedAt;

            // 每次工具调用落一行审计明细，被拒绝的调用同样落库（带错误码）。
            taskAuditService.recordToolCall(taskId, step, toolCall, result, costMs);

            // 打印工具执行结果日志。
            log.info("[阶段4 工具调用] 工具返回摘要：消息ID={}，步骤={}，工具={}，是否成功={}，说明={}，数据字段={}",
                    event.messageId(), step, result.tool(), result.success(), result.message(), result.data().keySet());
            log.debug("[阶段4 工具调用] 工具完整数据：消息ID={}，步骤={}，工具={}，数据={}",
                    event.messageId(), step, result.tool(), result.data());

            // 保存工具观察结果。
            observations.add(result);

            // 高风险操作被拦下时，任务最终状态要记成等待确认。
            if (Boolean.TRUE.equals(result.data().get("needConfirm"))) {
                pendingConfirm = true;
            }

            // 如果工具已经返回授权链接，直接回复用户，不再交给大模型二次解释，避免误说“不支持授权”。
            String authorizeReply = authorizeReplyFromToolResult(result);
            if (!authorizeReply.isBlank()) {
                String authorizeUrl = authorizeUrlFromToolResult(result);
                log.info("[阶段4 工具调用] 授权链接已生成，直接结束流程：消息ID={}，步骤={}，工具={}",
                        event.messageId(), step, result.tool());
                AgentRunResult runResult = new AgentRunResult(true, authorizeReply, authorizeUrl);
                memoryService.saveAssistantMessage(event, runResult.reply());
                taskAuditService.finishTask(taskId, AgentTaskStatus.SUCCESS, "等待用户授权", null, "");
                return runResult;
            }

            // 工具失败时结束执行，并把原因回复给用户。
            if (!result.success()) {
                // 打印工具失败导致 Agent 结束的日志。
                log.warn("[阶段4 工具调用] 工具失败导致流程结束：消息ID={}，步骤={}，工具={}，原因={}",
                        event.messageId(), step, result.tool(), result.message());

                // 前面有成功步骤就是部分成功，否则整体失败（产品规则 O-02）。
                AgentTaskStatus status = hasEarlierSuccess(observations)
                        ? AgentTaskStatus.PARTIAL
                        : AgentTaskStatus.FAILED;

                // 落最终状态与错误码，越权拦截会被记成 PERMISSION_DENIED。
                taskAuditService.finishTask(taskId, status, "工具失败", errorCodeOf(result), result.message());

                AgentRunResult runResult = new AgentRunResult(false, "⚠️ 执行失败\n\n🔎 原因：" + result.message());
                memoryService.saveAssistantMessage(event, runResult.reply());
                return runResult;
            }
        }

        // 超过最大步数仍未结束，返回保护性提示。
        log.warn("[阶段3 外层Agent规划] 强制停止：消息ID={}，原因=超过最大步骤数，最大步骤数={}",
                event.messageId(), MAX_STEPS);
        AgentRunResult result = new AgentRunResult(false, "⚠️ 本次任务步骤过多，已停止执行，避免重复操作。");
        memoryService.saveAssistantMessage(event, result.reply());
        taskAuditService.finishTask(taskId, AgentTaskStatus.FAILED, "超过最大步骤数",
                ToolErrorCode.INTERNAL_ERROR, "超过最大步骤数");
        return result;
    }

    /**
     * 判断失败之前是否已经有成功的工具调用。
     *
     * <p>用于区分「部分成功」和「整体失败」：前一步拿到了数据、后一步失败，
     * 对用户来说就是部分完成，必须如实记录。</p>
     */
    private boolean hasEarlierSuccess(List<ToolResult> observations) {
        // 至少要有两次调用，才存在"之前成功过"的可能。
        if (observations.size() < 2) {
            return false;
        }

        // 除最后一条（本次失败）之外，是否存在成功结果。
        for (int i = 0; i < observations.size() - 1; i++) {
            if (observations.get(i).success()) {
                return true;
            }
        }

        // 之前没有成功步骤。
        return false;
    }

    /**
     * 从工具结果里解析审计用的错误码。
     *
     * <p>没有错误码时统一按 INTERNAL_ERROR 记，避免审计表出现空白无法分类的失败。</p>
     */
    private ToolErrorCode errorCodeOf(ToolResult result) {
        // 空结果按内部错误处理。
        if (result == null || result.errorCode() == null || result.errorCode().isBlank()) {
            return ToolErrorCode.INTERNAL_ERROR;
        }

        try {
            // 按枚举名解析。
            return ToolErrorCode.valueOf(result.errorCode());
        } catch (IllegalArgumentException e) {
            // 未知错误码不猜，按内部错误记。
            return ToolErrorCode.INTERNAL_ERROR;
        }
    }

    private String authorizeReplyFromToolResult(ToolResult result) {
        // 空结果直接返回空字符串。
        if (result == null || result.data() == null || result.data().isEmpty()) {
            return "";
        }

        // 只有真正包含授权链接时，才直接返回。
        if (authorizeUrlFromToolResult(result).isBlank()) {
            return "";
        }

        // 优先使用工具已经整理好的用户回复。
        Object finalReply = result.data().get("finalReply");
        if (finalReply != null && !finalReply.toString().isBlank()) {
            return finalReply.toString();
        }

        // 没有 finalReply 时组装兜底回复，用户侧不展示冗长 scope，避免飞书消息过长截断授权链接。
        return "需要你授权后才能继续执行。\n\n"
                + "请扫描二维码完成授权。\n\n"
                + "授权完成后，系统会保存到用户表并定时刷新 token。";
    }

    private String authorizeUrlFromToolResult(ToolResult result) {
        // 空结果直接返回空字符串。
        if (result == null || result.data() == null || result.data().isEmpty()) {
            return "";
        }

        // 读取授权链接。
        Object authorizeUrl = result.data().get("authorizeUrl");
        if (authorizeUrl == null || authorizeUrl.toString().isBlank()) {
            return "";
        }

        // 返回授权链接。
        return authorizeUrl.toString();
    }

    private ToolCall enrichToolCall(FeishuMessageEvent event, ToolCall toolCall) {
        // 空工具调用直接返回。
        if (toolCall == null) {
            return null;
        }

        // 复制一份参数，避免修改不可变 Map。
        Map<String, Object> params = new HashMap<>(toolCall.params());

        // 注入来源会话 ID，工具层权限校验要用它判断会话是否在白名单里。
        putIfPresent(params, "sourceChatId", event.chatId());

        // 注入原消息 ID，用于卡片或消息 reply 原文。
        putIfPresent(params, "originalMessageId", event.messageId());

        // 注入发送人 open_id，用于工具层权限校验和群聊里 @ 对应的人。
        putIfPresent(params, "senderOpenId", event.openId());

        // 注入发送人 user_id，供个别命令需要 user_id 时使用。
        putIfPresent(params, "senderUserId", event.userId());

        // 所有工具都注入真实事件上下文，模型无法再自己编造群 ID 或调用者身份。
        return new ToolCall(toolCall.name(), params);
    }

    private void putIfPresent(Map<String, Object> params, String key, String value) {
        // 空值不写入：ToolCall 内部使用 Map.copyOf，写入 null 会直接抛异常。
        if (value != null && !value.isBlank()) {
            params.put(key, value);
        }
    }

}
