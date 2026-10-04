package com.sunzeqin.feishuadmin.service.tool;

import com.sunzeqin.feishuadmin.pojo.role.BotRole;
import com.sunzeqin.feishuadmin.pojo.tool.ToolCall;
import com.sunzeqin.feishuadmin.pojo.tool.ToolErrorCode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 按角色分级的工具权限服务测试。
 *
 * <p>重点验证「网关式电商调用」：真正的工具名藏在 ecommerce.call_tool 的参数里，
 * 校验必须深入参数，否则 L1 可以绕过分级查客户明细。</p>
 *
 * @author sunzeqin
 */
class RoleToolPermissionServiceTest {

    // 被测服务，纯逻辑不需要 Spring 容器。
    private final RoleToolPermissionService service = new RoleToolPermissionService();

    @Test
    void deniesCustomerOrdersThroughGatewayForL1() {
        // EC-11：L1 通过网关查客户明细必须被拒。
        ToolCall call = new ToolCall("ecommerce.call_tool", Map.of(
                "toolName", "ecommerce.query_customer_orders",
                "arguments", Map.of("customerName", "张伟")));

        RoleToolPolicy.Decision decision = service.check(call, BotRole.L1);

        assertFalse(decision.allowed());
        assertEquals(ToolErrorCode.PERMISSION_DENIED, decision.errorCode());
    }

    @Test
    void allowsCustomerOrdersThroughGatewayForL2() {
        // EC-15：L2 通过网关查客户明细必须放行。
        ToolCall call = new ToolCall("ecommerce.call_tool", Map.of(
                "toolName", "ecommerce.query_customer_orders"));

        assertTrue(service.check(call, BotRole.L2).allowed());
    }

    @Test
    void deniesGatewayCallWithoutInnerToolName() {
        // 网关缺 toolName 属于非法调用，不能默认放行。
        ToolCall call = new ToolCall("ecommerce.call_tool", Map.of("arguments", Map.of()));

        RoleToolPolicy.Decision decision = service.check(call, BotRole.L3);

        assertFalse(decision.allowed());
        assertEquals(ToolErrorCode.PERMISSION_DENIED, decision.errorCode());
    }

    @Test
    void deniesV2ActionThroughGateway() {
        // EC-16：通过网关发起建群同样要明确"V1 不支持"。
        ToolCall call = new ToolCall("ecommerce.call_tool", Map.of("toolName", "ecommerce.create_group"));

        assertEquals(ToolErrorCode.NOT_SUPPORTED_IN_V1, service.check(call, BotRole.L3).errorCode());
    }

    @Test
    void allowsToolListForEveryRole() {
        // 工具清单属于元数据，不参与数据分级。
        ToolCall call = new ToolCall("ecommerce.list_tools", Map.of());

        assertTrue(service.check(call, BotRole.L1).allowed());
    }

    @Test
    void doesNotBlockFeishuTools() {
        // 飞书侧工具由原有白名单机制负责，本服务不越权干预。
        ToolCall call = new ToolCall("cli.run_skill", Map.of("domain", "docs"));

        assertTrue(service.check(call, BotRole.L1).allowed());
    }

    @Test
    void deniesEmptyCall() {
        // 空调用不能放行。
        assertFalse(service.check(null, BotRole.L3).allowed());
        assertFalse(service.check(new ToolCall("", Map.of()), BotRole.L3).allowed());
    }
}
