package com.sunzeqin.feishuadmin.pojo;

/**
 * 飞书用户身份 scope 信息。
 *
 * <p>作用：保存开放平台返回的 scope、中文描述和权限等级，方便授权链路按业务域选择权限。</p>
 *
 * @param scope       飞书权限标识
 * @param description 权限中文描述
 * @param level       权限等级
 *
 * @author sunzeqin
 */
public record UserScopeInfo(String scope, String description, int level) {

    public UserScopeInfo {
        // scope 为空时转成空字符串，避免后续拼接空指针。
        scope = scope == null ? "" : scope;

        // description 为空时转成空字符串，方便日志和工具返回。
        description = description == null ? "" : description;
    }
}
