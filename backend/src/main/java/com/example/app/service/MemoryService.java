package com.example.app.service;

import com.example.app.dto.MemoryDTO;
import com.example.app.entity.LongTermMemory.MemoryType;
import com.example.app.memory.LongTermMemoryManager;
import com.example.app.memory.ShortTermMemory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemoryService {

    private final ShortTermMemory shortTermMemory;
    private final LongTermMemoryManager longTermMemoryManager;
    private final LongTermMemoryService longTermMemoryService;

    public List<ChatMessage> getMemoryContext(String conversationId) {
        log.info("[Memory Retrieve] Getting short-term memory for conversation: {}", conversationId);
        ChatMemory memory = shortTermMemory.getMemory(conversationId);
        List<ChatMessage> messages = memory.messages();
        log.info("[Memory Retrieve] Found {} short-term memory messages for conversation: {}",
                messages.size(), conversationId);
        return messages;
    }

    public List<String> getLongTermMemoryContext(String userId) {
        log.info("[Memory Retrieve] Getting long-term memory for user: {}", userId);
        List<String> memories = longTermMemoryManager.retrieve(userId);
        log.info("[Memory Retrieve] Found {} long-term memory items for user: {}",
                memories.size(), userId);
        return memories;
    }

    public List<MemoryDTO> recallLongTermMemory(String userId, String query, int topK) {
        log.info("[Memory Retrieve] Recalling long-term memory for user: {}, query: '{}', topK: {}",
                userId, query, topK);
        List<MemoryDTO> memories = longTermMemoryService.recall(userId, query, topK);
        log.info("[Memory Retrieve] Recalled {} long-term memories for user: {}",
                memories.size(), userId);
        if (!memories.isEmpty()) {
            for (MemoryDTO m : memories) {
                log.info("[Memory Retrieve] - [{}] {} (importance: {})",
                        m.getType(), m.getContent(), m.getImportance());
            }
        }
        return memories;
    }

    public List<MemoryDTO> recallLongTermMemory(String userId, String query, int topK, List<String> types) {
        return longTermMemoryService.recall(userId, query, topK, types);
    }

    public List<MemoryDTO> getAllLongTermMemory(String userId) {
        return longTermMemoryService.findByUserId(userId);
    }

    public List<MemoryDTO> getLongTermMemoryByType(String userId, String type) {
        return longTermMemoryService.findByUserIdAndType(userId, type);
    }

    public MemoryDTO saveLongTermMemory(String userId, String content, MemoryType type, Integer importance) {
        log.info("[Memory Store] Saving long-term memory - userId: {}, type: {}, importance: {}, content: '{}'",
                userId, type, importance, content);
        MemoryDTO dto = MemoryDTO.builder()
                .userId(userId)
                .content(content)
                .type(type.name())
                .importance(importance != null ? importance : 5)
                .build();
        MemoryDTO saved = longTermMemoryService.save(dto);
        log.info("[Memory Store] Saved long-term memory - id: {}", saved.getId());
        return saved;
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

    public void storeLongTermMemory(String userId, String content) {
        longTermMemoryManager.store(userId, content);
    }

    public void clearMemory(String conversationId) {
        shortTermMemory.clearMemory(conversationId);
    }

    public void clearAllMemory() {
        shortTermMemory.clearAll();
    }

    public void clearAllMemory(String userId) {
        shortTermMemory.clearAll();
        longTermMemoryManager.clear(userId);
        longTermMemoryService.deleteByUserId(userId);
    }

    public void deleteLongTermMemory(Long memoryId) {
        longTermMemoryService.deleteById(memoryId);
    }

    public int cleanExpiredLongTermMemory() {
        return longTermMemoryService.cleanExpired();
    }
}