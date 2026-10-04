package com.sunzeqin.feishuadmin.service.audit;

import com.sunzeqin.feishuadmin.config.FeishuProperties;
import com.sunzeqin.feishuadmin.pojo.FeishuMessageEvent;
import com.sunzeqin.feishuadmin.pojo.audit.AgentTaskRecord;
import com.sunzeqin.feishuadmin.pojo.audit.AgentTaskStatus;
import com.sunzeqin.feishuadmin.pojo.audit.AgentToolCallRecord;
import com.sunzeqin.feishuadmin.pojo.role.BotRole;
import com.sunzeqin.feishuadmin.pojo.tool.ToolCall;
import com.sunzeqin.feishuadmin.pojo.tool.ToolErrorCode;
import com.sunzeqin.feishuadmin.pojo.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 任务审计服务。
 *
 * <p>作用：把「一次用户消息」变成一条可追溯的任务记录，把「每次工具调用」变成一行明细。</p>
 *
 * <p>重要取舍：审计失败绝不能影响业务，也不能伪造成功。
 * 所以这里所有落库动作都兜住异常，只在日志里留下证据，业务继续按真实结果走。</p>
 *
 * @author sunzeqin
 */
@Service
public class TaskAuditService {

    // 当前服务使用的日志对象。
    private static final Logger log = LoggerFactory.getLogger(TaskAuditService.class);

    // 摘要长度，够区分不同调用，又不至于把表撑大。
    private static final int DIGEST_LENGTH = 32;

    // 审计仓库。
    private final TaskAuditRepository repository;

    // 飞书配置，用来读取审计开关。
    private final FeishuProperties properties;

    public TaskAuditService(TaskAuditRepository repository, FeishuProperties properties) {
        // 保存审计仓库。
        this.repository = repository;

        // 保存配置。
        this.properties = properties;
    }

    /**
     * 开始一个任务。
     *
     * @param event 飞书消息事件
     * @param role  调用者角色
     * @return 任务 ID，审计关闭或落库失败时同样返回 ID（保证调用方能串日志）
     */
    public String startTask(FeishuMessageEvent event, BotRole role) {
        // 生成任务 ID：去掉横线，方便贴进日志和 SQL。
        String taskId = UUID.randomUUID().toString().replace("-", "");

        // 审计关闭时只返回 ID，不落库。
        if (!properties.isAuditEnabled()) {
            return taskId;
        }

        try {
            // 写入任务行，初始状态 RUNNING。
            repository.insertTask(new AgentTaskRecord(
                    taskId,
                    safe(event == null ? null : event.messageId()),
                    safe(event == null ? null : event.chatId()),
                    safe(event == null ? null : event.chatType()),
                    safe(event == null ? null : event.openId()),
                    (role == null ? BotRole.defaultRole() : role).name(),
                    safe(event == null ? null : event.text()),
                    "",
                    AgentTaskStatus.RUNNING.name(),
                    "接收消息",
                    LocalDateTime.now()));

            // 落库成功日志，方便确认审计链在工作。
            log.info("[阶段7 审计] 任务已落库：任务ID={}，角色={}", taskId, role);
        } catch (Exception e) {
            // 审计失败不影响业务，但必须留证据。
            log.warn("[阶段7 审计] 任务落库失败，业务继续：任务ID={}，错误={}", taskId, e.getMessage());
        }

        // 返回任务 ID。
        return taskId;
    }

    /**
     * 结束任务。
     *
     * @param taskId    任务 ID
     * @param status    最终状态
     * @param stage     结束阶段说明
     * @param errorCode 错误码，可为 null
     * @param errorMsg  错误信息，可为 null
     */
    public void finishTask(String taskId, AgentTaskStatus status, String stage, ToolErrorCode errorCode,
            String errorMsg) {
        // 审计关闭时不动数据库。
        if (!properties.isAuditEnabled() || taskId == null || taskId.isBlank()) {
            return;
        }

        try {
            // 写入最终状态。
            repository.finishTask(taskId, status.name(), safe(stage),
                    errorCode == null ? "" : errorCode.name(), safe(errorMsg));
            log.info("[阶段7 审计] 任务已结束：任务ID={}，状态={}，阶段={}，错误码={}",
                    taskId, status, stage, errorCode);
        } catch (Exception e) {
            // 同上：不因审计失败改变业务结果。
            log.warn("[阶段7 审计] 任务结束写入失败：任务ID={}，错误={}", taskId, e.getMessage());
        }
    }

    /**
     * 记录一次工具调用。
     *
     * @param taskId 任务 ID
     * @param stepNo 步骤号
     * @param call   工具调用
     * @param result 工具结果
     * @param costMs 本次调用耗时（毫秒）
     */
    public void recordToolCall(String taskId, int stepNo, ToolCall call, ToolResult result, long costMs) {
        // 审计关闭或参数缺失时直接返回。
        if (!properties.isAuditEnabled() || taskId == null || taskId.isBlank() || call == null || result == null) {
            return;
        }

        try {
            // 写入工具调用明细：只存摘要，不存原文。
            repository.insertToolCall(new AgentToolCallRecord(
                    taskId,
                    stepNo,
                    safe(call.name()),
                    digest(call.params()),
                    digest(result.data()),
                    result.success(),
                    safe(result.errorCode()),
                    safe(result.message()),
                    Math.max(0, costMs),
                    0L));

            // 第一次识别出意图域时补齐任务行。
            String domain = intentDomainOf(call);
            if (!domain.isBlank()) {
                repository.updateIntentDomain(taskId, domain);
            }
        } catch (Exception e) {
            // 审计失败不改变工具结果。
            log.warn("[阶段7 审计] 工具调用落库失败：任务ID={}，步骤={}，工具={}，错误={}",
                    taskId, stepNo, call.name(), e.getMessage());
        }
    }

    /**
     * 推断意图域，写入 `agent_task.intent_domain`。
     *
     * @param call 工具调用
     * @return 意图域，识别不出返回空字符串
     */
    static String intentDomainOf(ToolCall call) {
        // 空调用返回空。
        if (call == null || call.name() == null) {
            return "";
        }

        // 电商工具统一记 ecommerce。
        if (call.name().startsWith("ecommerce.")) {
            return "ecommerce";
        }

        // 用户身份授权相关记 oauth。
        if ("feishu.scope_for_domain".equals(call.name())) {
            return "oauth";
        }

        // Skill + CLI 记具体飞书业务域。
        if ("cli.run_skill".equals(call.name())) {
            Object domain = call.params().get("domain");
            return domain == null ? "" : domain.toString().trim();
        }

        // 其它工具无法判断。
        return "";
    }

    /**
     * 生成内容摘要，用于审计表存指纹而不是原文。
     *
     * @param value 任意对象
     * @return 32 位十六进制摘要，空值返回空字符串
     */
    static String digest(Object value) {
        // 空值没有摘要。
        if (value == null) {
            return "";
        }

        // 转成字符串再摘要。
        String text = value.toString();

        // 空串返回空摘要。
        if (text.isBlank()) {
            return "";
        }

        try {
            // 用 SHA-256，取前 32 位十六进制，避免不同调用在表里长得一样。
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));

            // 转成十六进制字符串。
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }

            // 截断到固定长度。
            return hex.substring(0, DIGEST_LENGTH);
        } catch (Exception e) {
            // 摘要失败时返回空字符串，不让审计影响业务。
            return "";
        }
    }

    private static String safe(String value) {
        // null 统一转成空字符串。
        return value == null ? "" : value;
    }
}
