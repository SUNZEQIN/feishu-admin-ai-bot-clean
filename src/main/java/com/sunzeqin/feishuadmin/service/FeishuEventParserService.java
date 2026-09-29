package com.sunzeqin.feishuadmin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sunzeqin.feishuadmin.pojo.FeishuMention;
import com.sunzeqin.feishuadmin.pojo.FeishuMessageEvent;
import com.sunzeqin.feishuadmin.utils.JsonUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 飞书事件解析服务。
 *
 * <p>作用：把飞书事件原始 JSON 解析成系统内部对象。</p>
 *
 * @author sunzeqin
 */
@Service
public class FeishuEventParserService {
    // JSON 工具类，用来解析飞书事件体和消息 content。
    private final JsonUtils jsonUtils;

    public FeishuEventParserService(JsonUtils jsonUtils) {
        // 保存 JSON 工具类。
        this.jsonUtils = jsonUtils;
    }

    public JsonNode parse(String rawBody) {
        // 把飞书原始回调请求体解析成 JsonNode。
        return jsonUtils.readTree(rawBody);
    }

    public boolean urlVerification(JsonNode root) {
        // 飞书 URL 验证事件的 type 固定是 url_verification。
        return "url_verification".equals(root.path("type").asText(""));
    }

    public String challenge(JsonNode root) {
        // 飞书要求 URL 验证时原样返回 challenge。
        return root.path("challenge").asText("");
    }

    public FeishuMessageEvent messageEvent(JsonNode root) {
        // 从事件头里取事件类型。
        String eventType = root.path("header").path("event_type").asText("");

        // 当前项目只处理“收到消息”事件，其它事件直接返回 null。
        if (!"im.message.receive_v1".equals(eventType)) {
            return null;
        }

        // event 节点里包含发送人和消息正文。
        JsonNode event = root.path("event");

        // sender 节点里包含发送人身份。
        JsonNode sender = event.path("sender");

        // sender_id 里包含 open_id、user_id 等 ID。
        JsonNode senderId = sender.path("sender_id");

        // message 节点里包含 chat_id、message_id、content、mentions 等信息。
        JsonNode message = event.path("message");

        // 把飞书原始 JSON 转成系统内部统一使用的消息事件对象。
        return new FeishuMessageEvent(
                root.path("header").path("app_id").asText(""),
                root.path("header").path("event_id").asText(""),
                eventType,
                senderId.path("open_id").asText(""),
                senderId.path("user_id").asText(""),
                sender.path("sender_type").asText(""),
                message.path("chat_id").asText(""),
                message.path("chat_type").asText(""),
                message.path("message_id").asText(""),
                message.path("message_type").asText(""),
                extractText(message),
                extractMentions(message)
        );
    }

    private String extractText(JsonNode message) {
        // 飞书文本消息的 content 本身还是一个 JSON 字符串。
        String content = message.path("content").asText("");

        // 如果 content 为空，直接返回空字符串。
        if (content.isBlank()) {
            return "";
        }

        try {
            // 正常情况下从 content JSON 里读取 text 字段。
            return jsonUtils.readTree(content).path("text").asText("");
        } catch (IllegalArgumentException ignored) {
            // 如果 content 不是合法 JSON，就直接返回原始 content，避免消息丢失。
            return content;
        }
    }

    private List<FeishuMention> extractMentions(JsonNode message) {
        // 用列表保存消息里的 @ 人信息。
        List<FeishuMention> mentions = new ArrayList<>();

        // 遍历飞书 message.mentions 数组。
        for (JsonNode item : message.path("mentions")) {
            // 每个 mention 的 id 节点里包含 open_id、user_id、union_id。
            JsonNode id = item.path("id");

            // 把当前 mention 转成系统内部对象。
            mentions.add(new FeishuMention(
                    item.path("key").asText(""),
                    item.path("name").asText(""),
                    id.path("open_id").asText(""),
                    id.path("user_id").asText(""),
                    id.path("union_id").asText("")
            ));
        }

        // 返回不可变列表，避免外部代码误修改解析结果。
        return List.copyOf(mentions);
    }
}
