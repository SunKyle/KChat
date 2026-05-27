package com.example.app.memory;

import com.example.app.entity.LongTermMemory;
import com.example.app.repository.LongTermMemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class LongTermMemoryManager {

    private final LongTermMemoryRepository repository;

    public void store(String userId, String content) {
        store(userId, content, "default");
    }

    public void store(String userId, String content, String type) {
        LongTermMemory memory = LongTermMemory.builder()
                .id(UUID.randomUUID().toString())
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
        List<LongTermMemory> memories = repository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, type);
        List<String> contents = new ArrayList<>();
        for (LongTermMemory memory : memories) {
            contents.add(memory.getContent());
        }
        return contents;
    }

    public void delete(String memoryId) {
        repository.deleteById(memoryId);
        log.debug("Deleted long-term memory: {}", memoryId);
    }

    public void clear(String userId) {
        repository.deleteByUserId(userId);
        log.debug("Cleared all long-term memories for user: {}", userId);
    }
}