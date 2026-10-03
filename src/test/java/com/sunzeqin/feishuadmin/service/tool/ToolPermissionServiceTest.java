package com.sunzeqin.feishuadmin.service.tool;

import com.sunzeqin.feishuadmin.config.FeishuProperties;
import com.sunzeqin.feishuadmin.pojo.tool.ToolCall;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工具调用权限校验测试。
 *
 * @author sunzeqin
 */
class ToolPermissionServiceTest {

    @Test
    void allowsEverythingWhenNoAllowlistConfigured() {
        ToolPermissionService service = new ToolPermissionService(new FeishuProperties());

        assertFalse(service.enforced());
        assertTrue(service.check(new ToolCall("cli.run_skill", Map.of())).allowed());
    }

    @Test
    void deniesCallerMissingFromOpenIdAllowlist() {
        FeishuProperties properties = new FeishuProperties();
        properties.setToolAllowedOpenIds("ou_1, ou_2");
        ToolPermissionService service = new ToolPermissionService(properties);

        assertTrue(service.enforced());
        assertFalse(service.check(new ToolCall("cli.run_skill", Map.of("senderOpenId", "ou_9"))).allowed());
        assertTrue(service.check(new ToolCall("cli.run_skill", Map.of("senderOpenId", "ou_2"))).allowed());
    }

    @Test
    void deniesCallWithoutIdentityWhenAllowlistConfigured() {
        FeishuProperties properties = new FeishuProperties();
        properties.setToolAllowedChatIds("oc_1");
        ToolPermissionService service = new ToolPermissionService(properties);

        assertFalse(service.check(new ToolCall("ecommerce.call_tool", Map.of())).allowed());
        assertTrue(service.check(new ToolCall("ecommerce.call_tool", Map.of("sourceChatId", "oc_1"))).allowed());
    }

    @Test
    void masksIdentityForLogs() {
        assertEquals("未知", ToolPermissionService.mask(""));
        assertEquals("***", ToolPermissionService.mask("ou_1"));
        assertEquals("ou_abc***", ToolPermissionService.mask("ou_abcdefg"));
    }
}
