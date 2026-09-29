package com.sunzeqin.feishuadmin;

import com.sunzeqin.feishuadmin.config.FeishuProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Spring Boot 启动类。
 *
 * <p>作用：启动飞书管理员机器人服务，并加载飞书应用配置。</p>
 *
 * @author sunzeqin
 */
@SpringBootApplication
@EnableConfigurationProperties(FeishuProperties.class)
public class FeishuAdminAiBotApplication {

    public static void main(String[] args) {
        // 启动 Spring Boot 应用，Spring 会自动扫描 controller、service、utils 等 Bean。
        SpringApplication.run(FeishuAdminAiBotApplication.class, args);
    }
}
