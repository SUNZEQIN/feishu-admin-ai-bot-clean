package com.sunzeqin.feishuadmin.pojo.audit;

import java.time.LocalDateTime;

/**
 * 一条任务审计记录，对应 `agent_task` 表。
 *
 * <p>作用：回答"谁在什么时候问了什么、结果如何"（产品规则 O-05）。</p>
 *
 * @param taskId       任务 ID
 * @param messageId    飞书消息 ID，用于和日志串联
 * @param chatId       会话 ID
 * @param chatType     会话类型
 * @param userOpenId   发送人 open_id
 * @param userRole     发送人角色（L1/L2/L3）
 * @param rawInput     用户原始输入
 * @param intentDomain 意图域：ecommerce / 飞书业务域 / oauth
 * @param status       任务状态
 * @param stage        当前或结束阶段说明
 * @param startTime    开始时间
 *
 * @author sunzeqin
 */
public record AgentTaskRecord(String taskId, String messageId, String chatId, String chatType,
        String userOpenId, String userRole, String rawInput, String intentDomain,
        String status, String stage, LocalDateTime startTime) {
}
