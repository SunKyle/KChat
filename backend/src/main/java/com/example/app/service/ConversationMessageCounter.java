package com.example.app.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class ConversationMessageCounter {

    private final Map<String, Integer> messageCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> lastActivityTimes = new ConcurrentHashMap<>();

    public int increment(String conversationId) {
        int count = messageCounts.merge(conversationId, 1, Integer::sum);
        lastActivityTimes.put(conversationId, System.currentTimeMillis());
        log.debug("Conversation {} message count: {}", conversationId, count);
        return count;
    }

    public int getCount(String conversationId) {
        return messageCounts.getOrDefault(conversationId, 0);
    }

    public void reset(String conversationId) {
        messageCounts.remove(conversationId);
        log.debug("Reset message count for conversation {}", conversationId);
    }

    public long getLastActivityTime(String conversationId) {
        return lastActivityTimes.getOrDefault(conversationId, 0L);
    }

    public void clear() {
        messageCounts.clear();
        lastActivityTimes.clear();
    }
}