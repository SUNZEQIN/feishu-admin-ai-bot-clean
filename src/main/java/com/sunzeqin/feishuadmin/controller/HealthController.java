package com.sunzeqin.feishuadmin.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 健康检查控制器。
 *
 * <p>作用：提供部署后用于验证 Java 服务是否存活的接口。</p>
 *
 * @author sunzeqin
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        // 返回一个最简单的健康检查结果，用来确认服务已经启动成功。
        return Map.of("ok", true, "service", "feishu-admin-ai-bot-clean");
    }
}
