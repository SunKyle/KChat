package com.example.app.memory;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class ShortTermMemory {

    private final RedisTemplate<String, Object> redisTemplate;

    private final Map<String, ChatMemory> memoryMap = new ConcurrentHashMap<>();

    private static final String REDIS_KEY_PREFIX = "kchat:memory:";
    private static final Duration EXPIRATION = Duration.ofHours(24);

    public ChatMemory getMemory(String conversationId) {
        try {
            String key = REDIS_KEY_PREFIX + conversationId;
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                log.debug("Loaded memory from Redis for conversation: {}", conversationId);
            }
            ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);
            if (cached instanceof List) {
                var messages = (List<dev.langchain4j.data.message.ChatMessage>) cached;
                for (var msg : messages) {
                    memory.add(msg);
                }
            }
            return new RedisBackedChatMemory(memory, redisTemplate, key, conversationId);
        } catch (Exception e) {
            log.warn("Redis unavailable, falling back to in-memory storage: {}", e.getMessage());
            return memoryMap.computeIfAbsent(conversationId, id -> MessageWindowChatMemory.withMaxMessages(20));
        }
    }

    public void clearMemory(String conversationId) {
        memoryMap.remove(conversationId);
        try {
            String key = REDIS_KEY_PREFIX + conversationId;
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.debug("Failed to clear memory from Redis: {}", e.getMessage());
        }
    }

    public void clearAll() {
        memoryMap.clear();
        try {
            var keys = redisTemplate.keys(REDIS_KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.debug("Failed to clear all memory from Redis: {}", e.getMessage());
        }
    }

    private static class RedisBackedChatMemory implements ChatMemory {

        private final ChatMemory delegate;
        private final RedisTemplate<String, Object> redisTemplate;
        private final String key;
        private final String conversationId;

        public RedisBackedChatMemory(ChatMemory delegate, RedisTemplate<String, Object> redisTemplate,
                String key, String conversationId) {
            this.delegate = delegate;
            this.redisTemplate = redisTemplate;
            this.key = key;
            this.conversationId = conversationId;
        }

        @Override
        public void add(dev.langchain4j.data.message.ChatMessage message) {
            delegate.add(message);
            persist();
        }

        @Override
        public List<dev.langchain4j.data.message.ChatMessage> messages() {
            return delegate.messages();
        }

        @Override
        public void clear() {
            delegate.clear();
            try {
                redisTemplate.delete(key);
            } catch (Exception e) {
                log.debug("Failed to clear Redis memory: {}", e.getMessage());
            }
        }

        @Override
        public String id() {
            return conversationId;
        }

        private void persist() {
            try {
                redisTemplate.opsForValue().set(key, delegate.messages(), EXPIRATION);
            } catch (Exception e) {
                log.debug("Failed to persist memory to Redis: {}", e.getMessage());
            }
        }
    }
}