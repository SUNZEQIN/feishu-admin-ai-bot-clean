package com.sunzeqin.feishuadmin.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.sunzeqin.feishuadmin.config.FeishuProperties;
import com.sunzeqin.feishuadmin.pojo.FeishuMessageEvent;
import com.sunzeqin.feishuadmin.pojo.UrlVerificationResponse;
import com.sunzeqin.feishuadmin.service.AdminAgentService;
import com.sunzeqin.feishuadmin.service.FeishuEventParserService;
import com.sunzeqin.feishuadmin.service.MessageDedupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 飞书事件回调控制器。
 *
 * <p>作用：接收飞书事件推送，处理 URL 验证和消息事件。业务逻辑全部交给 service 层。</p>
 *
 * @author sunzeqin
 */
@RestController
public class FeishuEventController {
    // 当前控制器使用的日志对象，方便排查飞书事件是否进入系统。
    private static final Logger log = LoggerFactory.getLogger(FeishuEventController.class);

    // 飞书应用配置，例如 verification token。
    private final FeishuProperties properties;

    // 飞书事件解析服务，负责把原始 JSON 转成业务对象。
    private final FeishuEventParserService parser;

    // 管理员机器人业务服务，真正处理消息事件。
    private final AdminAgentService agentService;

    // 消息去重服务，避免飞书重试导致同一条消息回复多次。
    private final MessageDedupService dedupService;

    public FeishuEventController(FeishuProperties properties, FeishuEventParserService parser,
            AdminAgentService agentService, MessageDedupService dedupService) {
        // 保存配置对象，后面校验飞书 URL verification token 会用到。
        this.properties = properties;
        // 保存事件解析器，后面解析飞书回调 JSON 会用到。
        this.parser = parser;
        // 保存业务服务，后面把消息事件交给它处理。
        this.agentService = agentService;
        // 保存消息去重服务。
        this.dedupService = dedupService;
    }

    @PostMapping("/api/feishu/events")
    public ResponseEntity<?> events(@RequestBody String rawBody) {
        // 先把飞书推送过来的原始字符串解析成 JsonNode，后面统一从 JsonNode 取字段。
        JsonNode root = parser.parse(rawBody);

        // 飞书第一次配置事件地址时，会发送 url_verification 请求，需要单独处理。
        if (parser.urlVerification(root)) {
            // 校验飞书传来的 token，防止不是当前应用的请求混进来。
            verifyToken(root.path("token").asText(""));
            // 按飞书要求原样返回 challenge，飞书后台才能保存回调地址。
            return ResponseEntity.ok(new UrlVerificationResponse(parser.challenge(root)));
        }

        // 尝试把当前事件解析成“接收消息事件”，不是消息事件时返回 null。
        FeishuMessageEvent event = parser.messageEvent(root);

        // 如果解析到了消息事件，就交给业务服务处理。
        if (event != null) {
            // 同一个 message_id 如果已经处理过，直接忽略，避免重复回复。
            if (!dedupService.firstSeen(event.messageId())) {
                log.info("[阶段1 接收飞书事件] 重复推送已忽略：消息ID={}", event.messageId());
                return ResponseEntity.ok(Map.of("ok", true, "duplicated", true));
            }

            // 打印入口摘要，后续所有日志都可以用 messageId 串起来。
            log.info("[阶段1 接收飞书事件] 收到消息：消息ID={}，会话ID={}，会话类型={}，发送人openId={}，文本={}",
                    event.messageId(), event.chatId(), event.chatType(), event.openId(), event.text());

            // 后台异步处理消息，当前回调立即返回 200 给飞书，避免飞书超时重试。
            agentService.handleMessageAsync(event);
        } else {
            // 如果不是当前系统关心的事件，只记录日志，不抛异常，避免飞书反复重试。
            String eventType = root.path("header").path("event_type").asText("");
            log.info("[阶段1 接收飞书事件] 非目标事件已忽略：事件类型={}", eventType);
        }

        // 飞书事件回调需要快速返回成功，具体业务在服务里处理。
        return ResponseEntity.ok(Map.of("ok", true));
    }

    private void verifyToken(String token) {
        // 从配置里取出我们自己设置的 verification token。
        String expected = properties.getVerificationToken();

        // 如果配置了 token，并且飞书传来的 token 不一致，就拒绝本次请求。
        if (expected != null && !expected.isBlank() && !expected.equals(token)) {
            throw new IllegalArgumentException("飞书 verification token 不匹配");
        }
    }
}
