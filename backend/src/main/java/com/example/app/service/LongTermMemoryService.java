package com.example.app.service;

import com.example.app.dto.MemoryDTO;
import com.example.app.entity.LongTermMemory;
import com.example.app.entity.LongTermMemory.MemoryType;
import com.example.app.repository.LongTermMemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LongTermMemoryService {

    private final LongTermMemoryRepository repository;
    private final VectorStoreWrapper vectorStoreWrapper;
    private final com.example.app.config.VectorStoreConfig vectorStoreConfig;

    @Transactional
    public MemoryDTO save(MemoryDTO dto) {
        LongTermMemory entity = LongTermMemory.builder()
                .userId(dto.getUserId())
                .content(dto.getContent())
                .type(dto.getMemoryType())
                .importance(dto.getImportance() != null ? dto.getImportance() : 5)
                .build();

        entity = repository.save(entity);

        vectorStoreWrapper.add(dto.getUserId(), dto.getContent(), entity.getId());

        log.info("Saved long-term memory: id={}, userId={}, type={}",
                entity.getId(), entity.getUserId(), entity.getType());

        return MemoryDTO.fromEntity(entity);
    }

    @Transactional
    public List<MemoryDTO> saveAll(List<MemoryDTO> dtos) {
        List<LongTermMemory> entities = dtos.stream()
                .map(dto -> LongTermMemory.builder()
                        .userId(dto.getUserId())
                        .content(dto.getContent())
                        .type(dto.getMemoryType())
                        .importance(dto.getImportance() != null ? dto.getImportance() : 5)
                        .build())
                .collect(Collectors.toList());

        entities = repository.saveAll(entities);

        List<VectorStoreWrapper.MemoryEmbeddingPair> pairs = new ArrayList<>();
        for (int i = 0; i < entities.size(); i++) {
            LongTermMemory entity = entities.get(i);
            MemoryDTO dto = dtos.get(i);
            pairs.add(new VectorStoreWrapper.MemoryEmbeddingPair(entity.getId(), dto.getContent()));
        }
        vectorStoreWrapper.addBatch(entities.get(0).getUserId(), pairs);

        log.info("Saved {} long-term memories for user {}", entities.size(), entities.get(0).getUserId());

        return entities.stream()
                .map(MemoryDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<MemoryDTO> findById(Long id) {
        return repository.findById(id)
                .map(MemoryDTO::fromEntity);
    }

    @Transactional(readOnly = true)
    public List<MemoryDTO> findByUserId(String userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(MemoryDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MemoryDTO> findByUserIdAndType(String userId, String type) {
        try {
            MemoryType memoryType = MemoryType.valueOf(type.toUpperCase());
            return repository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, memoryType).stream()
                    .map(MemoryDTO::fromEntity)
                    .collect(Collectors.toList());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid memory type: {}", type);
            return new ArrayList<>();
        }
    }

    @Transactional(readOnly = true)
    public List<MemoryDTO> recall(String userId, String query, int topK) {
        List<Long> memoryIds = vectorStoreWrapper.search(userId, query, topK);

        if (memoryIds.isEmpty()) {
            return new ArrayList<>();
        }

        int minImportance = vectorStoreConfig.getMinImportance();
        log.info("[Memory Recall] Using min importance threshold: {}", minImportance);

        return repository.findAllById(memoryIds).stream()
                .filter(m -> m.getUserId().equals(userId))
                .filter(m -> m.getImportance() >= minImportance)
                .map(MemoryDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MemoryDTO> recall(String userId, String query, int topK, List<String> types) {
        List<MemoryDTO> allResults = recall(userId, query, topK);

        if (types == null || types.isEmpty()) {
            return allResults;
        }

        List<MemoryType> memoryTypes = types.stream()
                .map(t -> {
                    try {
                        return MemoryType.valueOf(t.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .filter(t -> t != null)
                .collect(Collectors.toList());

        return allResults.stream()
                .filter(dto -> memoryTypes.contains(dto.getMemoryType()))
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteById(Long id) {
        Optional<LongTermMemory> memory = repository.findById(id);
        if (memory.isPresent()) {
            LongTermMemory entity = memory.get();
            vectorStoreWrapper.remove(entity.getUserId(), id);
            repository.deleteById(id);
            log.info("Deleted long-term memory: id={}, userId={}", id, entity.getUserId());
        }
    }

    @Transactional
    public int cleanExpired() {
        int deleted = repository.deleteByExpiresAtBefore(LocalDateTime.now());
        log.info("Cleaned {} expired long-term memories", deleted);
        return deleted;
    }

    @Transactional
    public void deleteByUserId(String userId) {
        vectorStoreWrapper.removeByUserId(userId);
        repository.deleteByUserId(userId);
        log.info("Deleted all long-term memories for user {}", userId);
    }

    @Transactional(readOnly = true)
    public List<MemoryDTO> findByUserIdAndMinImportance(String userId, Integer minImportance) {
        return repository.findByUserIdAndMinImportance(userId, minImportance).stream()
                .map(MemoryDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public int countByUserId(String userId) {
        return repository.countByUserId(userId);
    }
}