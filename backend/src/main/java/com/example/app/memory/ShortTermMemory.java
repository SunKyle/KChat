package com.example.app.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 短期记忆管理
 *
 * 缓存策略设计：
 * 1. L1 缓存（内存）：ConcurrentHashMap，快速访问，应用重启丢失
 * 2. L2 缓存（Redis）：持久化存储，支持跨实例和重启恢复
 * 3. 回退机制：Redis 不可用时自动降级为纯内存存储
 *
 * 内存生命周期：
 * - 首次访问时从 Redis 加载或创建新记忆
 * - 每次添加消息时自动持久化到 Redis
 * - 24 小时后自动过期清理
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ShortTermMemory {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * L1 内存缓存：会话ID -> 记忆对象
     * 使用 ConcurrentHashMap 保证并发安全
     */
    private final Map<String, ChatMemory> memoryMap = new ConcurrentHashMap<>();

    /**
     * Redis 键前缀
     */
    private static final String REDIS_KEY_PREFIX = "kchat:memory:";

    /**
     * 记忆过期时间：24 小时
     *
     * 设计考虑：
     * - 短期记忆不需要永久保存
     * - 自动过期防止 Redis 内存无限增长
     */
    private static final Duration EXPIRATION = Duration.ofHours(24);

    /**
     * 获取对话记忆
     *
     * 获取流程：
     * 1. 先从 L1 内存缓存查找，命中直接返回
     * 2. 未命中从 Redis 加载，反序列化后重建记忆
     * 3. 包装为自动持久化的 RedisBackedChatMemory
     * 4. 存入 L1 缓存并返回
     *
     * 降级策略：
     * Redis 不可用时，创建纯内存记忆并记录警告
     *
     * @param conversationId 对话 ID
     * @return 对话记忆对象
     */
    public ChatMemory getMemory(String conversationId) {
        log.info("[ShortTermMemory] Getting memory for conversation: {}", conversationId);

        ChatMemory cachedMemory = memoryMap.get(conversationId);
        if (cachedMemory != null) {
            log.info("[ShortTermMemory] Found cached memory for conversation: {}", conversationId);
            return cachedMemory;
        }

        try {
            String key = REDIS_KEY_PREFIX + conversationId;
            String json = stringRedisTemplate.opsForValue().get(key);

            ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);

            if (json != null && !json.isEmpty()) {
                try {
                    List<ChatMessage> messages = objectMapper.readValue(json,
                            new TypeReference<List<ChatMessage>>() {
                            });
                    if (messages != null && !messages.isEmpty()) {
                        for (ChatMessage msg : messages) {
                            memory.add(msg);
                        }
                        log.info("[ShortTermMemory] Loaded {} messages from Redis for conversation: {}",
                                messages.size(), conversationId);
                    }
                } catch (Exception e) {
                    log.warn("[ShortTermMemory] Failed to deserialize memory from Redis for conversation {}: {}",
                            conversationId, e.getMessage());
                }
            }

            RedisBackedChatMemory backedMemory = new RedisBackedChatMemory(
                    memory, stringRedisTemplate, objectMapper, key, conversationId);

            memoryMap.put(conversationId, backedMemory);

            return backedMemory;
        } catch (Exception e) {
            log.warn("[ShortTermMemory] Redis unavailable, falling back to in-memory storage: {}", e.getMessage());
            return memoryMap.computeIfAbsent(conversationId, id -> MessageWindowChatMemory.withMaxMessages(20));
        }
    }

    /**
     * 清除指定对话的记忆
     *
     * 同时从内存缓存和 Redis 中删除
     *
     * @param conversationId 对话 ID
     */
    public void clearMemory(String conversationId) {
        memoryMap.remove(conversationId);
        try {
            String key = REDIS_KEY_PREFIX + conversationId;
            stringRedisTemplate.delete(key);
        } catch (Exception e) {
            log.debug("[ShortTermMemory] Failed to clear memory from Redis: {}", e.getMessage());
        }
    }

    /**
     * 清除所有对话的记忆
     *
     * 技术债务：
     * - 使用 keys() 命令在生产环境可能有性能风险，建议改为 SCAN 或定期清理
     */
    public void clearAll() {
        memoryMap.clear();
        try {
            var keys = stringRedisTemplate.keys(REDIS_KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.debug("[ShortTermMemory] Failed to clear all memory from Redis: {}", e.getMessage());
        }
    }

    /**
     * Redis 持久化包装类
     *
     * 装饰器模式：包装原始 ChatMemory，在添加消息时自动同步到 Redis
     * 实现了写时持久化（Write-Through）策略
     */
    private static class RedisBackedChatMemory implements ChatMemory {

        private final ChatMemory delegate;
        private final StringRedisTemplate redisTemplate;
        private final ObjectMapper objectMapper;
        private final String key;
        private final String conversationId;

        public RedisBackedChatMemory(ChatMemory delegate, StringRedisTemplate redisTemplate,
                ObjectMapper objectMapper, String key, String conversationId) {
            this.delegate = delegate;
            this.redisTemplate = redisTemplate;
            this.objectMapper = objectMapper;
            this.key = key;
            this.conversationId = conversationId;
        }

        /**
         * 添加消息
         *
         * 先更新内存，再异步持久化到 Redis
         * 持久化失败不影响内存操作（容错设计）
         *
         * @param message 聊天消息
         */
        @Override
        public void add(ChatMessage message) {
            delegate.add(message);
            persist();
        }

        @Override
        public List<ChatMessage> messages() {
            return delegate.messages();
        }

        /**
         * 清空记忆
         *
         * 同时清空内存和 Redis
         */
        @Override
        public void clear() {
            delegate.clear();
            try {
                redisTemplate.delete(key);
            } catch (Exception e) {
                log.debug("[ShortTermMemory] Failed to clear Redis memory: {}", e.getMessage());
            }
        }

        @Override
        public String id() {
            return conversationId;
        }

        /**
         * 持久化到 Redis
         *
         * 设计决策：
         * - 失败只记录日志，不抛异常，保证核心功能可用
         * - 设置过期时间，自动清理过期数据
         */
        private void persist() {
            try {
                List<ChatMessage> messages = delegate.messages();
                String json = objectMapper.writeValueAsString(messages);
                redisTemplate.opsForValue().set(key, json, EXPIRATION);
                log.debug("[ShortTermMemory] Persisted {} messages to Redis for conversation: {}",
                        messages.size(), conversationId);
            } catch (Exception e) {
                log.debug("[ShortTermMemory] Failed to persist memory to Redis: {}", e.getMessage());
            }
        }
    }
}