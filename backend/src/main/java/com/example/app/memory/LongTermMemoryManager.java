package com.example.app.memory;

import com.example.app.entity.LongTermMemory;
import com.example.app.entity.LongTermMemory.MemoryType;
import com.example.app.repository.LongTermMemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class LongTermMemoryManager {

    private final LongTermMemoryRepository repository;

    public void store(String userId, String content) {
        store(userId, content, MemoryType.KNOWLEDGE);
    }

    public void store(String userId, String content, String type) {
        MemoryType memoryType;
        try {
            memoryType = MemoryType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            memoryType = MemoryType.KNOWLEDGE;
        }
        store(userId, content, memoryType);
    }

    public void store(String userId, String content, MemoryType type) {
        LongTermMemory memory = LongTermMemory.builder()
                .userId(userId)
                .content(content)
                .type(type)
                .build();
        repository.save(memory);
        log.debug("Stored long-term memory for user: {}", userId);
    }

    public List<String> retrieve(String userId) {
        List<LongTermMemory> memories = repository.findByUserIdOrderByCreatedAtDesc(userId);
        List<String> contents = new ArrayList<>();
        for (LongTermMemory memory : memories) {
            contents.add(memory.getContent());
        }
        return contents;
    }

    public List<String> retrieve(String userId, String type) {
        MemoryType memoryType;
        try {
            memoryType = MemoryType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return new ArrayList<>();
        }
        List<LongTermMemory> memories = repository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, memoryType);
        List<String> contents = new ArrayList<>();
        for (LongTermMemory memory : memories) {
            contents.add(memory.getContent());
        }
        return contents;
    }

    public void delete(Long memoryId) {
        repository.deleteById(memoryId);
        log.debug("Deleted long-term memory: {}", memoryId);
    }

    public void clear(String userId) {
        repository.deleteByUserId(userId);
        log.debug("Cleared all long-term memories for user: {}", userId);
    }
}