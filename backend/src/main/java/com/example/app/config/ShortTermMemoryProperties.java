package com.example.app.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 短期记忆配置类
 *
 * 控制会话级短期记忆（MessageWindowChatMemory）的窗口大小、
 * Redis 持久化过期时间、以及 Redis 键前缀。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "memory.short-term")
public class ShortTermMemoryProperties {

    /**
     * 短期记忆窗口大小（最多保留的消息条数）
     * 使用 LangChain4j MessageWindowChatMemory 作为滑动窗口，
     * 超出时自动丢弃最早的消息。
     */
    private int maxMessages = 20;

    /**
     * Redis 中短期记忆的过期时间（小时）
     * 超过 TTL 后自动失效，下一次请求会从数据库回退加载
     */
    private int ttlHours = 24;

    /**
     * Redis 键前缀
     * 实际 key 格式为 {redisKeyPrefix}{conversationId}
     */
    private String redisKeyPrefix = "kchat:memory:";
}
