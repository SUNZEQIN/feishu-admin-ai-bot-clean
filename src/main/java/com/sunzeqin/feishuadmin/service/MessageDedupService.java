package com.sunzeqin.feishuadmin.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 飞书消息去重服务。
 *
 * <p>作用：飞书事件可能因为网络或回调超时被重复推送，同一个 message_id 只允许处理一次。</p>
 *
 * @author sunzeqin
 */
@Service
public class MessageDedupService {
    // 去重记录保留 30 分钟，足够覆盖飞书重试窗口。
    private static final long KEEP_SECONDS = 30 * 60;

    // 保存已接收过的 message_id 和首次接收时间。
    private final Map<String, Instant> receivedMessages = new ConcurrentHashMap<>();

    public boolean firstSeen(String messageId) {
        // 清理过期 message_id，避免内存一直增长。
        cleanExpired();

        // 空 messageId 不做去重，避免误伤。
        if (messageId == null || messageId.isBlank()) {
            return true;
        }

        // putIfAbsent 是原子操作；返回 null 表示第一次出现。
        return receivedMessages.putIfAbsent(messageId, Instant.now()) == null;
    }

    private void cleanExpired() {
        // 当前时间。
        Instant now = Instant.now();

        // 遍历缓存。
        Iterator<Map.Entry<String, Instant>> iterator = receivedMessages.entrySet().iterator();

        // 删除过期记录。
        while (iterator.hasNext()) {
            Map.Entry<String, Instant> entry = iterator.next();
            if (entry.getValue().plusSeconds(KEEP_SECONDS).isBefore(now)) {
                iterator.remove();
            }
        }
    }
}
