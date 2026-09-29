package com.sunzeqin.feishuadmin.service;

import com.sunzeqin.feishuadmin.config.FeishuProperties;
import com.sunzeqin.feishuadmin.pojo.agent.AgentRunResult;
import com.sunzeqin.feishuadmin.pojo.FeishuMessageEvent;
import com.sunzeqin.feishuadmin.service.agent.AgentOrchestratorService;
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
        // 如果配置开启了处理中提示，就先给用户回一条“正在处理”。
        if (properties.isProcessingReplyEnabled()) {
            try {
                // 用飞书回复接口回复原消息，告诉用户请求已经进入处理流程。
                openApi.replyText(event.messageId(), properties.getProcessingReplyText());
            } catch (Exception ignored) {
                // 处理中提示失败不影响主流程。
            }
        }

        // 交给 Agent 编排器执行多步循环。
        AgentRunResult result = orchestrator.run(event);

        // 把最终处理结果回复到飞书原消息下面。
        openApi.replyText(event.messageId(), result.reply());
    }
}
