package com.sunzeqin.feishuadmin.service.agent;

import com.sunzeqin.feishuadmin.pojo.ChatMember;
import com.sunzeqin.feishuadmin.pojo.FeishuMessageEvent;
import com.sunzeqin.feishuadmin.pojo.agent.AgentDecision;
import com.sunzeqin.feishuadmin.pojo.agent.AgentRunResult;
import com.sunzeqin.feishuadmin.pojo.tool.ToolCall;
import com.sunzeqin.feishuadmin.pojo.tool.ToolResult;
import com.sunzeqin.feishuadmin.service.ConversationMemoryService;
import com.sunzeqin.feishuadmin.service.FeishuOpenApiService;
import com.sunzeqin.feishuadmin.service.tool.ToolRegistryService;
import com.sunzeqin.feishuadmin.utils.TextIntentUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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

    // 飞书 OpenAPI 服务，兜底执行时会用到。
    private final FeishuOpenApiService openApi;

    // 会话记忆服务，用来读取和保存用户上下文。
    private final ConversationMemoryService memoryService;

    public AgentOrchestratorService(AgentPlannerService planner, ToolRegistryService toolRegistry,
            FeishuOpenApiService openApi, ConversationMemoryService memoryService) {
        // 保存 Agent 规划器。
        this.planner = planner;

        // 保存工具注册表。
        this.toolRegistry = toolRegistry;

        // 保存 OpenAPI 服务。
        this.openApi = openApi;

        // 保存会话记忆服务。
        this.memoryService = memoryService;
    }

    public AgentRunResult run(FeishuMessageEvent event) {
        // 打印 Agent 开始执行日志，方便用 messageId 串起整次请求。
        log.info("[阶段3 外层Agent规划] 开始执行：消息ID={}，会话ID={}，会话类型={}，最大步骤数={}，用户文本={}",
                event.messageId(), event.chatId(), event.chatType(), MAX_STEPS, event.text());

        // 读取当前用户在当前会话里的历史记忆。
        String memoryText = memoryService.readMemoryText(event);

        // 保存当前用户输入，供下一轮对话使用。
        memoryService.saveUserMessage(event);

        // LLM Agent Loop 没启用时，使用本地稳定兜底流程。
        if (!planner.enabled()) {
            // 打印兜底模式日志。
            log.info("[阶段3 外层Agent规划] 进入兜底流程：消息ID={}，原因=规划器未启用", event.messageId());
            AgentRunResult result = fallbackRun(event);
            memoryService.saveAssistantMessage(event, result.reply());
            return result;
        }

        // 保存每一步工具观察结果。
        List<ToolResult> observations = new ArrayList<>();

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
                // 打印最终回复日志。
                log.info("[阶段3 外层Agent规划] 生成最终回复：消息ID={}，步骤={}，回复长度={}",
                        event.messageId(), step, decision.finalReply() == null ? 0 : decision.finalReply().length());
                AgentRunResult result = new AgentRunResult(true, decision.finalReply());
                memoryService.saveAssistantMessage(event, result.reply());
                return result;
            }

            // 如果 LLM 说要调用工具但没给工具参数，直接结束。
            if (decision.toolCall() == null) {
                // 打印缺少工具调用日志。
                log.warn("[阶段3 外层Agent规划] 执行失败：消息ID={}，步骤={}，原因=缺少工具调用参数", event.messageId(), step);
                AgentRunResult result = new AgentRunResult(false, "⚠️ Agent 没有给出可执行工具。");
                memoryService.saveAssistantMessage(event, result.reply());
                return result;
            }

            // 给部分工具补充系统上下文参数，例如当前提问人的 open_id。
            ToolCall toolCall = enrichToolCall(decision.toolCall(), event);

            // 打印工具执行前日志。
            log.info("[阶段4 工具调用] 准备执行工具：消息ID={}，步骤={}，工具={}，入参={}",
                    event.messageId(), step, toolCall.name(), toolCall.params());

            // 执行工具。
            ToolResult result = toolRegistry.execute(toolCall);

            // 打印工具执行结果日志。
            log.info("[阶段4 工具调用] 工具返回摘要：消息ID={}，步骤={}，工具={}，是否成功={}，说明={}，数据字段={}",
                    event.messageId(), step, result.tool(), result.success(), result.message(), result.data().keySet());
            log.debug("[阶段4 工具调用] 工具完整数据：消息ID={}，步骤={}，工具={}，数据={}",
                    event.messageId(), step, result.tool(), result.data());

            // 保存工具观察结果。
            observations.add(result);

            // 工具失败时结束执行，并把原因回复给用户。
            if (!result.success()) {
                // 打印工具失败导致 Agent 结束的日志。
                log.warn("[阶段4 工具调用] 工具失败导致流程结束：消息ID={}，步骤={}，工具={}，原因={}",
                        event.messageId(), step, result.tool(), result.message());
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
        return result;
    }

    private AgentRunResult fallbackRun(FeishuMessageEvent event) {
        // 本地兜底先判断是否是当前支持的建群需求。
        if (!TextIntentUtils.createChatFromCurrentMembers(event.text(), event.chatType())) {
            return new AgentRunResult(true, "✅ 已收到。目前干净版先支持：在群里新建群聊，并把当前群里的用户和机器人拉进去。");
        }

        try {
            // 提取新群名称。
            String chatName = TextIntentUtils.chatName(event.text(), "机器人测试群");

            // 判断是否需要用户。
            boolean includeUsers = TextIntentUtils.includeUsers(event.text());

            // 判断是否需要机器人。
            boolean includeBots = TextIntentUtils.includeBots(event.text());

            // 提取指定名称。
            List<String> targetNames = TextIntentUtils.targetNames(event.text());

            // 查询用户成员。
            List<ChatMember> users = includeUsers ? filteredMembers(event.chatId(), "open_id", false, targetNames) : List.of();

            // 查询机器人成员；飞书查询群成员接口不支持 app_id，所以这里也用 open_id 查询后按 bot 字段过滤。
            List<ChatMember> bots = includeBots ? filteredMembers(event.chatId(), "open_id", true, targetNames) : List.of();

            // 提取用户 open_id。
            List<String> userIds = memberIds(users);

            // 提取机器人 app_id；只有 cli_ 开头的 ID 才能放进 botAppIds。
            List<String> botIds = botAppIds(bots);

            // 通过工具注册表创建群聊，保持兜底流程也走工具入口。
            ToolResult result = toolRegistry.execute(new ToolCall("im.create_chat", Map.of(
                    "chatName", chatName,
                    "userOpenIds", userIds,
                    "botAppIds", botIds
            )));

            // 工具失败时返回失败结果。
            if (!result.success()) {
                return new AgentRunResult(false, "⚠️ 没有完成新建群聊\n\n🔎 失败原因：" + result.message());
            }

            // 读取新群 chat_id。
            String newChatId = result.data().get("chatId").toString();

            // 返回成功回复。
            return new AgentRunResult(true, "✅ 已创建群聊并拉入匹配成员\n\n"
                    + "💬 群名：" + chatName + "\n"
                    + "🔑 chat_id：" + newChatId + "\n"
                    + "👤 用户：" + users.size() + " 人" + names(users) + "\n"
                    + "🤖 机器人：" + bots.size() + " 个" + names(bots));
        } catch (Exception e) {
            // 兜底流程异常时返回失败回复。
            log.warn("[阶段3 外层Agent规划] 兜底流程失败：消息ID={}，错误={}", event.messageId(), e.getMessage());
            return new AgentRunResult(false, "⚠️ 没有完成处理\n\n🔎 失败原因：" + e.getMessage());
        }
    }

    private ToolCall enrichToolCall(ToolCall toolCall, FeishuMessageEvent event) {
        // 查询用户可用应用时，必须用当前提问人的 open_id，不能让模型自己猜。
        if ("application.list_installed_apps".equals(toolCall.name())) {
            Map<String, Object> params = new java.util.HashMap<>(toolCall.params());
            params.put("openId", event.openId());
            return new ToolCall(toolCall.name(), params);
        }

        // 其它工具保持原样。
        return toolCall;
    }

    private List<ChatMember> filteredMembers(String chatId, String memberIdType, boolean bot, List<String> targetNames) {
        // 查询群成员。
        List<ChatMember> allMembers = openApi.listChatMembers(chatId, memberIdType);

        // 保存命中的成员。
        List<ChatMember> matched = new ArrayList<>();

        // 遍历成员。
        for (ChatMember member : allMembers) {
            // 按机器人/用户类型过滤。
            if (member.bot() != bot) {
                continue;
            }

            // 按名称过滤。
            if (!nameMatched(member, targetNames)) {
                continue;
            }

            // 保存命中的成员。
            matched.add(member);
        }

        // 返回命中成员。
        return matched;
    }

    private boolean nameMatched(ChatMember member, List<String> targetNames) {
        // targetNames 为空表示全量命中。
        if (targetNames.isEmpty()) {
            return true;
        }

        // 成员名称为空时无法按名称命中。
        if (member.name() == null || member.name().isBlank()) {
            return false;
        }

        // 遍历目标名称。
        for (String targetName : targetNames) {
            // 跳过空目标名称。
            if (targetName == null || targetName.isBlank()) {
                continue;
            }

            // 去掉目标名称前后空格。
            String expected = targetName.trim();

            // 支持完整匹配和包含匹配。
            if (member.name().equals(expected) || member.name().contains(expected) || expected.contains(member.name())) {
                return true;
            }
        }

        // 没有命中。
        return false;
    }

    private List<String> memberIds(List<ChatMember> members) {
        // 保存成员 ID。
        List<String> ids = new ArrayList<>();

        // 遍历成员。
        for (ChatMember member : members) {
            // 只加入非空 ID。
            if (member.memberId() != null && !member.memberId().isBlank()) {
                ids.add(member.memberId());
            }
        }

        // 返回成员 ID。
        return ids;
    }

    private List<String> botAppIds(List<ChatMember> members) {
        // 保存机器人 app_id。
        List<String> ids = new ArrayList<>();

        // 遍历机器人成员。
        for (ChatMember member : members) {
            // 只有 cli_ 开头的 ID 才是飞书应用 app_id。
            if (member.memberId() != null && member.memberId().startsWith("cli_")) {
                ids.add(member.memberId());
            }
        }

        // 返回机器人 app_id。
        return ids;
    }

    private String names(List<ChatMember> members) {
        // 没有成员时不追加名称。
        if (members.isEmpty()) {
            return "";
        }

        // 保存名称文本。
        StringBuilder builder = new StringBuilder();

        // 最多展示 8 个。
        int maxCount = Math.min(members.size(), 8);

        // 拼接名称。
        for (int i = 0; i < maxCount; i++) {
            ChatMember member = members.get(i);
            if (builder.length() > 0) {
                builder.append("、");
            }
            builder.append(member.name() == null || member.name().isBlank() ? member.memberId() : member.name());
        }

        // 返回名称摘要。
        return "（" + builder + (members.size() > 8 ? " 等" : "") + "）";
    }
}
