package com.sunzeqin.feishuadmin.service.tool;

import com.sunzeqin.feishuadmin.config.FeishuProperties;
import com.sunzeqin.feishuadmin.pojo.tool.ToolCall;
import com.sunzeqin.feishuadmin.pojo.tool.ToolResult;
import com.sunzeqin.feishuadmin.service.EcommerceMcpClientService;
import com.sunzeqin.feishuadmin.service.FeishuUserScopeMappingService;
import com.sunzeqin.feishuadmin.service.cli.SkillCliExecutorService;
import com.sunzeqin.feishuadmin.service.role.BotRoleResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工具注册表护栏测试。
 *
 * <p>覆盖三个关键护栏：未知工具拒绝、权限拒绝、工具超时。</p>
 *
 * @author sunzeqin
 */
class ToolRegistryServiceTest {

    // 保存当前用例创建的工具注册表，方便用例结束后关闭线程池。
    private ToolRegistryService registry;

    @AfterEach
    void tearDown() {
        // 关闭工具执行线程池，避免用例之间线程泄漏。
        if (registry != null) {
            registry.shutdownToolExecutor();
        }
    }

    @Test
    void rejectsUnknownTool() {
        FeishuProperties properties = new FeishuProperties();
        registry = newRegistry(mock(SkillCliExecutorService.class), properties);

        ToolResult result = registry.execute(new ToolCall("feishu.delete_everything", Map.of()));

        assertFalse(result.success());
        assertTrue(result.message().contains("未知工具"));
    }

    @Test
    void rejectsCallerOutsideAllowlist() {
        FeishuProperties properties = new FeishuProperties();
        properties.setToolAllowedOpenIds("ou_allowed");

        SkillCliExecutorService cli = mock(SkillCliExecutorService.class);
        registry = newRegistry(cli, properties);

        ToolResult result = registry.execute(new ToolCall("cli.run_skill", Map.of(
                "domain", "im",
                "goal", "查一下本群成员",
                "sourceChatId", "oc_1",
                "senderOpenId", "ou_other")));

        assertFalse(result.success());
        assertTrue(result.message().contains("权限"));
    }

    @Test
    void allowsCallerInsideAllowlist() {
        FeishuProperties properties = new FeishuProperties();
        properties.setToolAllowedOpenIds("ou_allowed");

        SkillCliExecutorService cli = mock(SkillCliExecutorService.class);
        when(cli.runSkill(any(), any(), any(), any(), any(), any())).thenReturn(Map.of("finalReply", "ok"));
        registry = newRegistry(cli, properties);

        ToolResult result = registry.execute(new ToolCall("cli.run_skill", Map.of(
                "domain", "im",
                "goal", "查一下本群成员",
                "sourceChatId", "oc_1",
                "senderOpenId", "ou_allowed")));

        assertTrue(result.success());
    }

    @Test
    void timesOutWhenToolIsSlow() throws Exception {
        FeishuProperties properties = new FeishuProperties();
        properties.setToolTimeoutSeconds(1);

        SkillCliExecutorService cli = mock(SkillCliExecutorService.class);
        when(cli.runSkill(any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            // 模拟卡住的工具：睡 5 秒，超过 1 秒超时。
            Thread.sleep(5000);
            return Map.of();
        });
        registry = newRegistry(cli, properties);

        ToolResult result = registry.execute(new ToolCall("cli.run_skill", Map.of(
                "domain", "im",
                "goal", "查一下本群成员",
                "sourceChatId", "oc_1")));

        assertFalse(result.success());
        assertTrue(result.message().contains("超时"));
    }

    @Test
    void toolDescriptionsMatchDispatchWhitelist() {
        FeishuProperties properties = new FeishuProperties();
        registry = newRegistry(mock(SkillCliExecutorService.class), properties);

        // 启动自检不抛异常，并保证四个工具都在说明文本里。
        registry.verifyToolCatalog();
        String descriptions = registry.toolDescriptions();

        assertTrue(descriptions.contains("cli.run_skill"));
        assertTrue(descriptions.contains("feishu.scope_for_domain"));
        assertTrue(descriptions.contains("ecommerce.list_tools"));
        assertTrue(descriptions.contains("ecommerce.call_tool"));
        assertEquals(4, descriptions.lines().filter(line -> line.matches("\\s*\\d+\\.\\s*[a-z][a-z0-9_.]+\\s*")).count());
    }

    @Test
    void deniesCustomerOrdersForUnregisteredL1Caller() {
        // EC-11：未登记用户（L1）通过注册表查客户明细，必须在调用电商服务之前被拦下。
        FeishuProperties properties = new FeishuProperties();
        EcommerceMcpClientService ecommerce = mock(EcommerceMcpClientService.class);
        registry = newRegistry(mock(SkillCliExecutorService.class), ecommerce, new BotRoleResolver(openId -> null),
                properties);

        ToolResult result = registry.execute(new ToolCall("ecommerce.call_tool", Map.of(
                "toolName", "ecommerce.query_customer_orders",
                "senderOpenId", "ou_l1")));

        assertFalse(result.success());
        assertTrue(result.message().contains("运营负责人权限"));
        verify(ecommerce, never()).callTool(any(), any());
    }

    @Test
    void allowsCustomerOrdersForRegisteredL2Caller() {
        // EC-15：L2 通过注册表查客户明细应当真正调用电商服务。
        FeishuProperties properties = new FeishuProperties();
        EcommerceMcpClientService ecommerce = mock(EcommerceMcpClientService.class);
        when(ecommerce.callTool(any(), any())).thenReturn(Map.of("rows", "ok"));
        registry = newRegistry(mock(SkillCliExecutorService.class), ecommerce, new BotRoleResolver(openId -> "L2"),
                properties);

        ToolResult result = registry.execute(new ToolCall("ecommerce.call_tool", Map.of(
                "toolName", "ecommerce.query_customer_orders",
                "senderOpenId", "ou_l2")));

        assertTrue(result.success());
        verify(ecommerce).callTool(any(), any());
    }

    private ToolRegistryService newRegistry(SkillCliExecutorService cli, FeishuProperties properties) {
        // 默认场景：电商服务用 Mock，角色解析返回未登记（L1）。
        return newRegistry(cli, mock(EcommerceMcpClientService.class), new BotRoleResolver(openId -> null), properties);
    }

    private ToolRegistryService newRegistry(SkillCliExecutorService cli, EcommerceMcpClientService ecommerce,
            BotRoleResolver roleResolver, FeishuProperties properties) {
        // 用 Mock 构造依赖，避免测试依赖数据库和飞书网络。
        return new ToolRegistryService(cli, ecommerce, mock(FeishuUserScopeMappingService.class),
                new ToolPermissionService(properties), new RoleToolPermissionService(), roleResolver, properties);
    }
}
