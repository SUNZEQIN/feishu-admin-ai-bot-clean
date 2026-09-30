package com.sunzeqin.feishuadmin.pojo;

import java.time.Instant;

/**
 * 飞书用户授权 token。
 *
 * <p>作用：保存数据库里读取出来的用户 access_token、refresh_token 和 scope 信息。</p>
 *
 * @param appId            飞书应用ID
 * @param userOpenId       用户open_id
 * @param userId           用户user_id
 * @param scopeText        已授权scope，空格分隔
 * @param accessToken      用户access_token
 * @param refreshToken     用户refresh_token
 * @param expiresAt        access_token过期时间
 * @param refreshExpiresAt refresh_token过期时间
 *
 * @author sunzeqin
 */
public record UserOAuthToken(String appId, String userOpenId, String userId, String scopeText,
                             String accessToken, String refreshToken,
                             Instant expiresAt, Instant refreshExpiresAt) {
}
