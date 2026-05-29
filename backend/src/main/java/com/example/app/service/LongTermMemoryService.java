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

/**
 * 长期记忆服务
 *
 * 核心职责：
 * - 管理用户长期记忆的 CRUD 操作
 * - 维护向量索引与数据库的一致性
 * - 支持语义召回和类型过滤
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LongTermMemoryService {

    private final LongTermMemoryRepository repository;
    private final VectorStoreWrapper vectorStoreWrapper;
    private final com.example.app.config.VectorStoreConfig vectorStoreConfig;

    /**
     * 保存单个记忆
     *
     * 事务一致性：
     * - 数据库保存和向量索引添加在同一事务中
     * - 任一失败都会回滚，保证数据一致性
     *
     * @param dto 记忆 DTO
     * @return 保存后的记忆 DTO
     */
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

    /**
     * 批量保存记忆
     *
     * 性能优化：
     * - 使用 saveAll 批量插入数据库
     * - 使用 addBatch 批量添加向量索引
     *
     * 注意：
     * - 假设所有记忆属于同一用户，取第一条的 userId 用于向量索引
     *
     * @param dtos 记忆 DTO 列表
     * @return 保存后的记忆 DTO 列表
     */
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

    /**
     * 按用户和类型查找记忆
     *
     * 异常处理：
     * - 无效的 type 字符串会被忽略，返回空列表
     *
     * @param userId 用户 ID
     * @param type 记忆类型字符串
     * @return 记忆列表
     */
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

    /**
     * 语义召回记忆
     *
     * 召回策略：
     * 1. 基于向量相似度检索 topK 个记忆
     * 2. 过滤非当前用户的记忆（防止数据泄漏）
     * 3. 应用重要性阈值过滤低质量记忆
     *
     * @param userId 用户 ID
     * @param query 查询文本
     * @param topK 返回数量上限
     * @return 相关记忆列表
     */
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

    /**
     * 语义召回记忆（带类型过滤）
     *
     * @param userId 用户 ID
     * @param query 查询文本
     * @param topK 返回数量上限
     * @param types 记忆类型白名单
     * @return 相关记忆列表
     */
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

    /**
     * 删除记忆
     *
     * 一致性保证：
     * - 同时删除数据库记录和向量索引
     *
     * @param id 记忆 ID
     */
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

    /**
     * 清理过期记忆
     *
     * 技术债务：
     * - 仅清理数据库，向量索引中的过期数据未同步清理
     * - 可能导致向量召回返回已过期的记忆 ID
     *
     * @return 删除的记忆数量
     */
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