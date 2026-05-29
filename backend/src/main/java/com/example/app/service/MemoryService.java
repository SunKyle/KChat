package com.example.app.service;

import com.example.app.dto.MemoryDTO;
import com.example.app.entity.LongTermMemory.MemoryType;
import dev.langchain4j.data.message.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Deprecated
public class MemoryService {

    private final ShortTermMemoryService shortTermMemoryService;
    private final LongTermMemoryFacadeService longTermMemoryFacadeService;

    public List<ChatMessage> getMemoryContext(String conversationId) {
        return shortTermMemoryService.getMemoryContext(conversationId);
    }

    public List<String> getLongTermMemoryContext(String userId) {
        return longTermMemoryFacadeService.getLongTermMemoryContext(userId);
    }

    public List<MemoryDTO> recallLongTermMemory(String userId, String query, int topK) {
        return longTermMemoryFacadeService.recallLongTermMemory(userId, query, topK);
    }

    public List<MemoryDTO> recallLongTermMemory(String userId, String query, int topK, List<String> types) {
        return longTermMemoryFacadeService.recallLongTermMemory(userId, query, topK, types);
    }

    public List<MemoryDTO> getAllLongTermMemory(String userId) {
        return longTermMemoryFacadeService.getAllLongTermMemory(userId);
    }

    public List<MemoryDTO> getLongTermMemoryByType(String userId, String type) {
        return longTermMemoryFacadeService.getLongTermMemoryByType(userId, type);
    }

    public MemoryDTO saveLongTermMemory(String userId, String content, MemoryType type, Integer importance) {
        return longTermMemoryFacadeService.saveLongTermMemory(userId, content, type, importance);
    }

    public void updateMemoryWithUserMessage(String conversationId, String content) {
        shortTermMemoryService.updateMemoryWithUserMessage(conversationId, content);
    }

    public void updateMemoryWithAiMessage(String conversationId, String content) {
        shortTermMemoryService.updateMemoryWithAiMessage(conversationId, content);
    }

    public void updateMemory(String conversationId, String userMessage, String aiMessage) {
        shortTermMemoryService.updateMemory(conversationId, userMessage, aiMessage);
    }

    public void storeLongTermMemory(String userId, String content) {
        longTermMemoryFacadeService.storeLongTermMemory(userId, content);
    }

    public void clearMemory(String conversationId) {
        shortTermMemoryService.clearMemory(conversationId);
    }

    public void clearAllMemory() {
        shortTermMemoryService.clearAll();
    }

    public void clearAllMemory(String userId) {
        shortTermMemoryService.clearAll();
        longTermMemoryFacadeService.clearAllMemory(userId);
    }

    public void deleteLongTermMemory(Long memoryId) {
        longTermMemoryFacadeService.deleteLongTermMemory(memoryId);
    }

    public int cleanExpiredLongTermMemory() {
        return longTermMemoryFacadeService.cleanExpiredLongTermMemory();
    }
}
