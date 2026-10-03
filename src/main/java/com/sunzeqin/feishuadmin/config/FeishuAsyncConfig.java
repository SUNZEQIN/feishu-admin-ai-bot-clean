package com.sunzeqin.feishuadmin.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 飞书机器人业务线程池配置。
 *
 * <p>作用：给 Agent 业务处理提供专用线程池，避免 @Async 使用默认执行器时无界创建线程，
 * 也避免慢任务把飞书回调线程池（Tomcat）拖满。</p>
 *
 * @author sunzeqin
 */
@Configuration
public class FeishuAsyncConfig {

    // 业务线程池的 Bean 名称，@Async 通过这个名字指定执行器。
    public static final String FEISHU_AGENT_EXECUTOR = "feishuAgentExecutor";

    // 当前配置类使用的日志对象。
    private static final Logger log = LoggerFactory.getLogger(FeishuAsyncConfig.class);

    /**
     * 创建飞书 Agent 业务线程池。
     *
     * @param properties 飞书配置
     * @return 业务线程池
     */
    @Bean(name = FEISHU_AGENT_EXECUTOR)
    public ThreadPoolTaskExecutor feishuAgentExecutor(FeishuProperties properties) {
        // 创建 Spring 线程池。
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 核心线程数：常驻处理飞书消息的后台线程。
        executor.setCorePoolSize(properties.getAgentCorePoolSize());

        // 最大线程数：队列满后允许临时扩容到的上限。
        executor.setMaxPoolSize(properties.getAgentMaxPoolSize());

        // 队列容量：控制积压上限，超限后直接拒绝，避免无限排队把内存吃满。
        executor.setQueueCapacity(properties.getAgentQueueCapacity());

        // 线程名前缀，方便 jstack 和日志定位。
        executor.setThreadNamePrefix("feishu-agent-");

        // 拒绝策略：直接抛 TaskRejectedException，由 Controller 捕获并回复用户，不使用 CallerRunsPolicy 阻塞回调线程。
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());

        // 关闭时等待在途任务结束，避免飞书消息处理到一半被强杀。
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        // 初始化线程池。
        executor.initialize();

        // 打印线程池参数，部署后可以直接确认配置是否生效。
        log.info("[启动] 飞书业务线程池已创建：名称={}，核心线程={}，最大线程={}，队列容量={}",
                FEISHU_AGENT_EXECUTOR, properties.getAgentCorePoolSize(),
                properties.getAgentMaxPoolSize(), properties.getAgentQueueCapacity());

        return executor;
    }
}
