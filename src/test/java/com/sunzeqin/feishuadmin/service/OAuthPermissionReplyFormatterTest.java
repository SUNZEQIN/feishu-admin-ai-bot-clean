package com.sunzeqin.feishuadmin.service;

import com.sunzeqin.feishuadmin.pojo.UserScopeInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * OAuth 授权回复的权限摘要测试。
 */
class OAuthPermissionReplyFormatterTest {

    @Test
    void showsCurrentRequiredPermissionsInsteadOfOnlySayingScanQrCode() {
        FeishuUserScopeMappingService scopeMapping = mock(FeishuUserScopeMappingService.class);
        when(scopeMapping.allDomainScopes()).thenReturn(Map.of(
                "base", List.of(
                        new UserScopeInfo("base:app:read", "查看多维表格应用", 1),
                        new UserScopeInfo("base:record:create", "新增多维表格记录", 2),
                        new UserScopeInfo("base:record:delete", "删除多维表格记录", 3)
                )
        ));

        String reply = OAuthPermissionReplyFormatter.userAuthorizationRequired(
                scopeMapping,
                "base",
                "offline_access base:app:read base:record:create base:record:delete unknown:scope");

        assertTrue(reply.contains("当前需要授权的权限"), "回复必须告诉用户这次具体申请什么权限");
        assertTrue(reply.contains("业务域：base"), "回复要带业务域，方便用户理解为什么要授权");
        assertTrue(reply.contains("查看多维表格应用"), "已知 scope 应展示中文说明");
        assertTrue(reply.contains("新增多维表格记录"), "已知 scope 应展示中文说明");
        assertTrue(reply.contains("删除多维表格记录"), "高风险权限也要明示");
        assertTrue(reply.contains("unknown:scope"), "未知 scope 不能吞掉，要原样展示");
        assertTrue(reply.contains("用于刷新授权"), "offline_access 要解释成人话");
        assertFalse(reply.contains("https://"), "授权链接不应出现在文本里，仍然只用二维码发送");
    }

    @Test
    void truncatesLongScopeListsButKeepsTotalCount() {
        FeishuUserScopeMappingService scopeMapping = mock(FeishuUserScopeMappingService.class);
        when(scopeMapping.allDomainScopes()).thenReturn(Map.of());

        String reply = OAuthPermissionReplyFormatter.userAuthorizationRequired(
                scopeMapping,
                "docs",
                "offline_access a:a b:b c:c d:d e:e f:f g:g h:h i:i j:j k:k");

        assertTrue(reply.contains("共 12 项"), "长列表要告诉用户总数");
        assertTrue(reply.contains("其余 4 项"), "长列表要折叠剩余数量，避免回复过长");
    }
}
