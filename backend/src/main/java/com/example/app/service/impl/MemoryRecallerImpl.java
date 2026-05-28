package com.example.app.service.impl;

import com.example.app.dto.MemoryDTO;
import com.example.app.entity.LongTermMemory.MemoryType;
import com.example.app.service.LongTermMemoryService;
import com.example.app.service.MemoryRecaller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemoryRecallerImpl implements MemoryRecaller {

    private final LongTermMemoryService longTermMemoryService;

    @Override
    public List<MemoryDTO> recall(String userId, String query, int topK) {
        try {
            List<MemoryDTO> memories = longTermMemoryService.recall(userId, query, topK);
            log.debug("Recalled {} memories for user {} with query: {}", 
                    memories.size(), userId, query);
            return memories;
        } catch (Exception e) {
            log.warn("Failed to recall memory: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public List<MemoryDTO> recall(String userId, String query, int topK, List<String> types) {
        if (types == null || types.isEmpty()) {
            return recall(userId, query, topK);
        }

        try {
            List<MemoryDTO> memories = longTermMemoryService.recall(userId, query, topK, types);
            log.debug("Recalled {} memories for user {} with query {} and types {}", 
                    memories.size(), userId, query, types);
            return memories;
        } catch (Exception e) {
            log.warn("Failed to recall memory with type filter: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<MemoryDTO> getHighPriorityMemories(String userId) {
        return longTermMemoryService.findByUserIdAndMinImportance(userId, 7);
    }
}