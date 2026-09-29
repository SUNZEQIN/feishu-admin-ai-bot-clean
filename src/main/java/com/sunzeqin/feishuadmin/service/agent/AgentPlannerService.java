package com.sunzeqin.feishuadmin.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.sunzeqin.feishuadmin.config.FeishuProperties;
import com.sunzeqin.feishuadmin.pojo.agent.AgentDecision;
import com.sunzeqin.feishuadmin.pojo.tool.ToolCall;
import com.sunzeqin.feishuadmin.pojo.tool.ToolResult;
import com.sunzeqin.feishuadmin.service.tool.ToolRegistryService;
import com.sunzeqin.feishuadmin.utils.JsonUtils;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agent 规划服务。
 *
 * <p>作用：每一轮根据用户目标和已有工具观察结果，决定下一步调用哪个工具，
 * 或者直接输出最终回复。</p>
 *
 * @author sunzeqin
 */
@Service
public class AgentPlannerService {
    // 当前规划器使用的日志对象。
    private static final Logger log = LoggerFactory.getLogger(AgentPlannerService.class);

    // JSON 工具类，用来解析模型输出。
    private final JsonUtils jsonUtils;

    // 工具注册表，用来把工具说明给模型看。
    private final ToolRegistryService toolRegistry;

    // LangChain4j 聊天模型，未启用时为空。
    private final ChatModel chatModel;

    public AgentPlannerService(FeishuProperties properties, JsonUtils jsonUtils, ToolRegistryService toolRegistry) {
        // 保存 JSON 工具类。
        this.jsonUtils = jsonUtils;

        // 保存工具注册表。
        this.toolRegistry = toolRegistry;

        // 创建模型。
        this.chatModel = buildChatModel(properties);
    }

    public boolean enabled() {
        // 有模型才表示 LLM Agent Loop 可用。
        return chatModel != null;
    }

    public AgentDecision decide(String messageId, int step, String userText, String chatId, List<ToolResult> observations) {
        // LLM 没启用时不应该调用这个方法。
        if (chatModel == null) {
            return new AgentDecision("final_answer", "LLM 未启用", null, "LLM 未启用");
        }

        // 构造 Agent 提示词。
        String prompt = buildPrompt(userText, chatId, observations);

        // 打印规划输入摘要，排查提示词和 observation 数量。
        log.info("AGENT_PLAN_INPUT messageId={} step={} chatId={} observationCount={} userText={}",
                messageId, step, chatId, observations.size(), userText);

        // 调用模型。
        String answer = chatModel.chat(prompt);

        // 打印模型原始输出，方便排查 JSON 格式问题。
        log.info("AGENT_PLAN_RAW messageId={} step={} answer={}", messageId, step, answer);

        // 解析模型决策。
        AgentDecision decision = parseDecision(answer);

        // 打印决策日志，方便排查模型下一步要做什么。
        log.info("AGENT_DECISION messageId={} step={} type={} tool={} reason={}",
                messageId,
                step,
                decision.type(),
                decision.toolCall() == null ? "" : decision.toolCall().name(),
                decision.reason());

        // 返回模型决策。
        return decision;
    }

    private ChatModel buildChatModel(FeishuProperties properties) {
        // 没开启 LLM 时不创建模型。
        if (!properties.isLlmEnabled()) {
            return null;
        }

        // 没配置 API Key 时不创建模型。
        if (properties.getLlmApiKey() == null || properties.getLlmApiKey().isBlank()) {
            log.warn("AGENT_LLM_DISABLED reason=api_key_empty");
            return null;
        }

        // 创建 OpenAI 兼容模型。
        return OpenAiChatModel.builder()
                .baseUrl(properties.getLlmBaseUrl())
                .apiKey(properties.getLlmApiKey())
                .modelName(properties.getLlmModelName())
                .temperature(properties.getLlmTemperature())
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    private String buildPrompt(String userText, String chatId, List<ToolResult> observations) {
        // 把历史工具结果转成 JSON 字符串。
        String observationText = jsonUtils.write(observations);

        // 返回完整提示词。
        return """
                你是飞书管理员 Agent 的规划器。你只能决定下一步，不要一次性假装完成任务。

                你的工作方式：
                1. 根据用户目标和已有工具结果，选择下一步工具；
                2. 每次最多调用一个工具；
                3. 工具执行结果会作为 observations 再返回给你；
                4. 任务完成后输出 final_answer。

                输出必须是 JSON，不要 Markdown，不要解释。

                工具调用格式：
                {
                  "type": "tool_call",
                  "reason": "为什么要调用这个工具",
                  "tool": {
                    "name": "工具名称",
                    "params": {}
                  },
                  "finalReply": ""
                }

                最终回复格式：
                {
                  "type": "final_answer",
                  "reason": "为什么任务完成或无法继续",
                  "tool": null,
                  "finalReply": "回复给用户的中文文本"
                }

                重要规则：
                0. 当前群 chatId 就是本次飞书事件所在群。用户在群聊里说“本群”“当前群”“群里”“这个群”，都默认指当前群 chatId。
                1. 创建群聊前，必须先查询成员。
                2. 要拉用户时，先调用 im.list_chat_members，memberIdType=open_id。
                3. 要拉机器人时，先调用 im.list_chat_members，memberIdType=app_id。
                4. 拿到成员后，再调用 im.create_chat。
                5. im.create_chat 的 userOpenIds 只能放用户 open_id。
                6. im.create_chat 的 botAppIds 只能放机器人 app_id。
                7. 如果用户说“群里的用户和机器人都拉进去”，就需要用户和机器人两类成员。
                8. 如果用户只说“只拉用户”，不要查 app_id。
                9. 如果用户只说“只拉机器人”，不要查 open_id。

                当前群 chatId：%s
                用户目标：%s

                %s

                已有 observations：
                %s
                """.formatted(chatId, userText, toolRegistry.toolDescriptions(), observationText);
    }

    private AgentDecision parseDecision(String answer) {
        // 截取模型输出中的 JSON。
        String json = extractJson(answer);

        // 解析 JSON。
        JsonNode root = jsonUtils.readTree(json);

        // 读取类型。
        String type = root.path("type").asText("final_answer");

        // 读取原因。
        String reason = root.path("reason").asText("");

        // 读取最终回复。
        String finalReply = root.path("finalReply").asText("");

        // 读取工具调用。
        ToolCall toolCall = parseToolCall(root.path("tool"));

        // 返回决策对象。
        return new AgentDecision(type, reason, toolCall, finalReply);
    }

    private ToolCall parseToolCall(JsonNode toolNode) {
        // tool 为空或 null 时返回 null。
        if (toolNode == null || toolNode.isMissingNode() || toolNode.isNull()) {
            return null;
        }

        // 读取工具名。
        String name = toolNode.path("name").asText("");

        // 读取 params。
        Map<String, Object> params = jsonUtils.convertToMap(toolNode.path("params"));

        // 返回工具调用对象。
        return new ToolCall(name, params);
    }

    private String extractJson(String answer) {
        // 空回复时返回最终回复 JSON。
        if (answer == null || answer.isBlank()) {
            return "{\"type\":\"final_answer\",\"reason\":\"模型空回复\",\"tool\":null,\"finalReply\":\"模型没有返回内容\"}";
        }

        // 找到第一个左大括号。
        int start = answer.indexOf('{');

        // 找到最后一个右大括号。
        int end = answer.lastIndexOf('}');

        // 如果存在 JSON 片段，截取 JSON。
        if (start >= 0 && end > start) {
            return answer.substring(start, end + 1);
        }

        // 没有 JSON 时返回原文，让 JSON 解析抛出错误。
        return answer;
    }
}
