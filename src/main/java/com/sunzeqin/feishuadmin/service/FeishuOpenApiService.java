package com.sunzeqin.feishuadmin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sunzeqin.feishuadmin.config.FeishuProperties;
import com.sunzeqin.feishuadmin.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;

/**
 * 飞书 OpenAPI 服务。
 *
 * <p>作用：集中封装 tenant_access_token 和回复消息等基础飞书接口。</p>
 *
 * @author sunzeqin
 */
@Service
public class FeishuOpenApiService {
    // 当前服务使用的日志对象，主要打印飞书接口入参和执行结果。
    private static final Logger log = LoggerFactory.getLogger(FeishuOpenApiService.class);

    // 飞书应用配置，里面有 app_id、app_secret、baseUrl 等。
    private final FeishuProperties properties;

    // Spring 的 HTTP 客户端，用来请求飞书 OpenAPI。
    private final RestClient restClient;

    // JSON 工具类，用来把飞书消息 content 转成 JSON 字符串。
    private final JsonUtils jsonUtils;

    // 缓存 tenant_access_token，避免每次请求飞书都重新获取 token。
    private volatile String tenantAccessToken = "";

    // 记录 token 过期时间，快过期时自动重新获取。
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    public FeishuOpenApiService(FeishuProperties properties, RestClient.Builder builder, JsonUtils jsonUtils) {
        // 保存飞书配置。
        this.properties = properties;
        // 创建带飞书 baseUrl 的 RestClient。
        this.restClient = builder.baseUrl(properties.getBaseUrl()).build();
        // 保存 JSON 工具类。
        this.jsonUtils = jsonUtils;
    }

    /**
     * 回复飞书消息。
     *
     * @param messageId 原消息 ID
     * @param text      回复文本
     */
    public void replyText(String messageId, String text) {
        // 组装回复消息请求体。
        Map<String, Object> body = Map.of("msg_type", "text", "content", jsonUtils.write(Map.of("text", text)));

        // 打印回复消息的真实飞书请求入参。
        log.info("[阶段8 回复飞书] 飞书回复请求：消息ID={}，回复长度={}", messageId, text == null ? 0 : text.length());
        log.debug("[阶段8 回复飞书] 飞书回复完整请求：方法=POST，接口=/open-apis/im/v1/messages/{messageId}/reply，消息ID={}，请求体={}",
                messageId, body);

        // 调用飞书“回复消息”接口，把处理结果回复到原消息下。
        JsonNode response = restClient.post()
                .uri("/open-apis/im/v1/messages/{message_id}/reply", messageId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        // 打印回复消息的飞书响应摘要。
        log.info("[阶段8 回复飞书] 飞书回复响应摘要：消息ID={}，状态码={}，消息={}，新消息ID={}",
                messageId,
                response.path("code").asInt(-1),
                response.path("msg").asText(""),
                response.path("data").path("message_id").asText(""));
        log.debug("[阶段8 回复飞书] 飞书回复完整响应：消息ID={}，响应={}", messageId, response);
    }

    /**
     * 给 lark-cli 使用的 tenant_access_token。
     *
     * <p>作用：复用 Java OpenAPI 已经验证过的应用凭据，在执行 lark-cli 前写入 CLI 的 token store。</p>
     *
     * @return tenant_access_token 明文，只能传给 lark-cli，不允许打印到日志
     */
    public String tenantAccessTokenForCli() {
        // 复用已有 token 缓存和刷新逻辑。
        return tenantAccessToken();
    }

    private synchronized String tenantAccessToken() {
        // 如果 token 已存在且距离过期还有 60 秒以上，就直接复用缓存。
        if (!tenantAccessToken.isBlank() && Instant.now().isBefore(tokenExpiresAt.minusSeconds(60))) {
            // 打印 token 缓存命中日志，不打印 token 明文。
            log.info("[阶段4 工具调用] 飞书token缓存命中：过期时间={}", tokenExpiresAt);
            return tenantAccessToken;
        }

        // 打印获取 token 请求日志，只打印 appId，不打印 appSecret。
        log.info("[阶段4 工具调用] 飞书接口请求：方法=POST，接口=/open-apis/auth/v3/tenant_access_token/internal，appId={}，appSecret是否已配置={}",
                properties.getAppId(), properties.getAppSecret() != null && !properties.getAppSecret().isBlank());

        // token 不存在或快过期时，调用飞书接口重新获取 tenant_access_token。
        JsonNode response = restClient.post()
                .uri("/open-apis/auth/v3/tenant_access_token/internal")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("app_id", properties.getAppId(), "app_secret", properties.getAppSecret()))
                .retrieve()
                .body(JsonNode.class);

        // 打印获取 token 响应摘要，不打印 token 明文。
        log.info("[阶段4 工具调用] 飞书接口响应摘要：方法=POST，接口=/open-apis/auth/v3/tenant_access_token/internal，状态码={}，消息={}，有效期秒数={}",
                response.path("code").asInt(-1),
                response.path("msg").asText(""),
                response.path("expire").asLong(0));
        // token 接口响应里包含 tenant_access_token，不能在 DEBUG 里打印完整响应。

        // 检查获取 token 的返回结果。
        ensureOk(response, "获取 tenant_access_token 失败");

        // 保存新的 token。
        tenantAccessToken = response.path("tenant_access_token").asText("");

        // 保存 token 过期时间，飞书 expire 通常是秒数。
        tokenExpiresAt = Instant.now().plusSeconds(response.path("expire").asLong(7200));

        // 返回可用 token。
        return tenantAccessToken;
    }

    private void ensureOk(JsonNode response, String message) {
        // 飞书接口成功时 code 为 0；如果 response 为空，就给一个 -1。
        int code = response == null ? -1 : response.path("code").asInt(-1);

        // code 不是 0 就说明飞书接口失败。
        if (code != 0) {
            // 失败时保留飞书原始返回，方便从日志里查 code、msg、log_id。
            String detail = response == null ? "empty response" : response.toString();

            // 抛异常给上层，由上层组织用户可读的失败回复。
            throw new IllegalStateException(message + "：" + detail);
        }
    }
}
