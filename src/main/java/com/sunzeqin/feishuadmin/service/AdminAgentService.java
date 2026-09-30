package com.sunzeqin.feishuadmin.service;

import com.sunzeqin.feishuadmin.config.FeishuProperties;
import com.sunzeqin.feishuadmin.pojo.agent.AgentRunResult;
import com.sunzeqin.feishuadmin.pojo.FeishuMessageEvent;
import com.sunzeqin.feishuadmin.service.agent.AgentOrchestratorService;
import com.sunzeqin.feishuadmin.utils.LlmErrorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 管理员机器人业务服务。
 *
 * <p>作用：接收飞书消息事件，判断是否命中当前功能，并调用粗粒度 Tool 完成业务。</p>
 *
 * @author sunzeqin
 */
@Service
public class AdminAgentService {
    // 当前服务使用的日志对象，用来排查后台异步处理是否成功。
    private static final Logger log = LoggerFactory.getLogger(AdminAgentService.class);

    // 飞书应用配置，用来判断是否开启处理中提示。
    private final FeishuProperties properties;

    // 飞书 OpenAPI 服务，用来回复飞书消息。
    private final FeishuOpenApiService openApi;

    // Agent 编排服务，负责 plan -> tool -> observe -> plan 的循环。
    private final AgentOrchestratorService orchestrator;

    public AdminAgentService(FeishuProperties properties, FeishuOpenApiService openApi,
            AgentOrchestratorService orchestrator) {
        // 保存配置对象。
        this.properties = properties;
        // 保存 OpenAPI 服务。
        this.openApi = openApi;
        // 保存 Agent 编排器。
        this.orchestrator = orchestrator;
    }

    /**
     * 处理一条飞书消息。
     *
     * @param event 消息事件
     */
    public void handleMessage(FeishuMessageEvent event) {
        // 这里留给同步调用兜底；真实入口会优先调用异步方法。
        doHandleMessage(event);
    }

    @Async
    public void handleMessageAsync(FeishuMessageEvent event) {
        // 后台异步处理飞书消息，避免飞书回调接口等待 LLM 执行。
        doHandleMessage(event);
    }

    private void doHandleMessage(FeishuMessageEvent event) {
        try {
            // 如果配置开启了处理中提示，就先给用户回一条“正在处理”。
            if (properties.isProcessingReplyEnabled()) {
                try {
                    // 用飞书回复接口回复原消息，告诉用户请求已经进入处理流程。
                    replyToSender(event, properties.getProcessingReplyText());
                    log.info("[阶段2 回复处理中] 已发送处理中提示：消息ID={}", event.messageId());
                } catch (Exception ignored) {
                    // 处理中提示失败不影响主流程。
                    log.warn("[阶段2 回复处理中] 处理中提示发送失败但不影响主流程：消息ID={}，错误={}",
                            event.messageId(), ignored.getMessage());
                }
            }

            // 交给 Agent 编排器执行多步循环。
            AgentRunResult result = orchestrator.run(event);

            // 把最终处理结果回复到飞书原消息下面。
            replyToSender(event, result.reply());
        } catch (Exception e) {
            // 捕获后台线程异常，避免异步任务静默失败。
            log.error("[阶段8 回复飞书] 消息异步处理失败：消息ID={}，错误={}", event.messageId(), e.getMessage(), e);

            // 如果是大模型余额不足，要明确回复用户，避免用户只看到“正在处理”。
            if (LlmErrorUtils.insufficientBalance(e)) {
                try {
                    replyToSender(event, LlmErrorUtils.insufficientBalanceReply());
                } catch (Exception replyError) {
                    log.warn("[阶段8 回复飞书] 余额不足提示回复失败：消息ID={}，错误={}", event.messageId(), replyError.getMessage());
                }
            }
        }
    }

    private void replyToSender(FeishuMessageEvent event, String text) {
        // 打印最终回复摘要，不在 INFO 里刷完整正文。
        log.info("[阶段8 回复飞书] 准备回复用户：消息ID={}，会话类型={}，是否@发送人={}，回复长度={}",
                event.messageId(), event.chatType(), "group".equals(event.chatType()), text == null ? 0 : text.length());
        // 给飞书用户回复消息。群聊里统一 @ 触发本次请求的人。
        openApi.replyText(event.messageId(), withSenderMention(event, text));
    }

    private String withSenderMention(FeishuMessageEvent event, String text) {
        // 非群聊不需要 @，私聊里直接回复即可。
        if (!"group".equals(event.chatType())) {
            return text;
        }

        // open_id 为空时不能构造 @，直接返回原文。
        if (event.openId() == null || event.openId().isBlank()) {
            return text;
        }

        // 飞书文本消息里使用 open_id 作为 at 的 user_id。
        return "<at user_id=\"" + event.openId() + "\"></at> " + text;
    }
}
