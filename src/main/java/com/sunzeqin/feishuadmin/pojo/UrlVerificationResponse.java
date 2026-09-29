package com.sunzeqin.feishuadmin.pojo;

/**
 * 飞书 URL 验证响应。
 *
 * <p>作用：飞书开放平台配置事件回调地址时，需要原样返回 challenge。</p>
 *
 * @param challenge 飞书 challenge
 *
 * @author sunzeqin
 */
public record UrlVerificationResponse(String challenge) {
}
