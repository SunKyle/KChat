package com.example.app.service;

import com.example.app.memory.LongTermMemory;
import com.example.app.memory.ShortTermMemory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MemoryService {

    private final ShortTermMemory shortTermMemory;
    private final LongTermMemory longTermMemory;

    public List<ChatMessage> getMemoryContext(String conversationId) {
        ChatMemory memory = shortTermMemory.getMemory(conversationId);
        return memory.messages();
    }

    public void updateMemoryWithUserMessage(String conversationId, String content) {
        ChatMemory memory = shortTermMemory.getMemory(conversationId);
        memory.add(UserMessage.from(content));
    }

    public void updateMemoryWithAiMessage(String conversationId, String content) {
        ChatMemory memory = shortTermMemory.getMemory(conversationId);
        memory.add(AiMessage.from(content));
    }

    public void updateMemory(String conversationId, String userMessage, String aiMessage) {
        ChatMemory memory = shortTermMemory.getMemory(conversationId);
        memory.add(UserMessage.from(userMessage));
        memory.add(AiMessage.from(aiMessage));
    }

    public void clearMemory(String conversationId) {
        shortTermMemory.clearMemory(conversationId);
    }

    public void clearAllMemory() {
        shortTermMemory.clearAll();
        longTermMemory.clear();
    }
}