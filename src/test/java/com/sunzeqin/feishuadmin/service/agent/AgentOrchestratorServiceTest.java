package com.sunzeqin.feishuadmin.service.agent;

import com.sunzeqin.feishuadmin.config.FeishuProperties;
import com.sunzeqin.feishuadmin.pojo.FeishuMessageEvent;
import com.sunzeqin.feishuadmin.pojo.agent.AgentDecision;
import com.sunzeqin.feishuadmin.pojo.agent.AgentRunResult;
import com.sunzeqin.feishuadmin.pojo.audit.AgentTaskRecord;
import com.sunzeqin.feishuadmin.pojo.audit.AgentToolCallRecord;
import com.sunzeqin.feishuadmin.pojo.role.BotRole;
import com.sunzeqin.feishuadmin.pojo.tool.ToolCall;
import com.sunzeqin.feishuadmin.pojo.tool.ToolResult;
import com.sunzeqin.feishuadmin.service.ConversationMemoryService;
import com.sunzeqin.feishuadmin.service.audit.TaskAuditRepository;
import com.sunzeqin.feishuadmin.service.audit.TaskAuditService;
import com.sunzeqin.feishuadmin.service.role.BotRoleResolver;
import com.sunzeqin.feishuadmin.service.tool.ToolRegistryService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Agent 编排审计与回复规范测试（产品规则 O-02 / C-08 / C-06）。
 *
 * <p>验证的是"状态不会撒谎"：成功记 SUCCESS、越权拦标记 FAILED 且带 PERMISSION_DENIED、
 * 前一步成功后面失败记 PARTIAL、高风险操作等确认记 WAITING_CONFIRM。</p>
 *
 * @author sunzeqin
 */
class AgentOrchestratorServiceTest {

    // 捕获审计写入的假仓库。
    private final CapturingAuditRepository repository = new CapturingAuditRepository();

    // 飞书配置。
    private final FeishuProperties properties = new FeishuProperties();

    @Test
    void recordsSuccessAndAppendsDataSourceNotice() {
        // 第一步取电商数据，第二步给出最终回复。
        AgentPlannerService planner = plannerWith(
                toolCall("ecommerce.call_tool", Map.of("toolName", "ecommerce.query_top_products")),
                finalAnswer("销售额最高的是洗衣液，卖了 320 件。"));

        // 电商调用成功。
        ToolRegistryService registry = registryReturning(
                ToolResult.success("ecommerce.call_tool", "调用完成", Map.of("rows", "x")));

        AgentRunResult result = orchestrator(planner, registry, BotRole.L1).run(event());

        // 回复里必须标注数据来源（C-08）。
        assertTrue(result.success());
        assertTrue(result.reply().contains("数据来源：测试数据"));

        // 任务记成功，意图域为 ecommerce，工具明细一行。
        assertEquals("SUCCESS", repository.lastStatus());
        assertEquals("ecommerce", repository.lastIntentDomain());
        assertEquals(1, repository.toolCalls.size());
        assertTrue(repository.toolCalls.get(0).success());
    }

    @Test
    void recordsPermissionDeniedAsFailedTaskWithoutNotice() {
        // 第一步就被权限拦截。
        AgentPlannerService planner = plannerWith(
                toolCall("ecommerce.call_tool", Map.of("toolName", "ecommerce.query_customer_orders")));

        ToolRegistryService registry = registryReturning(ToolResult.failed("ecommerce.call_tool",
                "这条数据需要运营负责人权限。需要我帮你把开通需求发给管理员吗？", "PERMISSION_DENIED"));

        AgentRunResult result = orchestrator(planner, registry, BotRole.L1).run(event());

        // 用户看到权限引导话术。
        assertFalse(result.success());
        assertTrue(result.reply().contains("运营负责人权限"));

        // 审计必须记 FAILED + PERMISSION_DENIED（EC-43）。
        assertEquals("FAILED", repository.lastStatus());
        assertEquals("PERMISSION_DENIED", repository.lastErrorCode());

        // 没有拿到数据，不能标注数据来源。
        assertFalse(result.reply().contains("数据来源"));
    }

    @Test
    void recordsPartialWhenEarlierStepSucceeded() {
        // 第一步飞书侧成功，第二步电商侧失败 → 部分成功。
        AgentPlannerService planner = plannerWith(
                toolCall("cli.run_skill", Map.of("domain", "docs", "goal", "建文档")),
                toolCall("ecommerce.call_tool", Map.of("toolName", "ecommerce.query_refund_top_products")));

        ToolRegistryService registry = mock(ToolRegistryService.class);
        when(registry.execute(any()))
                .thenReturn(ToolResult.success("cli.run_skill", "执行完成", Map.of("docUrl", "https://x")))
                .thenReturn(ToolResult.failed("ecommerce.call_tool", "电商 MCP 连接失败"));

        AgentRunResult result = orchestrator(planner, registry, BotRole.L1).run(event());

        // 状态必须是 PARTIAL，而不是 FAILED 或 SUCCESS。
        assertFalse(result.success());
        assertEquals("PARTIAL", repository.lastStatus());

        // 两次调用都落库。
        assertEquals(2, repository.toolCalls.size());
        assertEquals("docs", repository.lastIntentDomain());
    }

    @Test
    void recordsWaitingConfirmWhenDestructiveCommandBlocked() {
        // 工具返回 needConfirm，模型随后输出确认提示。
        AgentPlannerService planner = plannerWith(
                toolCall("cli.run_skill", Map.of("domain", "drive", "goal", "删除文件")),
                finalAnswer("⚠️ 这是一次高风险操作，需要你确认后才会执行。"));

        ToolRegistryService registry = registryReturning(ToolResult.success("cli.run_skill", "等待确认",
                Map.of("needConfirm", true, "finalReply", "⚠️ 这是一次高风险操作，需要你确认后才会执行。")));

        AgentRunResult result = orchestrator(planner, registry, BotRole.L1).run(event());

        // 任务状态必须是 WAITING_CONFIRM，不能记成成功（O-02）。
        assertTrue(result.success());
        assertEquals("WAITING_CONFIRM", repository.lastStatus());
        assertEquals("等待用户确认", repository.lastStage());
    }

    @Test
    void recordsFailedWhenPlannerDisabled() {
        // 规划器未启用时也要留下任务结果，不能停在 RUNNING。
        AgentPlannerService planner = mock(AgentPlannerService.class);
        when(planner.enabled()).thenReturn(false);

        AgentRunResult result = orchestrator(planner, mock(ToolRegistryService.class), BotRole.L1).run(event());

        assertFalse(result.success());
        assertEquals("FAILED", repository.lastStatus());
        assertEquals("规划器未启用", repository.lastStage());
    }

    @Test
    void recordsFailedWhenAgentGivesNoTool() {
        // 模型说调用工具但没给参数。
        AgentPlannerService planner = plannerWith(
                new AgentDecision("tool_call", "缺少参数", null, ""));

        AgentRunResult result = orchestrator(planner, mock(ToolRegistryService.class), BotRole.L1).run(event());

        assertFalse(result.success());
        assertEquals("FAILED", repository.lastStatus());
        assertEquals("缺少工具调用参数", repository.lastStage());
    }

    @Test
    void terminalToolReplyEndsTheTurnWithoutExtraPlannerCalls() {
        // 工具自带终态回复（Java 已经算完的结论），外层必须直接采用并结束这一轮。
        AgentPlannerService planner = plannerWith(
                toolCall("cli.run_skill", Map.of("domain", "base", "goal", "删除我名下最早的10个多维表格")),
                finalAnswer("模型自己的解释：步骤太多了"));

        ToolRegistryService registry = registryReturning(ToolResult.success("cli.run_skill", "执行完成",
                Map.of("terminal", true, "finalReply", "已删除 2/10 个多维表格：\n✅ A\n❌ B：not found")));

        AgentRunResult result = orchestrator(planner, registry, BotRole.L1).run(event());

        // 结论必须来自工具，不能被模型的二次解释覆盖成「步骤过多」。
        assertTrue(result.success());
        assertTrue(result.reply().contains("已删除 2/10"),
                "终态回复要原样采用，否则用户看到的是模型编的解释而不是真实结果");
        assertEquals("SUCCESS", repository.lastStatus());
        assertEquals(1, repository.toolCalls.size(), "终态回复之后不应再调用工具");
    }

    private AgentOrchestratorService orchestrator(AgentPlannerService planner, ToolRegistryService registry,
            BotRole role) {
        // 用假仓库与真实的审计、标注服务组装编排器。
        return new AgentOrchestratorService(planner, registry, mock(ConversationMemoryService.class),
                new BotRoleResolver(openId -> role == null ? null : role.name()),
                new TaskAuditService(repository, properties),
                new DataSourceNoticeService(properties));
    }

    private AgentPlannerService plannerWith(AgentDecision... decisions) {
        // 按步骤号返回预设决策。
        AgentPlannerService planner = mock(AgentPlannerService.class);
        when(planner.enabled()).thenReturn(true);

        // 兜底：超出预设步骤时统一返回最终答复，避免测试跑到最大步数。
        // 注意 matcher 用 any() 而不是 anyString()：记忆文本可能是 null，anyString() 不匹配 null。
        when(planner.decide(any(), anyInt(), any(), any(), any(), anyList()))
                .thenReturn(finalAnswer("结束"));

        // 具体步骤的决策最后注册，覆盖兜底桩。
        for (int i = 0; i < decisions.length; i++) {
            when(planner.decide(any(), eq(i + 1), any(), any(), any(), anyList()))
                    .thenReturn(decisions[i]);
        }
        return planner;
    }

    private ToolRegistryService registryReturning(ToolResult result) {
        // 所有工具调用都返回同一个结果。
        ToolRegistryService registry = mock(ToolRegistryService.class);
        when(registry.execute(any())).thenReturn(result);
        return registry;
    }

    private AgentDecision toolCall(String tool, Map<String, Object> params) {
        // 构造工具调用决策。
        return new AgentDecision("tool_call", "需要数据", new ToolCall(tool, params), "");
    }

    private AgentDecision finalAnswer(String reply) {
        // 构造最终回复决策。
        return new AgentDecision("final_answer", "给出结论", null, reply);
    }

    private FeishuMessageEvent event() {
        // 构造一条单聊消息事件。
        return new FeishuMessageEvent("cli_a", "ev_1", "im.message.receive_v1", "ou_l1", "u_1", "user",
                "oc_1", "p2p", "om_1", "text", "查一下低库存商品", List.of());
    }

    /**
     * 假审计仓库：只保留最近一次结束状态与全部工具明细，方便断言。
     */
    private static final class CapturingAuditRepository implements TaskAuditRepository {

        // 工具调用明细。
        private final List<AgentToolCallRecord> toolCalls = new ArrayList<>();

        // 最近一次结束状态。
        private String lastStatus = "";

        // 最近一次结束阶段。
        private String lastStage = "";

        // 最近一次错误码。
        private String lastErrorCode = "";

        // 最近一次写入的意图域。
        private String lastIntentDomain = "";

        @Override
        public void insertTask(AgentTaskRecord record) {
            // 本测试不校验建行，忽略。
        }

        @Override
        public void finishTask(String taskId, String status, String stage, String errorCode, String errorMsg) {
            this.lastStatus = status;
            this.lastStage = stage;
            this.lastErrorCode = errorCode;
        }

        @Override
        public void updateIntentDomain(String taskId, String intentDomain) {
            // 与生产 SQL 语义保持一致：只在当前为空时写入，第一次识别结果生效。
            if (lastIntentDomain.isEmpty()) {
                this.lastIntentDomain = intentDomain;
            }
        }

        @Override
        public void insertToolCall(AgentToolCallRecord record) {
            toolCalls.add(record);
        }

        private String lastStatus() {
            return lastStatus;
        }

        private String lastStage() {
            return lastStage;
        }

        private String lastErrorCode() {
            return lastErrorCode;
        }

        private String lastIntentDomain() {
            return lastIntentDomain;
        }
    }
}
