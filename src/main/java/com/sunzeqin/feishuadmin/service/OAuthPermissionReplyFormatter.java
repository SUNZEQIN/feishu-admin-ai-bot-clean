package com.sunzeqin.feishuadmin.service;

import com.sunzeqin.feishuadmin.pojo.UserScopeInfo;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OAuth 授权回复格式化工具。
 *
 * <p>作用：当系统需要用户扫码授权时，把“为什么要授权、当前申请哪些权限”说清楚，
 * 避免用户只看到一个二维码，不知道这次授权覆盖什么。</p>
 */
public final class OAuthPermissionReplyFormatter {
    // 用户可见的权限明细上限，避免 scope 太多导致飞书消息过长。
    private static final int MAX_VISIBLE_SCOPES = 8;

    private OAuthPermissionReplyFormatter() {
    }

    public static String userAuthorizationRequired(FeishuUserScopeMappingService scopeMappingService,
            String domain, String scopeText) {
        return "需要你授权后才能以用户身份继续执行。\n\n"
                + permissionSummary(scopeMappingService, domain, scopeText)
                + "\n\n请扫描二维码完成授权。\n\n"
                + "授权完成后，系统会保存到用户表并定时刷新 token。";
    }

    public static String continuationAuthorizationRequired(FeishuUserScopeMappingService scopeMappingService,
            String domain, String scopeText) {
        return "需要你授权后才能继续执行。\n\n"
                + permissionSummary(scopeMappingService, domain, scopeText)
                + "\n\n请扫描二维码完成授权。\n\n"
                + "授权完成后，系统会保存到用户表并定时刷新 token。";
    }

    private static String permissionSummary(FeishuUserScopeMappingService scopeMappingService,
            String domain, String scopeText) {
        Set<String> scopes = parseScopes(scopeText);
        if (scopes.isEmpty()) {
            return "当前需要授权的权限：使用系统默认权限。";
        }

        Map<String, String> descriptions = scopeDescriptions(scopeMappingService);
        StringBuilder builder = new StringBuilder();
        builder.append("当前需要授权的权限：共 ").append(scopes.size()).append(" 项");
        if (domain != null && !domain.isBlank()) {
            builder.append("，业务域：").append(domain.trim());
        }
        builder.append("\n");

        int index = 0;
        for (String scope : scopes) {
            index++;
            if (index > MAX_VISIBLE_SCOPES) {
                builder.append("- 其余 ").append(scopes.size() - MAX_VISIBLE_SCOPES)
                        .append(" 项会一并申请，避免你反复扫码");
                break;
            }
            builder.append("- ").append(scope).append("：")
                    .append(descriptions.getOrDefault(scope, defaultDescription(scope)))
                    .append("\n");
        }

        return builder.toString().stripTrailing();
    }

    private static Map<String, String> scopeDescriptions(FeishuUserScopeMappingService scopeMappingService) {
        Map<String, String> descriptions = new LinkedHashMap<>();
        descriptions.put("offline_access", "用于刷新授权，避免短时间内反复扫码");

        if (scopeMappingService == null) {
            return descriptions;
        }

        try {
            for (List<UserScopeInfo> infos : scopeMappingService.allDomainScopes().values()) {
                for (UserScopeInfo info : infos) {
                    if (!info.scope().isBlank() && !info.description().isBlank()) {
                        descriptions.putIfAbsent(info.scope(), info.description());
                    }
                }
            }
        } catch (Exception ignored) {
            // 映射文件读取异常不影响授权回复，未知 scope 会原样展示。
        }
        return descriptions;
    }

    private static String defaultDescription(String scope) {
        if (scope == null || scope.isBlank()) {
            return "未识别权限";
        }
        return "飞书返回/配置要求的权限";
    }

    private static Set<String> parseScopes(String scopeText) {
        Set<String> scopes = new LinkedHashSet<>();
        if (scopeText == null || scopeText.isBlank()) {
            return scopes;
        }
        for (String item : scopeText.trim().split("\\s+")) {
            if (!item.isBlank()) {
                scopes.add(item.trim());
            }
        }
        return scopes;
    }
}
