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

    public AgentDecision decide(String messageId, int step, String userText, String chatId,
            String memoryText, List<ToolResult> observations) {
        // LLM 没启用时不应该调用这个方法。
        if (chatModel == null) {
            return new AgentDecision("final_answer", "LLM 未启用", null, "LLM 未启用");
        }

        // 构造 Agent 提示词。
        String prompt = buildPrompt(userText, chatId, memoryText, observations);

        // 打印规划输入摘要，排查提示词和 observation 数量。
        log.info("智能体规划输入：消息ID={}，步骤={}，会话ID={}，观察结果数量={}，用户文本={}",
                messageId, step, chatId, observations.size(), userText);

        // 调用模型。
        String answer = chatModel.chat(prompt);

        // 打印模型原始输出，方便排查 JSON 格式问题。
        log.info("智能体模型原始输出：消息ID={}，步骤={}，模型输出={}", messageId, step, answer);

        // 解析模型决策。
        AgentDecision decision = parseDecision(answer);

        // 打印决策日志，方便排查模型下一步要做什么。
        log.info("智能体规划结果：消息ID={}，步骤={}，决策类型={}，工具={}，原因={}",
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
            log.warn("智能体大模型未启用：原因=API Key为空");
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

    private String buildPrompt(String userText, String chatId, String memoryText, List<ToolResult> observations) {
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

                技能：创建群聊并拉入成员
                0. 当前群 chatId 就是本次飞书事件所在群。用户在群聊里说“本群”“当前群”“群里”“这个群”，都默认指当前群 chatId。
                1. 只要用户目标是创建群聊、拉人进群、把当前群成员复制到新群，就使用这个技能。
                2. 第一步必须调用 im.list_chat_members，memberIdType=open_id，查询当前群里的用户成员。
                3. 如果目标里包含普通用户，就从 im.list_chat_members 的 members 里按姓名匹配用户，取 memberId 作为 userOpenIds。
                4. 如果用户说“群里的用户”“所有用户”“当前群用户”，就把 members 里 bot=false 的成员都放入 userOpenIds。
                5. 如果目标里包含机器人、助手、应用、bot，必须调用 application.list_installed_apps。
                6. 机器人不能用 open_id 拉入新群，机器人必须用 application.list_installed_apps 返回的 appId，也就是 cli_ 开头的应用 ID。
                7. 如果目标里指定了机器人名称，就用机器人名称和 applications 里的 appName 做包含匹配或近似匹配，匹配到后取 appId 放入 botAppIds。
                8. 如果用户说“群里的机器人”“所有机器人”，但 im.list_chat_members 没返回机器人名称，就从用户原话里的机器人名称匹配 applications；如果原话也没有明确机器人名称，就如实说明无法判断要拉哪些机器人。
                9. im.create_chat 的 userOpenIds 只能放用户 open_id，botAppIds 只能放机器人 app_id，不要混用。
                10. 飞书查询群成员接口不支持 memberIdType=app_id，永远不要传 app_id。
                11. userOpenIds 和 botAppIds 都准备好以后，再调用 im.create_chat。
                12. 不要编造用户 ID、机器人 appId、群 ID。缺少哪类 ID，就继续调用工具查询；工具也查不到时再 final_answer 说明原因。
                13. 除非用户明确要求二次确认，否则创建群聊和拉入成员不需要额外确认。

                固定工具优先规则：
                1. 如果当前可用固定工具能完成用户目标，必须优先使用固定工具。
                2. 当前固定工具主要覆盖：查询群成员、查询企业安装应用、创建群聊。
                3. 如果用户目标涉及固定工具没有覆盖的能力，例如多维表格、云文档、日程、会议、审批、通讯录高级查询，就调用 cli.run_skill。
                4. cli.run_skill 是长尾能力执行器，不是最终回复；它会读取本地 Skill，先查 lark-cli help/schema，再执行 CLI。
                5. 调用 cli.run_skill 时，domain 要按业务选择：多维表格用 base，云文档用 docs，日程用 calendar，会议用 vc，群聊消息用 im，通讯录用 contact，审批用 approval。
                6. 调用 cli.run_skill 时，goal 必须保留用户完整目标，sourceChatId 必须传当前群 chatId。

                当前群 chatId：%s

                当前用户在当前会话里的历史记忆：
                %s

                用户目标：%s

                %s

                已有 observations：
                %s
                """.formatted(chatId, memoryText == null || memoryText.isBlank() ? "无" : memoryText,
                userText, toolRegistry.toolDescriptions(), observationText);
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
