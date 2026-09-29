package com.sunzeqin.feishuadmin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sunzeqin.feishuadmin.config.FeishuProperties;
import com.sunzeqin.feishuadmin.pojo.ChatMember;
import com.sunzeqin.feishuadmin.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 飞书 OpenAPI 服务。
 *
 * <p>作用：集中封装 tenant_access_token、查询群成员、创建群聊、回复消息等飞书接口。</p>
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
     * 查询群成员。
     *
     * @param chatId       群 ID
     * @param memberIdType 成员 ID 类型，用户用 open_id，机器人用 app_id
     * @return 成员列表
     */
    public List<ChatMember> listChatMembers(String chatId, String memberIdType) {
        // 保存所有分页查询出来的群成员。
        List<ChatMember> members = new ArrayList<>();

        // 飞书分页标记，第一页为空。
        String pageToken = "";

        // 循环拉取所有分页，直到飞书不再返回 page_token。
        do {
            // 当前页使用的 page_token。
            String currentPageToken = pageToken;

            // 拼接查询群成员接口地址，member_id_type 决定返回 open_id 还是 app_id。
            String requestUri = "/open-apis/im/v1/chats/" + chatId + "/members"
                    + "?member_id_type=" + memberIdType
                    + "&page_size=100";

            // 如果不是第一页，就把上一页返回的 page_token 带上。
            if (!currentPageToken.isBlank()) {
                requestUri = requestUri + "&page_token=" + currentPageToken;
            }

            // 打印查询群成员的真实飞书请求入参。
            log.info("飞书接口请求：方法=GET，接口=/open-apis/im/v1/chats/{chatId}/members，群ID={}，成员ID类型={}，分页大小={}，分页标记={}，请求地址={}",
                    chatId, memberIdType, 100, currentPageToken, requestUri);

            // 调用飞书查询群成员接口。
            JsonNode response = restClient.get()
                    .uri(requestUri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantAccessToken())
                    .retrieve()
                    .body(JsonNode.class);

            // 打印查询群成员的飞书响应摘要。
            log.info("飞书接口响应：方法=GET，接口=/open-apis/im/v1/chats/{chatId}/members，状态码={}，消息={}，成员数量={}，是否还有下一页={}，下一页标记={}",
                    response.path("code").asInt(-1),
                    response.path("msg").asText(""),
                    response.path("data").path("items").size(),
                    response.path("data").path("has_more").asBoolean(false),
                    response.path("data").path("page_token").asText(""));

            // 检查飞书返回 code 是否为 0，不为 0 就抛异常。
            ensureOk(response, "查询群成员失败");

            // data 节点里包含当前页成员和下一页 page_token。
            JsonNode data = response.path("data");

            // 遍历当前页成员。
            for (JsonNode item : data.path("items")) {
                // 把飞书返回的成员信息转成系统内部 ChatMember。
                members.add(new ChatMember(
                        item.path("member_id").asText(""),
                        item.path("name").asText(""),
                        item.path("member_type").asText(""),
                        memberIdType,
                        item.path("tenant_key").asText("")
                ));
            }

            // 读取下一页 page_token，如果为空说明已经没有下一页。
            pageToken = data.path("page_token").asText("");
        } while (!pageToken.isBlank());

        // 返回不可变列表，防止外部修改查询结果。
        return List.copyOf(members);
    }

    /**
     * 创建群聊。
     *
     * @param chatName    群名
     * @param userOpenIds 用户 open_id 列表
     * @param botAppIds   机器人 app_id 列表
     * @return 新群 chat_id
     */
    public String createChat(String chatName, List<String> userOpenIds, List<String> botAppIds) {
        // 组装创建群聊接口请求体，用户使用 open_id，机器人使用 app_id。
        Map<String, Object> body = Map.of("name", chatName,
                "user_id_list", userOpenIds,
                "bot_id_list", botAppIds);

        // 打印建群入参，方便排查实际传给飞书的用户和机器人 ID。
        log.info("飞书建群入参：群名={}，用户openId列表={}，机器人appId列表={}，请求体={}",
                chatName, userOpenIds, botAppIds, body);

        // 打印创建群聊的真实飞书请求入参。
        log.info("飞书接口请求：方法=POST，接口=/open-apis/im/v1/chats，查询参数=user_id_type=open_id,set_bot_manager=true，请求体={}",
                body);

        // 调用飞书创建群聊接口。
        JsonNode response = restClient.post()
                .uri(builder -> builder.path("/open-apis/im/v1/chats")
                        .queryParam("user_id_type", "open_id")
                        .queryParam("set_bot_manager", true)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        // 打印创建群聊的飞书响应摘要。
        log.info("飞书接口响应：方法=POST，接口=/open-apis/im/v1/chats，状态码={}，消息={}，数据={}",
                response.path("code").asInt(-1),
                response.path("msg").asText(""),
                response.path("data"));

        // 检查飞书返回 code 是否为 0。
        ensureOk(response, "创建群聊失败");

        // 不同返回结构里 chat_id 的位置可能略有差异，这里优先取 data.chat.chat_id。
        String chatId = response.path("data").path("chat").path("chat_id")
                .asText(response.path("data").path("chat_id").asText(""));

        // 打印建群成功日志，方便用 chat_id 继续排查。
        log.info("飞书建群成功：群名={}，新群会话ID={}", chatName, chatId);

        // 返回新群 chat_id。
        return chatId;
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
        log.info("飞书接口请求：方法=POST，接口=/open-apis/im/v1/messages/{messageId}/reply，消息ID={}，请求体={}",
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
        log.info("飞书接口响应：方法=POST，接口=/open-apis/im/v1/messages/{messageId}/reply，消息ID={}，状态码={}，消息={}，数据={}",
                messageId,
                response.path("code").asInt(-1),
                response.path("msg").asText(""),
                response.path("data"));
    }

    private synchronized String tenantAccessToken() {
        // 如果 token 已存在且距离过期还有 60 秒以上，就直接复用缓存。
        if (!tenantAccessToken.isBlank() && Instant.now().isBefore(tokenExpiresAt.minusSeconds(60))) {
            // 打印 token 缓存命中日志，不打印 token 明文。
            log.info("飞书token缓存命中：过期时间={}", tokenExpiresAt);
            return tenantAccessToken;
        }

        // 打印获取 token 请求日志，只打印 appId，不打印 appSecret。
        log.info("飞书接口请求：方法=POST，接口=/open-apis/auth/v3/tenant_access_token/internal，appId={}，appSecret是否已配置={}",
                properties.getAppId(), properties.getAppSecret() != null && !properties.getAppSecret().isBlank());

        // token 不存在或快过期时，调用飞书接口重新获取 tenant_access_token。
        JsonNode response = restClient.post()
                .uri("/open-apis/auth/v3/tenant_access_token/internal")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("app_id", properties.getAppId(), "app_secret", properties.getAppSecret()))
                .retrieve()
                .body(JsonNode.class);

        // 打印获取 token 响应摘要，不打印 token 明文。
        log.info("飞书接口响应：方法=POST，接口=/open-apis/auth/v3/tenant_access_token/internal，状态码={}，消息={}，有效期秒数={}",
                response.path("code").asInt(-1),
                response.path("msg").asText(""),
                response.path("expire").asLong(0));

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
