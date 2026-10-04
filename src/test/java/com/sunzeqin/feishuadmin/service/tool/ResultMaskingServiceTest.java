package com.sunzeqin.feishuadmin.service.tool;

import com.sunzeqin.feishuadmin.pojo.role.BotRole;
import com.sunzeqin.feishuadmin.pojo.tool.ToolCall;
import com.sunzeqin.feishuadmin.pojo.tool.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 电商结果脱敏测试（产品规则 P-04 + 需求 4.2）。
 *
 * <p>核心断言：L1 拿不到成本价与客户字段，L2 拿不到手机号与地址，
 * 密钥类字段任何角色都拿不到，而且原始数据不会被就地修改。</p>
 *
 * @author sunzeqin
 */
class ResultMaskingServiceTest {

    // 被测服务，纯逻辑不需要 Spring 容器。
    private final ResultMaskingService service = new ResultMaskingService();

    @Test
    void hidesCostAndCustomerFieldsFromL1() {
        // 模拟电商 MCP 返回：商品行里带成本价与客户信息。
        Map<String, Object> data = Map.of("products", List.of(Map.of(
                "name", "洗衣液",
                "stock", 3,
                "costPrice", 9.9,
                "customerName", "张伟",
                "phone", "13800138000")));

        ToolResult masked = service.mask(BotRole.L1, gatewayCall("ecommerce.query_low_inventory"),
                ToolResult.success("ecommerce.call_tool", "调用完成", data));

        // 非敏感字段保留。
        assertTrue(masked.data().toString().contains("洗衣液"));
        assertTrue(masked.data().toString().contains("stock"));

        // 敏感字段全部消失（EC-13）。
        assertFalse(masked.data().toString().contains("costPrice"));
        assertFalse(masked.data().toString().contains("9.9"));
        assertFalse(masked.data().toString().contains("张伟"));
        assertFalse(masked.data().toString().contains("13800138000"));
    }

    @Test
    void keepsCostButHidesPersonalDataFromL2() {
        // L2 可以看成本与客户姓名，但手机号地址仍然不能给。
        Map<String, Object> data = Map.of(
                "costPrice", 9.9,
                "customerName", "张伟",
                "address", "上海市浦东新区某路 1 号");

        ToolResult masked = service.mask(BotRole.L2, gatewayCall("ecommerce.query_customer_orders"),
                ToolResult.success("ecommerce.call_tool", "调用完成", data));

        // 成本与姓名保留。
        assertTrue(masked.data().containsKey("costPrice"));
        assertTrue(masked.data().containsKey("customerName"));

        // 地址被去掉。
        assertFalse(masked.data().containsKey("address"));
    }

    @Test
    void keepsEverythingExceptSecretsForL3() {
        // 管理员可以看业务字段，但密钥类字段属于永久红线。
        Map<String, Object> data = Map.of(
                "costPrice", 9.9,
                "address", "上海市浦东新区某路 1 号",
                "apiToken", "abc123");

        ToolResult masked = service.mask(BotRole.L3, gatewayCall("ecommerce.query_low_inventory"),
                ToolResult.success("ecommerce.call_tool", "调用完成", data));

        // 业务字段保留。
        assertTrue(masked.data().containsKey("costPrice"));
        assertTrue(masked.data().containsKey("address"));

        // 密钥类字段所有角色都看不到。
        assertFalse(masked.data().containsKey("apiToken"));
    }

    @Test
    void masksPhoneNumberAppearingInPlainText() {
        // 字段名正常，但值里出现手机号，也要打码。
        Map<String, Object> data = Map.of("remark", "客户备注：13800138000 加急");

        ToolResult masked = service.mask(BotRole.L1, gatewayCall("ecommerce.query_low_inventory"),
                ToolResult.success("ecommerce.call_tool", "调用完成", data));

        assertEquals("客户备注：*** 加急", masked.data().get("remark"));
    }

    @Test
    void doesNotMutateOriginalData() {
        // 原始数据是不可变 Map，脱敏只能生成新结果。
        Map<String, Object> data = Map.of("costPrice", 9.9, "name", "洗衣液");

        service.mask(BotRole.L1, gatewayCall("ecommerce.query_low_inventory"),
                ToolResult.success("ecommerce.call_tool", "调用完成", data));

        // 原始 Map 不受影响。
        assertTrue(data.containsKey("costPrice"));
    }

    @Test
    void ignoresNonEcommerceResults() {
        // 飞书侧工具结果不参与电商脱敏。
        ToolResult result = ToolResult.success("cli.run_skill", "执行完成", Map.of("costPrice", 9.9));

        ToolResult masked = service.mask(BotRole.L1, new ToolCall("cli.run_skill", Map.of()), result);

        assertEquals(result, masked);
    }

    @Test
    void treatsNullRoleAsL1() {
        // 角色漏传时按最小权限处理。
        Map<String, Object> data = Map.of("costPrice", 9.9, "customerName", "张伟");

        ToolResult masked = service.mask(null, gatewayCall("ecommerce.query_low_inventory"),
                ToolResult.success("ecommerce.call_tool", "调用完成", data));

        assertTrue(masked.data().isEmpty());
    }

    @Test
    void hidesSensitiveFieldNamesRegardlessOfSuffix() {
        // 字段名规范化后按关键词匹配，兼容下划线与驼峰写法。
        assertTrue(ResultMaskingService.hidden(BotRole.L1, "cost_price"));
        assertTrue(ResultMaskingService.hidden(BotRole.L1, "customerName"));
        assertTrue(ResultMaskingService.hidden(BotRole.L2, "receiver_address"));
        assertTrue(ResultMaskingService.hidden(BotRole.L3, "accessToken"));

        // 正常字段不受影响。
        assertFalse(ResultMaskingService.hidden(BotRole.L1, "productName"));
        assertFalse(ResultMaskingService.hidden(BotRole.L1, "stock"));
        assertFalse(ResultMaskingService.hidden(BotRole.L1, "gmv"));
    }

    private ToolCall gatewayCall(String toolName) {
        // 构造电商网关调用，真实工具名在参数里。
        return new ToolCall("ecommerce.call_tool", Map.of("toolName", toolName));
    }
}
