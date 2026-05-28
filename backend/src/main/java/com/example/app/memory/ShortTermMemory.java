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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class ShortTermMemory {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private final Map<String, ChatMemory> memoryMap = new ConcurrentHashMap<>();

    private static final String REDIS_KEY_PREFIX = "kchat:memory:";
    private static final Duration EXPIRATION = Duration.ofHours(24);

    public ChatMemory getMemory(String conversationId) {
        try {
            String key = REDIS_KEY_PREFIX + conversationId;
            String json = stringRedisTemplate.opsForValue().get(key);
            
            ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);
            
            if (json != null && !json.isEmpty()) {
                try {
                    List<ChatMessage> messages = objectMapper.readValue(json,
                            new TypeReference<List<ChatMessage>>() {});
                    if (messages != null && !messages.isEmpty()) {
                        for (ChatMessage msg : messages) {
                            memory.add(msg);
                        }
                        log.debug("Loaded {} messages from Redis for conversation: {}", 
                                messages.size(), conversationId);
                    }
                } catch (Exception e) {
                    log.warn("Failed to deserialize memory from Redis for conversation {}: {}", 
                            conversationId, e.getMessage());
                }
            }
            
            return new RedisBackedChatMemory(memory, stringRedisTemplate, objectMapper, key, conversationId);
        } catch (Exception e) {
            log.warn("Redis unavailable, falling back to in-memory storage: {}", e.getMessage());
            return memoryMap.computeIfAbsent(conversationId, id -> MessageWindowChatMemory.withMaxMessages(20));
        }
    }

    public void clearMemory(String conversationId) {
        memoryMap.remove(conversationId);
        try {
            String key = REDIS_KEY_PREFIX + conversationId;
            stringRedisTemplate.delete(key);
        } catch (Exception e) {
            log.debug("Failed to clear memory from Redis: {}", e.getMessage());
        }
    }

    public void clearAll() {
        memoryMap.clear();
        try {
            var keys = stringRedisTemplate.keys(REDIS_KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.debug("Failed to clear all memory from Redis: {}", e.getMessage());
        }
    }

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

        @Override
        public void add(ChatMessage message) {
            delegate.add(message);
            persist();
        }

        @Override
        public List<ChatMessage> messages() {
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
                List<ChatMessage> messages = delegate.messages();
                String json = objectMapper.writeValueAsString(messages);
                redisTemplate.opsForValue().set(key, json, EXPIRATION);
            } catch (Exception e) {
                log.debug("Failed to persist memory to Redis: {}", e.getMessage());
            }
        }
    }
}