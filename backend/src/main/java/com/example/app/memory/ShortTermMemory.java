package com.example.app.memory;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ShortTermMemory {

    private final Map<String, ChatMemory> memoryMap = new ConcurrentHashMap<>();

    public ChatMemory getMemory(String conversationId) {
        return memoryMap.computeIfAbsent(conversationId, id -> MessageWindowChatMemory.withMaxMessages(20));
    }

    public void clearMemory(String conversationId) {
        memoryMap.remove(conversationId);
    }

    public void clearAll() {
        memoryMap.clear();
    }
}