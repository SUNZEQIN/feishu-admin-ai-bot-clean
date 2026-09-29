package com.sunzeqin.feishuadmin.service.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.sunzeqin.feishuadmin.config.FeishuProperties;
import com.sunzeqin.feishuadmin.pojo.cli.CliCommandResult;
import com.sunzeqin.feishuadmin.pojo.cli.CliStepDecision;
import com.sunzeqin.feishuadmin.service.FeishuOpenApiService;
import com.sunzeqin.feishuadmin.utils.JsonUtils;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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

    // 飞书 OpenAPI 服务，用来获取 tenant_access_token 并写入 lark-cli。
    private final FeishuOpenApiService openApiService;

    // CLI 内部规划器使用的大模型。
    private final ChatModel chatModel;

    public SkillCliExecutorService(FeishuProperties properties, JsonUtils jsonUtils,
            FeishuOpenApiService openApiService) {
        // 保存飞书配置。
        this.properties = properties;

        // 保存 JSON 工具类。
        this.jsonUtils = jsonUtils;

        // 保存飞书 OpenAPI 服务。
        this.openApiService = openApiService;

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

        // 读取所有允许业务域的 Skill 文档，让复合任务可以跨域执行。
        String skillText = readAllowedSkills(normalizedDomain);

        // 保存所有 CLI 执行观察结果。
        List<CliCommandResult> observations = new ArrayList<>();

        // 第一步固定先查当前业务域的 help，避免 LLM 直接猜命令。
        CliCommandResult helpResult = executeCommand(List.of(properties.getCliCommand(), normalizedDomain, "--help"));

        // 保存 help 结果。
        observations.add(helpResult);

        // help 都失败时直接停止，避免后面模型在没有 CLI 能力说明的情况下继续猜命令。
        if (helpResult.exitCode() != 0) {
            throw new IllegalStateException("CLI 帮助查询失败，业务域=" + normalizedDomain
                    + "，错误=" + firstNotBlank(helpResult.stderr(), helpResult.stdout()));
        }

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
            List<String> command = normalizeCommand(decision.command());

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
                9. 一个用户目标可以拆成多个 CLI 命令，命令业务域允许在白名单内灵活切换。
                10. 主业务域只表示用户目标的主要方向，不限制后续命令只能调用这个业务域。
                11. 复合任务要按真实步骤跨域执行，例如：
                    - 会议 + IM：先用 im/contact 找人，再用 calendar/vc 创建会议或日程，再用 im 通知参会人。
                    - 文档 + IM：先用 im 读取群消息或找收件人，再用 docs 创建/写入文档，再用 im 发送链接。
                    - 多维表格 + IM：先用 base 处理表格，再用 im 把结果发给群或用户。
                12. 不要因为当前业务域是 docs、vc、calendar、base 就拒绝执行 im/contact 等辅助命令，只要命令业务域在白名单内即可。
                13. 如果要调用其它业务域，但还不知道命令用法，先执行该业务域的 --help 或 schema 查询。
                14. 本项目是管理员机器人项目，所有 lark-cli 业务命令必须使用 --as bot，不要使用 --as user。

                业务域：%s
                允许切换的业务域：%s
                来源群ID：%s
                用户目标：%s

                Skill 内容集合：
                %s

                已有 CLI observations：
                %s
                """.formatted(domain, properties.getCliAllowedDomains(), sourceChatId, goal, skillText, observationText);
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

    private List<String> normalizeCommand(List<String> command) {
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

        // 校验 lark-cli 的业务域。复合任务允许在白名单业务域内灵活切换。
        if (normalized.size() > 1) {
            String firstArg = normalized.get(1);
            ensureCommandDomainAllowed(firstArg);
        }

        // 管理员机器人项目统一使用 bot 身份，避免模型误选 user 导致 token_missing。
        normalizeIdentityAsBot(normalized);

        // 返回规范化命令。
        return normalized;
    }

    private void normalizeIdentityAsBot(List<String> command) {
        // help/config/auth/skills/schema 这类命令不处理身份参数。
        if (!needTenantAccessToken(command)) {
            return;
        }

        // 记录是否已经出现 --as。
        boolean hasAs = false;

        // 遍历命令参数。
        for (int i = 0; i < command.size(); i++) {
            String part = command.get(i);

            // 处理 --as bot 或 --as user。
            if ("--as".equals(part)) {
                hasAs = true;
                if (i + 1 < command.size()) {
                    String oldValue = command.get(i + 1);
                    if (!"bot".equals(oldValue)) {
                        log.warn("SkillCLI身份参数已修正：原身份={}，新身份=bot，原因=管理员机器人项目不使用user身份", oldValue);
                        command.set(i + 1, "bot");
                    }
                } else {
                    command.add("bot");
                }
            }

            // 处理 --as=user 这种写法。
            if (part.startsWith("--as=")) {
                hasAs = true;
                if (!"--as=bot".equals(part)) {
                    log.warn("SkillCLI身份参数已修正：原参数={}，新参数=--as=bot，原因=管理员机器人项目不使用user身份", part);
                    command.set(i, "--as=bot");
                }
            }
        }

        // 如果业务命令没带 --as，就默认补 bot。
        if (!hasAs) {
            command.add("--as");
            command.add("bot");
            log.info("SkillCLI身份参数已补充：身份=bot，原因=管理员机器人项目默认使用bot身份");
        }
    }

    private CliCommandResult executeCommand(List<String> command) {
        try {
            // 执行业务 CLI 前，先把最新 tenant_access_token 写入 lark-cli，避免 bot token_missing。
            prepareTenantAccessTokenForCli(command);

            // 打印 CLI 执行入参。
            log.info("SkillCLI执行命令：命令={}", command);

            // 创建进程。
            Process process = new ProcessBuilder(command).start();

            // 异步读取标准输出，避免输出较多时进程缓冲区写满导致卡死。
            CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(
                    () -> readStream(process.getInputStream()));

            // 异步读取错误输出，避免错误信息较多时进程缓冲区写满导致卡死。
            CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(
                    () -> readStream(process.getErrorStream()));

            // 等待命令执行完成。
            boolean finished = process.waitFor(properties.getCliTimeoutSeconds(), TimeUnit.SECONDS);

            // 超时时销毁进程。
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("CLI 命令执行超时：" + command);
            }

            // 读取标准输出。
            String stdout = stdoutFuture.get(5, TimeUnit.SECONDS);

            // 读取错误输出。
            String stderr = stderrFuture.get(5, TimeUnit.SECONDS);

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

    private void prepareTenantAccessTokenForCli(List<String> command) {
        // 非 lark-cli 命令不处理；正常情况下不会出现。
        if (command == null || command.isEmpty()) {
            return;
        }

        // 只处理配置里的 lark-cli 命令。
        if (!properties.getCliCommand().equals(command.get(0))) {
            return;
        }

        // config/auth/help/schema/skills 这类命令不需要 bot token，避免无意义写入。
        if (!needTenantAccessToken(command)) {
            return;
        }

        // appId 不能为空，否则 lark-cli 不知道 token 属于哪个应用。
        if (properties.getAppId() == null || properties.getAppId().isBlank()) {
            throw new IllegalStateException("写入 lark-cli tenant_access_token 失败：FEISHU_APP_ID 为空");
        }

        // 从 Java OpenAPI 服务拿最新 tenant_access_token，不打印 token 明文。
        String token = openApiService.tenantAccessTokenForCli();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("写入 lark-cli tenant_access_token 失败：tenant_access_token 为空");
        }

        // 把 token 写入 lark-cli 的本地 token store。
        setTenantAccessToken(token);
    }

    private boolean needTenantAccessToken(List<String> command) {
        // 只有真实业务域命令才需要 token。
        if (command.size() < 2) {
            return false;
        }

        // 读取第二个参数。
        String domain = command.get(1);

        // 帮助和配置类命令不需要提前写 token。
        if ("--help".equals(domain) || "-h".equals(domain)
                || "config".equals(domain)
                || "auth".equals(domain)
                || "skills".equals(domain)
                || "schema".equals(domain)) {
            return false;
        }

        // 如果只是查询某个业务域 help，也不需要提前写 token。
        if (command.contains("--help") || command.contains("-h")) {
            return false;
        }

        // 其它 lark-cli 业务命令需要 token。
        return true;
    }

    private void setTenantAccessToken(String token) {
        // 组装写入 token 的 lark-cli 命令，token 通过 stdin 传入，不出现在命令行和日志里。
        List<String> command = List.of(
                properties.getCliCommand(),
                "config",
                "tenant-access-token",
                "set",
                "--app-id",
                properties.getAppId()
        );

        try {
            // 打印写入动作，不打印 token 明文。
            log.info("SkillCLI写入tenant_access_token：appId={}，命令={}", properties.getAppId(), command);

            // 创建写入 token 的进程。
            Process process = new ProcessBuilder(command).start();

            // 通过 stdin 写入 token。
            try (OutputStream outputStream = process.getOutputStream()) {
                outputStream.write(token.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
            }

            // 异步读取标准输出。
            CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(
                    () -> readStream(process.getInputStream()));

            // 异步读取错误输出。
            CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(
                    () -> readStream(process.getErrorStream()));

            // 等待写入完成。
            boolean finished = process.waitFor(properties.getCliTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("写入 lark-cli tenant_access_token 超时");
            }

            // 读取退出码和输出。
            int exitCode = process.exitValue();
            String stdout = stdoutFuture.get(5, TimeUnit.SECONDS);
            String stderr = stderrFuture.get(5, TimeUnit.SECONDS);

            // 写入失败时抛出异常，让上层返回真实错误。
            if (exitCode != 0) {
                throw new IllegalStateException("写入 lark-cli tenant_access_token 失败，退出码="
                        + exitCode + "，标准输出=" + truncate(stdout) + "，错误输出=" + truncate(stderr));
            }

            // 打印写入成功日志，不打印 token。
            log.info("SkillCLI写入tenant_access_token成功：appId={}，退出码={}", properties.getAppId(), exitCode);
        } catch (Exception e) {
            // 写入 token 失败时抛出异常。
            throw new IllegalStateException("写入 lark-cli tenant_access_token 异常：" + e.getMessage(), e);
        }
    }

    private String readStream(InputStream inputStream) {
        try {
            // 读取进程输出流并按 UTF-8 转成字符串。
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 输出流读取失败时返回错误文本，方便日志继续展示问题。
            return "读取进程输出失败：" + e.getMessage();
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

    private String readAllowedSkills(String primaryDomain) {
        // 保存合并后的 Skill 文档。
        StringBuilder builder = new StringBuilder();

        // 先放主业务域 Skill，方便模型优先理解当前任务方向。
        appendSkill(builder, primaryDomain);

        // 再放其它白名单业务域 Skill，方便模型处理跨域步骤。
        for (String item : properties.getCliAllowedDomains().split(",")) {
            String domain = item.trim().toLowerCase(Locale.ROOT);
            if (!domain.isBlank() && !domain.equals(primaryDomain)) {
                appendSkill(builder, domain);
            }
        }

        // 返回所有允许业务域的 Skill 文档。
        return builder.toString();
    }

    private void appendSkill(StringBuilder builder, String domain) {
        // 追加 Skill 分隔标题，避免多个文档混在一起看不清。
        builder.append("\n\n================ Skill Domain: ")
                .append(domain)
                .append(" ================\n");

        // 追加具体 Skill 内容。
        builder.append(readSkill(domain));
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

    private String firstNotBlank(String first, String second) {
        // 优先返回第一个非空文本。
        if (first != null && !first.isBlank()) {
            return truncate(first);
        }

        // 第一个为空时返回第二个非空文本。
        if (second != null && !second.isBlank()) {
            return truncate(second);
        }

        // 都为空时返回统一提示。
        return "无输出";
    }
}
