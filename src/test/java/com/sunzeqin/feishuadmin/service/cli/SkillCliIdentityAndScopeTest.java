package com.sunzeqin.feishuadmin.service.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunzeqin.feishuadmin.config.FeishuProperties;
import com.sunzeqin.feishuadmin.pojo.cli.CliCommandResult;
import com.sunzeqin.feishuadmin.service.FeishuOpenApiService;
import com.sunzeqin.feishuadmin.service.FeishuUserScopeMappingService;
import com.sunzeqin.feishuadmin.service.UserOAuthTokenService;
import com.sunzeqin.feishuadmin.utils.JsonUtils;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Skill + CLI 身份判定与授权 scope 的回归测试。
 *
 * <p>固化一次真实故障的修复。故障现象：用户在群里说「查询我名下的所有多维表格」后，
 * 机器人先要了一次授权，扫完码又要一次，最后报「超过最大步骤数」。真实原因是三件事叠加：</p>
 *
 * <ol>
 *   <li>身份判据只认「用我的身份」这类说法，「我名下」不匹配，导致模型给出的 --as user
 *       被反复改回 --as bot，而 bot 身份执行 --mine 必然失败，形成来回死循环；</li>
 *   <li>第一次授权链接的 scope 是从 CLI 报错文本里正则抠出来的子集，授权后仍不够用，
 *       被迫二次授权；</li>
 *   <li>失败命令不去重，同一条必败命令重复执行，直到烧完步数才报一句与真实原因无关的
 *       「超过最大步骤数」。</li>
 * </ol>
 *
 * @author sunzeqin
 */
class SkillCliIdentityAndScopeTest {

    @Test
    void treatsPossessivePhrasingAsUserIdentity() throws Exception {
        SkillCliExecutorService service = newService();

        // 这次故障的原话，必须判定为需要用户身份。
        assertTrue(invokeBoolean(service, "explicitUserIdentityRequired", "查询我名下的所有多维表格"),
                "「我名下」应判定为需要用户身份");

        // 同类所有格表达。
        assertTrue(invokeBoolean(service, "explicitUserIdentityRequired", "查一下我自己的多维表格"));
        assertTrue(invokeBoolean(service, "explicitUserIdentityRequired", "我本人的日程"));
        assertTrue(invokeBoolean(service, "explicitUserIdentityRequired", "查本人的考勤记录"));

        // 原有显式说法不能被改坏。
        assertTrue(invokeBoolean(service, "explicitUserIdentityRequired", "用我的身份读取云文档"));
        assertTrue(invokeBoolean(service, "explicitUserIdentityRequired", "以用户身份执行"));

        // 普通请求仍然走机器人身份，避免无谓弹二维码。
        assertFalse(invokeBoolean(service, "explicitUserIdentityRequired", "看看本群有多少人"),
                "「本群」不应判定为需要用户身份");
        assertFalse(invokeBoolean(service, "explicitUserIdentityRequired", "把消息发到我的群里"),
                "单独的「我的」刻意不放行，避免机器人无谓要求授权");
    }

    @Test
    void stopsRetryingACommandThatAlreadyFailed() throws Exception {
        SkillCliExecutorService service = newService();

        List<String> command = List.of("lark-cli", "drive", "+search", "--mine", "--as", "bot");

        // 第一次执行前：没有失败历史，允许执行。
        assertNull(invokePreviousFailure(service, List.of(), command),
                "首次执行不应被拦截");

        // 已经失败过一次：第二次必须拦下，并带回真实原因。
        String firstFailure = "{\"ok\":false,\"error\":\"缺少登录用户 open_id\"}";
        List<CliCommandResult> observations = List.of(
                new CliCommandResult(String.join(" ", command), 2, "", firstFailure));

        String reason = invokePreviousFailure(service, observations, command);
        assertNotNull(reason, "同一条命令第二次必须被拦截");
        assertTrue(reason.contains("缺少登录用户 open_id"),
                "拦截时要带回真实失败原因，而不是只说步数超限，实际=" + reason);

        // 成功过的命令由 hasSuccessfulCommand 负责，不属于这条路径。
        List<CliCommandResult> successOnly = List.of(
                new CliCommandResult(String.join(" ", command), 0, "{}", ""));
        assertNull(invokePreviousFailure(service, successOnly, command),
                "成功过的命令不应被失败去重逻辑拦截");

        // 不同命令的失败不应互相影响。
        List<CliCommandResult> otherFailure = List.of(
                new CliCommandResult("lark-cli im +chat-list", 2, "", "boom"));
        assertNull(invokePreviousFailure(service, otherFailure, command),
                "不同命令的失败不应牵连当前命令");
    }

    @Test
    void authorizeScopeCoversWholeBizDomainInsteadOfRegexSubset() throws Exception {
        FeishuUserScopeMappingService scopeMapping = mock(FeishuUserScopeMappingService.class);
        // 业务域完整 scope 远大于从报错文本里抠出来的子集。
        when(scopeMapping.scopeTextForDomain("base"))
                .thenReturn("bitable:app base:table:read base:record:read offline_access");

        SkillCliExecutorService service = newService(scopeMapping);

        String merged = invokeMergeScopes(service, "base", "search:docs:read");

        // 业务域的完整 scope 必须在结果里，否则用户授权后仍然缺权限，会被要求二次授权。
        assertTrue(merged.contains("bitable:app"), "缺少业务域 scope bitable:app，实际=" + merged);
        assertTrue(merged.contains("base:record:read"), "缺少业务域 scope base:record:read，实际=" + merged);
        assertTrue(merged.contains("offline_access"), "缺少 offline_access，实际=" + merged);

        // 报错文本里额外出现的跨域 scope 也要保留。
        assertTrue(merged.contains("search:docs:read"), "跨域 scope 不应被丢掉，实际=" + merged);

        // 不能出现重复项，否则授权链接会被撑长。
        long distinct = java.util.Arrays.stream(merged.split("\\s+")).distinct().count();
        assertEquals(merged.split("\\s+").length, distinct, "合并后不应有重复 scope");
    }

    @Test
    void keepsUserIdentityWhenUserAlreadyHoldsDomainToken() throws Exception {
        String openId = "ou_holder";
        String scopeText = "bitable:app base:record:read";

        // 用户没有说「用我的身份」，但已经授权过整个业务域：此时不该再把 --as user 改回 bot，
        // 否则命令必然失败、模型再规划一次、再被改回，形成来回死循环。
        SkillCliExecutorService withToken = newService(scopeMappingWith(scopeText), tokenServiceWith(openId, scopeText, true));
        assertTrue(invokeUserIdentityAllowed(withToken, "看看有哪些多维表格", "base", openId),
                "已持有业务域 token 时应保留 --as user");

        // 没有 token 时仍然回到机器人身份，避免无谓地弹二维码要求授权。
        SkillCliExecutorService withoutToken = newService(scopeMappingWith(scopeText), tokenServiceWith(openId, scopeText, false));
        assertFalse(invokeUserIdentityAllowed(withoutToken, "看看有哪些多维表格", "base", openId),
                "没有 token 且用户没明确要求时不应保留 --as user");
    }

    @Test
    void doesNotAskForAuthorizationWhenUserTokenAlreadyCoversDomain() throws Exception {
        String openId = "ou_holder";
        String scopeText = "bitable:app base:record:read";

        SkillCliExecutorService service = newService(scopeMappingWith(scopeText), tokenServiceWith(openId, scopeText, true));

        // 命令失败时，代码要靠这个判断区分「用户没授权」和「这条命令本身不对」。
        assertTrue(invokeHasUserTokenForDomain(service, openId, "base"),
                "已授权用户应判定为持有业务域 token");

        // 缺少定位信息时保守返回 false，避免误判成已授权而跳过真正需要的授权。
        assertFalse(invokeHasUserTokenForDomain(service, "", "base"),
                "openId 为空时应判定为没有 token");
        assertFalse(invokeHasUserTokenForDomain(service, openId, ""),
                "业务域为空时应判定为没有 token");
    }

    // ---------- 测试辅助 ----------

    private SkillCliExecutorService newService() {
        return newService(mock(FeishuUserScopeMappingService.class));
    }

    private SkillCliExecutorService newService(FeishuUserScopeMappingService scopeMapping) {
        return newService(scopeMapping, mock(UserOAuthTokenService.class));
    }

    private SkillCliExecutorService newService(FeishuUserScopeMappingService scopeMapping,
            UserOAuthTokenService tokenService) {
        // 用 Mock 构造依赖，避免测试依赖数据库、飞书网络和大模型。
        return new SkillCliExecutorService(
                new FeishuProperties(),
                new JsonUtils(new ObjectMapper()),
                mock(FeishuOpenApiService.class),
                tokenService,
                scopeMapping);
    }

    private FeishuUserScopeMappingService scopeMappingWith(String scopeText) {
        FeishuUserScopeMappingService scopeMapping = mock(FeishuUserScopeMappingService.class);
        when(scopeMapping.scopeTextForDomain("base")).thenReturn(scopeText);
        return scopeMapping;
    }

    private UserOAuthTokenService tokenServiceWith(String openId, String scopeText, boolean hasScopes) {
        UserOAuthTokenService tokenService = mock(UserOAuthTokenService.class);
        when(tokenService.tokenHasScopes(openId, scopeText)).thenReturn(hasScopes);
        return tokenService;
    }

    private boolean invokeUserIdentityAllowed(SkillCliExecutorService service, String goal, String domain,
            String senderOpenId) throws Exception {
        Method method = SkillCliExecutorService.class.getDeclaredMethod("userIdentityAllowed",
                String.class, String.class, String.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(service, goal, domain, senderOpenId);
    }

    private boolean invokeHasUserTokenForDomain(SkillCliExecutorService service, String senderOpenId, String domain)
            throws Exception {
        Method method = SkillCliExecutorService.class.getDeclaredMethod("hasUserTokenForDomain",
                String.class, String.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(service, senderOpenId, domain);
    }

    private boolean invokeBoolean(SkillCliExecutorService service, String methodName, String argument)
            throws Exception {
        Method method = SkillCliExecutorService.class.getDeclaredMethod(methodName, String.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(service, argument);
    }

    @SuppressWarnings("unchecked")
    private String invokePreviousFailure(SkillCliExecutorService service, List<CliCommandResult> observations,
            List<String> command) throws Exception {
        Method method = SkillCliExecutorService.class.getDeclaredMethod("previousFailureReason", List.class, List.class);
        method.setAccessible(true);
        return (String) method.invoke(service, observations, command);
    }

    private String invokeMergeScopes(SkillCliExecutorService service, String domain, String missingScopes)
            throws Exception {
        Method method = SkillCliExecutorService.class.getDeclaredMethod("mergeWithDomainScopes", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, domain, missingScopes);
    }
}
