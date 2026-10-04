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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 任务审计服务测试（产品规则 O-01 / O-02 / O-05）。
 *
 * <p>用假仓库捕获写入，验证「任务一行、工具调用一行、拦截带错误码」，
 * 以及审计失败不允许影响业务。</p>
 *
 * @author sunzeqin
 */
class TaskAuditServiceTest {

    @Test
    void startsTaskAsRunningWithRoleAndInput() {
        // 准备假仓库与配置。
        CapturingAuditRepository repository = new CapturingAuditRepository();
        TaskAuditService service = new TaskAuditService(repository, new FeishuProperties());

        // 开始任务。
        String taskId = service.startTask(event("查一下低库存商品"), BotRole.L2);

        // 任务 ID 应为 32 位十六进制。
        assertNotNull(taskId);
        assertEquals(32, taskId.length());

        // 任务行状态必须是 RUNNING，角色与原始输入都要落库。
        assertEquals(1, repository.tasks.size());
        AgentTaskRecord task = repository.tasks.get(0);
        assertEquals(AgentTaskStatus.RUNNING.name(), task.status());
        assertEquals("L2", task.userRole());
        assertEquals("查一下低库存商品", task.rawInput());
        assertEquals("om_1", task.messageId());
        assertEquals("oc_1", task.chatId());
    }

    @Test
    void finishesTaskWithStatusAndPermissionDeniedCode() {
        // 准备服务。
        CapturingAuditRepository repository = new CapturingAuditRepository();
        TaskAuditService service = new TaskAuditService(repository, new FeishuProperties());

        // 开始并结束任务，模拟一次越权拦截。
        String taskId = service.startTask(event("查张伟的订单"), BotRole.L1);
        service.finishTask(taskId, AgentTaskStatus.FAILED, "工具失败", ToolErrorCode.PERMISSION_DENIED,
                "角色 L1 无权调用客户明细工具");

        // 结束写入必须有错误码，供管理员按错误码排查（EC-43）。
        assertEquals(1, repository.finishes.size());
        assertEquals(taskId + "|FAILED|工具失败|PERMISSION_DENIED",
                repository.finishes.get(0));
    }

    @Test
    void recordsToolCallWithDigestsAndIntentDomain() {
        // 准备服务。
        CapturingAuditRepository repository = new CapturingAuditRepository();
        TaskAuditService service = new TaskAuditService(repository, new FeishuProperties());

        // 开始任务。
        String taskId = service.startTask(event("销售额最高的商品"), BotRole.L1);

        // 记录一次成功的电商工具调用。
        ToolCall call = new ToolCall("ecommerce.call_tool",
                Map.of("toolName", "ecommerce.query_top_products", "senderOpenId", "ou_l1"));
        service.recordToolCall(taskId, 1, call,
                ToolResult.success("ecommerce.call_tool", "调用完成", Map.of("rows", List.of(Map.of("name", "A")))),
                1234L);

        // 工具明细一行，且只存摘要不存原文。
        assertEquals(1, repository.toolCalls.size());
        AgentToolCallRecord record = repository.toolCalls.get(0);
        assertEquals(1, record.stepNo());
        assertEquals("ecommerce.call_tool", record.toolName());
        assertTrue(record.success());
        assertEquals(1234L, record.costMs());
        assertEquals(32, record.argsDigest().length());
        assertEquals(32, record.resultDigest().length());
        assertFalse(record.argsDigest().contains("ou_l1"));

        // 意图域只在第一次识别时补齐。
        assertEquals(List.of("ecommerce"), repository.intentDomains);
    }

    @Test
    void recordsDeniedCallWithErrorCode() {
        // 准备服务。
        CapturingAuditRepository repository = new CapturingAuditRepository();
        TaskAuditService service = new TaskAuditService(repository, new FeishuProperties());

        // 开始任务并记录一次被拒调用。
        String taskId = service.startTask(event("查张伟的订单"), BotRole.L1);
        service.recordToolCall(taskId, 1,
                new ToolCall("ecommerce.call_tool", Map.of("toolName", "ecommerce.query_customer_orders")),
                ToolResult.failed("ecommerce.call_tool", "这条数据需要运营负责人权限。", "PERMISSION_DENIED"),
                8L);

        // 拒绝也要落库，并带错误码。
        assertEquals(1, repository.toolCalls.size());
        AgentToolCallRecord record = repository.toolCalls.get(0);
        assertFalse(record.success());
        assertEquals("PERMISSION_DENIED", record.errorCode());
    }

    @Test
    void doesNotTouchDatabaseWhenAuditDisabled() {
        // 关闭审计。
        FeishuProperties properties = new FeishuProperties();
        properties.setAuditEnabled(false);

        CapturingAuditRepository repository = new CapturingAuditRepository();
        TaskAuditService service = new TaskAuditService(repository, properties);

        // 全流程都不应落库。
        String taskId = service.startTask(event("查一下低库存商品"), BotRole.L1);
        service.recordToolCall(taskId, 1, new ToolCall("ecommerce.list_tools", Map.of()),
                ToolResult.success("ecommerce.list_tools", "ok", Map.of()), 1L);
        service.finishTask(taskId, AgentTaskStatus.SUCCESS, "生成最终回复", null, "");

        assertTrue(repository.tasks.isEmpty());
        assertTrue(repository.toolCalls.isEmpty());
        assertTrue(repository.finishes.isEmpty());
    }

    @Test
    void keepsBusinessRunningWhenRepositoryFails() {
        // 仓库直接抛异常，模拟数据库不可用。
        TaskAuditRepository broken = new TaskAuditRepository() {
            @Override
            public void insertTask(AgentTaskRecord record) {
                throw new IllegalStateException("db down");
            }

            @Override
            public void finishTask(String taskId, String status, String stage, String errorCode, String errorMsg) {
                throw new IllegalStateException("db down");
            }

            @Override
            public void updateIntentDomain(String taskId, String intentDomain) {
                throw new IllegalStateException("db down");
            }

            @Override
            public void insertToolCall(AgentToolCallRecord record) {
                throw new IllegalStateException("db down");
            }
        };

        TaskAuditService service = new TaskAuditService(broken, new FeishuProperties());

        // 三个动作都不允许把异常抛给业务方。
        String taskId = service.startTask(event("查一下低库存商品"), BotRole.L1);
        service.recordToolCall(taskId, 1, new ToolCall("ecommerce.list_tools", Map.of()),
                ToolResult.success("ecommerce.list_tools", "ok", Map.of()), 1L);
        service.finishTask(taskId, AgentTaskStatus.SUCCESS, "生成最终回复", null, "");

        // 任务 ID 仍然返回，保证日志能串联。
        assertNotNull(taskId);
    }

    @Test
    void derivesIntentDomainFromToolCall() {
        // 电商工具。
        assertEquals("ecommerce", TaskAuditService.intentDomainOf(
                new ToolCall("ecommerce.call_tool", Map.of("toolName", "ecommerce.query_top_products"))));

        // Skill + CLI 记具体飞书业务域。
        assertEquals("docs", TaskAuditService.intentDomainOf(
                new ToolCall("cli.run_skill", Map.of("domain", "docs"))));

        // 用户身份授权。
        assertEquals("oauth", TaskAuditService.intentDomainOf(
                new ToolCall("feishu.scope_for_domain", Map.of("domain", "docs"))));

        // 未知工具不猜。
        assertEquals("", TaskAuditService.intentDomainOf(new ToolCall("unknown.tool", Map.of())));
    }

    @Test
    void digestIsStableAndHidesRawContent() {
        // 同样的输入摘要一致。
        assertEquals(TaskAuditService.digest(Map.of("a", 1)), TaskAuditService.digest(Map.of("a", 1)));

        // 不同输入摘要不同。
        assertFalse(TaskAuditService.digest(Map.of("a", 1)).equals(TaskAuditService.digest(Map.of("a", 2))));

        // 摘要里不能出现原文。
        String digest = TaskAuditService.digest(Map.of("customerName", "张伟"));
        assertEquals(32, digest.length());
        assertFalse(digest.contains("张伟"));

        // 空值返回空字符串。
        assertEquals("", TaskAuditService.digest(null));
        assertEquals("", TaskAuditService.digest(""));
    }

    private FeishuMessageEvent event(String text) {
        // 构造一条群聊消息事件。
        return new FeishuMessageEvent("cli_a", "ev_1", "im.message.receive_v1", "ou_l1", "u_1", "user",
                "oc_1", "group", "om_1", "text", text, List.of());
    }

    /**
     * 假仓库：把写入内容捕获下来，便于断言，不依赖数据库。
     */
    private static final class CapturingAuditRepository implements TaskAuditRepository {

        // 捕获的任务行。
        private final List<AgentTaskRecord> tasks = new ArrayList<>();

        // 捕获的工具调用明细。
        private final List<AgentToolCallRecord> toolCalls = new ArrayList<>();

        // 捕获的结束调用，格式 taskId|status|stage|errorCode。
        private final List<String> finishes = new ArrayList<>();

        // 捕获的意图域写入。
        private final List<String> intentDomains = new ArrayList<>();

        @Override
        public void insertTask(AgentTaskRecord record) {
            tasks.add(record);
        }

        @Override
        public void finishTask(String taskId, String status, String stage, String errorCode, String errorMsg) {
            finishes.add(taskId + "|" + status + "|" + stage + "|" + errorCode);
        }

        @Override
        public void updateIntentDomain(String taskId, String intentDomain) {
            intentDomains.add(intentDomain);
        }

        @Override
        public void insertToolCall(AgentToolCallRecord record) {
            toolCalls.add(record);
        }
    }
}
