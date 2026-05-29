package com.example.app.service;

import com.example.app.memory.ShortTermMemory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShortTermMemoryService {

    private final ShortTermMemory shortTermMemory;

    public List<ChatMessage> getMemoryContext(String conversationId) {
        log.info("[ShortTermMemoryService] Getting short-term memory for conversation: {}", conversationId);
        ChatMemory memory = shortTermMemory.getMemory(conversationId);
        List<ChatMessage> messages = memory.messages();
        log.info("[ShortTermMemoryService] Found {} short-term memory messages for conversation: {}",
                messages.size(), conversationId);
        return messages;
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

    public void clearAll() {
        shortTermMemory.clearAll();
    }
}
