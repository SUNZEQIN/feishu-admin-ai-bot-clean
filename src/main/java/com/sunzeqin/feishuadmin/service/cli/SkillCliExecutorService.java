package com.sunzeqin.feishuadmin.service.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.sunzeqin.feishuadmin.config.FeishuProperties;
import com.sunzeqin.feishuadmin.pojo.cli.CliCommandResult;
import com.sunzeqin.feishuadmin.pojo.cli.CliStepDecision;
import com.sunzeqin.feishuadmin.utils.JsonUtils;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Skill + CLI 执行服务。
 *
 * <p>作用：固定工具不满足时，读取本地 Skill，先查 lark-cli 帮助，再由 LLM 规划并执行受控 CLI 命令。</p>
 *
 * @author sunzeqin
 */
@Service
public class SkillCliExecutorService {
    // 当前服务使用的日志对象。
    private static final Logger log = LoggerFactory.getLogger(SkillCliExecutorService.class);

    // 飞书配置，包含 CLI 开关、命令路径、大模型配置。
    private final FeishuProperties properties;

    // JSON 工具类，用来解析 CLI 规划器输出。
    private final JsonUtils jsonUtils;

    // CLI 内部规划器使用的大模型。
    private final ChatModel chatModel;

    public SkillCliExecutorService(FeishuProperties properties, JsonUtils jsonUtils) {
        // 保存飞书配置。
        this.properties = properties;

        // 保存 JSON 工具类。
        this.jsonUtils = jsonUtils;

        // 创建 CLI 内部规划器模型。
        this.chatModel = buildChatModel(properties);
    }

    public Map<String, Object> runSkill(String domain, String goal, String sourceChatId) {
        // 校验 CLI 是否启用。
        if (!properties.isCliEnabled()) {
            throw new IllegalStateException("Skill + CLI 未启用");
        }

        // 校验 LLM 是否可用。
        if (chatModel == null) {
            throw new IllegalStateException("Skill + CLI 需要启用 LLM");
        }

        // 规范化业务域。
        String normalizedDomain = normalizeDomain(domain);

        // 校验业务域是否允许。
        ensureDomainAllowed(normalizedDomain);

        // 读取本地 Skill 文档。
        String skillText = readSkill(normalizedDomain);

        // 保存所有 CLI 执行观察结果。
        List<CliCommandResult> observations = new ArrayList<>();

        // 第一步固定先查当前业务域的 help，避免 LLM 直接猜命令。
        CliCommandResult helpResult = executeCommand(List.of(properties.getCliCommand(), normalizedDomain, "--help"));

        // 保存 help 结果。
        observations.add(helpResult);

        // 打印 Skill + CLI 开始日志。
        log.info("SkillCLI开始：业务域={}，目标={}，来源群ID={}，最大步骤数={}",
                normalizedDomain, goal, sourceChatId, properties.getCliMaxSteps());

        // 循环执行 CLI 内部规划。
        for (int step = 1; step <= properties.getCliMaxSteps(); step++) {
            // 构造 CLI 内部规划提示词。
            String prompt = buildPrompt(normalizedDomain, goal, sourceChatId, skillText, observations);

            // 打印规划输入摘要。
            log.info("SkillCLI规划输入：业务域={}，步骤={}，观察结果数量={}",
                    normalizedDomain, step, observations.size());

            // 调用模型规划下一条 CLI 命令或最终回复。
            String answer = chatModel.chat(prompt);

            // 打印模型原始输出。
            log.info("SkillCLI模型原始输出：业务域={}，步骤={}，模型输出={}", normalizedDomain, step, answer);

            // 解析模型决策。
            CliStepDecision decision = parseDecision(answer);

            // 打印模型决策。
            log.info("SkillCLI规划结果：业务域={}，步骤={}，类型={}，原因={}，命令={}，最终回复={}",
                    normalizedDomain, step, decision.type(), decision.reason(), decision.command(), decision.finalReply());

            // 如果模型认为已经完成，就返回成功结果。
            if (!decision.commandDecision()) {
                return Map.of(
                        "domain", normalizedDomain,
                        "goal", goal,
                        "sourceChatId", sourceChatId,
                        "finalReply", decision.finalReply(),
                        "observations", observations
                );
            }

            // 校验命令是否允许执行。
            List<String> command = normalizeCommand(decision.command(), normalizedDomain);

            // 执行 CLI 命令。
            CliCommandResult commandResult = executeCommand(command);

            // 保存执行结果。
            observations.add(commandResult);
        }

        // 超过最大步骤数还没结束，就抛出异常。
        throw new IllegalStateException("Skill + CLI 超过最大步骤数，已停止执行");
    }

    private ChatModel buildChatModel(FeishuProperties properties) {
        // 没开启 LLM 时不创建模型。
        if (!properties.isLlmEnabled()) {
            return null;
        }

        // 没配置 API Key 时不创建模型。
        if (properties.getLlmApiKey() == null || properties.getLlmApiKey().isBlank()) {
            return null;
        }

        // 创建 OpenAI 兼容模型。
        return OpenAiChatModel.builder()
                .baseUrl(properties.getLlmBaseUrl())
                .apiKey(properties.getLlmApiKey())
                .modelName(properties.getLlmModelName())
                .temperature(0.0)
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    private String buildPrompt(String domain, String goal, String sourceChatId,
            String skillText, List<CliCommandResult> observations) {
        // 把 CLI 历史执行结果转成 JSON，方便模型阅读。
        String observationText = jsonUtils.write(observations);

        // 返回 CLI 内部规划提示词。
        return """
                你是飞书 lark-cli Skill 执行器的内部规划器。

                你只能做两件事：
                1. 输出下一条要执行的 lark-cli 命令；
                2. 在任务已经完成或无法继续时输出 final_answer。

                输出必须是 JSON，不要 Markdown，不要解释。

                执行命令格式：
                {
                  "type": "cli_command",
                  "reason": "为什么执行这条命令",
                  "command": ["lark-cli", "im", "--help"],
                  "finalReply": ""
                }

                最终回复格式：
                {
                  "type": "final_answer",
                  "reason": "为什么完成或无法继续",
                  "command": [],
                  "finalReply": "给外层 Agent 的中文结果摘要"
                }

                严格规则：
                1. 优先根据 Skill 和已执行 help 的输出选择真实存在的命令，不要编造子命令。
                2. 每次只输出一条 CLI 命令。
                3. command 必须是字符串数组，不要输出一整段 shell 字符串。
                4. command 第一个元素必须是 lark-cli。
                5. 不要使用管道、重定向、分号、&&、||、反引号、$()。
                6. 当前群、当前会话、这个群、本群都指 sourceChatId。
                7. 如果缺参数，先用 CLI 查询对象，不要向用户索要 open_id、chat_id、app_id。
                8. 写操作执行成功后，必须 final_answer 汇总真实结果。

                业务域：%s
                来源群ID：%s
                用户目标：%s

                Skill 内容：
                %s

                已有 CLI observations：
                %s
                """.formatted(domain, sourceChatId, goal, skillText, observationText);
    }

    private CliStepDecision parseDecision(String answer) {
        // 提取 JSON 文本。
        String json = extractJson(answer);

        // 解析 JSON。
        JsonNode root = jsonUtils.readTree(json);

        // 读取类型。
        String type = root.path("type").asText("final_answer");

        // 读取原因。
        String reason = root.path("reason").asText("");

        // 读取最终回复。
        String finalReply = root.path("finalReply").asText("");

        // 读取命令数组。
        List<String> command = new ArrayList<>();

        // 遍历 command 数组。
        for (JsonNode item : root.path("command")) {
            // 只保留非空字符串。
            String value = item.asText("");
            if (!value.isBlank()) {
                command.add(value);
            }
        }

        // 返回决策对象。
        return new CliStepDecision(type, reason, command, finalReply);
    }

    private List<String> normalizeCommand(List<String> command, String domain) {
        // 命令不能为空。
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("CLI 命令不能为空");
        }

        // 第一个参数必须是 lark-cli，避免执行其它系统命令。
        if (!"lark-cli".equals(command.get(0))) {
            throw new IllegalArgumentException("CLI 命令必须以 lark-cli 开头");
        }

        // 禁止危险 shell 符号。
        for (String part : command) {
            if (part.contains("|") || part.contains(">") || part.contains("<")
                    || part.contains(";") || part.contains("&&") || part.contains("||")
                    || part.contains("`") || part.contains("$(")) {
                throw new IllegalArgumentException("CLI 命令包含不允许的 shell 符号：" + part);
            }
        }

        // 复制一份命令，避免修改不可变列表。
        List<String> normalized = new ArrayList<>(command);

        // 用配置里的真实命令路径替换 lark-cli。
        normalized.set(0, properties.getCliCommand());

        // 校验 lark-cli 的业务域。docs 任务可能需要 im 辅助读取群消息或发链接，所以不能强制等于当前 domain。
        if (normalized.size() > 1) {
            String firstArg = normalized.get(1);
            ensureCommandDomainAllowed(firstArg);
        }

        // 返回规范化命令。
        return normalized;
    }

    private CliCommandResult executeCommand(List<String> command) {
        try {
            // 打印 CLI 执行入参。
            log.info("SkillCLI执行命令：命令={}", command);

            // 创建进程。
            Process process = new ProcessBuilder(command).start();

            // 等待命令执行完成。
            boolean finished = process.waitFor(properties.getCliTimeoutSeconds(), TimeUnit.SECONDS);

            // 超时时销毁进程。
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("CLI 命令执行超时：" + command);
            }

            // 读取标准输出。
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            // 读取错误输出。
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);

            // 读取退出码。
            int exitCode = process.exitValue();

            // 打印 CLI 执行结果。
            log.info("SkillCLI命令结果：命令={}，退出码={}，标准输出={}，错误输出={}",
                    command, exitCode, truncate(stdout), truncate(stderr));

            // 返回 CLI 执行结果。
            return new CliCommandResult(String.join(" ", command), exitCode, stdout, stderr);
        } catch (Exception e) {
            // 打印 CLI 执行异常。
            log.warn("SkillCLI命令异常：命令={}，错误={}", command, e.getMessage());

            // 返回失败结果。
            return new CliCommandResult(String.join(" ", command), -1, "", e.getMessage());
        }
    }

    private String readSkill(String domain) {
        try {
            // 从 resources/skills 读取对应业务域 Skill。
            ClassPathResource resource = new ClassPathResource("skills/" + domain + ".md");

            // 如果没有对应 Skill，就使用通用说明。
            if (!resource.exists()) {
                return "没有找到专用 Skill。请先通过 lark-cli " + domain + " --help 查询能力，再谨慎执行。";
            }

            // 读取 Skill 文件内容。
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Skill 读取失败时抛出业务异常。
            throw new IllegalStateException("读取 Skill 失败：" + domain, e);
        }
    }

    private void ensureDomainAllowed(String domain) {
        // 读取允许的业务域配置。
        String allowed = properties.getCliAllowedDomains();

        // 按逗号分隔判断。
        for (String item : allowed.split(",")) {
            if (domain.equals(item.trim())) {
                return;
            }
        }

        // 不在白名单里就拒绝。
        throw new IllegalArgumentException("CLI 业务域不允许：" + domain);
    }

    private void ensureCommandDomainAllowed(String commandDomain) {
        // shortcut 命令以 + 开头，例如 +chat-members-list，由 lark-cli 自己路由，允许执行。
        if (commandDomain.startsWith("+")) {
            return;
        }

        // schema 是只读查询命令结构，允许执行。
        if ("schema".equals(commandDomain)) {
            return;
        }

        // 普通业务域必须在白名单里。
        ensureDomainAllowed(commandDomain);
    }

    private String normalizeDomain(String domain) {
        // 空业务域不允许。
        if (domain == null || domain.isBlank()) {
            throw new IllegalArgumentException("CLI 业务域不能为空");
        }

        // 转小写并去掉前后空格。
        return domain.trim().toLowerCase(Locale.ROOT);
    }

    private String extractJson(String answer) {
        // 空回复时返回最终回复 JSON。
        if (answer == null || answer.isBlank()) {
            return "{\"type\":\"final_answer\",\"reason\":\"模型空回复\",\"command\":[],\"finalReply\":\"模型没有返回内容\"}";
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

    private String truncate(String text) {
        // 空文本直接返回空字符串。
        if (text == null) {
            return "";
        }

        // 日志最多打印 2000 字符，避免刷屏。
        if (text.length() <= 2000) {
            return text;
        }

        // 返回截断文本。
        return text.substring(0, 2000) + "...";
    }
}
