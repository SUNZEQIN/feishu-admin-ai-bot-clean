package com.sunzeqin.feishuadmin.pojo;

import java.util.List;

/**
 * 飞书消息事件内部对象。
 *
 * <p>作用：把飞书原始事件 JSON 转成业务层容易使用的结构。</p>
 *
 * @param appId       飞书应用 ID
 * @param eventId     事件 ID
 * @param eventType   事件类型
 * @param openId      发送人 open_id
 * @param userId      发送人 user_id
 * @param senderType  发送人类型
 * @param chatId      当前会话 ID
 * @param chatType    当前会话类型，group 或 p2p
 * @param messageId   消息 ID
 * @param messageType 消息类型
 * @param text        文本内容
 * @param mentions    @ 提及对象
 *
 * @author sunzeqin
 */
public record FeishuMessageEvent(String appId, String eventId, String eventType,
                                 String openId, String userId, String senderType,
                                 String chatId, String chatType, String messageId,
                                 String messageType, String text, List<FeishuMention> mentions) {
    public FeishuMessageEvent {
        mentions = mentions == null ? List.of() : List.copyOf(mentions);
    }
}
