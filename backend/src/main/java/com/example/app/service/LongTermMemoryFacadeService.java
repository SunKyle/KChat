package com.example.app.service;

import com.example.app.dto.MemoryDTO;
import com.example.app.entity.LongTermMemory.MemoryType;
import com.example.app.memory.LongTermMemoryManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LongTermMemoryFacadeService {

    private final LongTermMemoryManager longTermMemoryManager;
    private final LongTermMemoryService longTermMemoryService;

    public List<String> getLongTermMemoryContext(String userId) {
        log.info("[LongTermMemoryFacadeService] Getting long-term memory for user: {}", userId);
        List<String> memories = longTermMemoryManager.retrieve(userId);
        log.info("[LongTermMemoryFacadeService] Found {} long-term memory items for user: {}",
                memories.size(), userId);
        return memories;
    }

    public List<MemoryDTO> recallLongTermMemory(String userId, String query, int topK) {
        log.info("[LongTermMemoryFacadeService] Recalling long-term memory for user: {}, query: '{}', topK: {}",
                userId, query, topK);
        List<MemoryDTO> memories = longTermMemoryService.recall(userId, query, topK);
        log.info("[LongTermMemoryFacadeService] Recalled {} long-term memories for user: {}",
                memories.size(), userId);
        if (!memories.isEmpty()) {
            for (MemoryDTO m : memories) {
                log.info("[LongTermMemoryFacadeService] - [{}] {} (importance: {})",
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
        log.info("[LongTermMemoryFacadeService] Saving long-term memory - userId: {}, type: {}, importance: {}, content: '{}'",
                userId, type, importance, content);
        MemoryDTO dto = MemoryDTO.builder()
                .userId(userId)
                .content(content)
                .type(type.name())
                .importance(importance != null ? importance : 5)
                .build();
        MemoryDTO saved = longTermMemoryService.save(dto);
        log.info("[LongTermMemoryFacadeService] Saved long-term memory - id: {}", saved.getId());
        return saved;
    }

    public void storeLongTermMemory(String userId, String content) {
        longTermMemoryManager.store(userId, content);
    }

    public void deleteLongTermMemory(Long memoryId) {
        longTermMemoryService.deleteById(memoryId);
    }

    public void clearAllMemory(String userId) {
        longTermMemoryManager.clear(userId);
        longTermMemoryService.deleteByUserId(userId);
    }

    public int cleanExpiredLongTermMemory() {
        return longTermMemoryService.cleanExpired();
    }
}
