
package com.example.app.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置类
 *
 * 设计考虑：
 * 1. Key 使用 StringRedisSerializer：确保 Redis 键的可读性和兼容性
 * 2. Value 使用 GenericJackson2JsonRedisSerializer：支持复杂对象的 JSON 序列化
 * 3. 条件化启用：通过配置项控制是否启用 Redis，支持降级为本地开发环境
 */
@Configuration
public class RedisConfig {

    /**
     * 配置 RedisTemplate Bean
     *
     * 设计决策：
     * - Key/HashKey 采用 String 序列化：保证键名在 Redis 客户端中可读，便于调试和排查问题
     * - Value/HashValue 采用 Jackson JSON 序列化：支持 POJO 直接持久化
     * - 默认启用：允许通过 spring.data.redis.enabled=true
     *
     * @param connectionFactory Redis 连接工厂
     * @return 配置好的 RedisTemplate
     */
    @Bean
    @ConditionalOnProperty(name = "spring.data.redis.enabled", havingValue = "true", matchIfMissing = true)
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
