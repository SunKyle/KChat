package com.example.app.service;

import com.example.app.dto.MemoryDTO;
import com.example.app.entity.LongTermMemory;
import com.example.app.entity.LongTermMemory.MemoryType;
import com.example.app.memory.VectorStoreWrapper;
import com.example.app.repository.LongTermMemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
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

    /**
     * 长期记忆数据访问层
     */
    private final LongTermMemoryRepository repository;

    /**
     * 向量存储封装器，负责向量索引的管理
     */
    private final VectorStoreWrapper vectorStoreWrapper;

    /**
     * 向量存储配置，包含相似度阈值、最小重要性等参数
     */
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
                .confidence(dto.getConfidence())
                .source(dto.getSource())
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
                        .confidence(dto.getConfidence())
                        .source(dto.getSource())
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

    /**
     * 批量按 ID 查找记忆
     *
     * <p>用于关键词检索命中后批量加载记忆详情，与向量检索结果合并去重。
     *
     * @param ids 记忆 ID 列表
     * @return 记忆列表（顺序与输入 ID 一致，不存在的 ID 被跳过）
     */
    @Transactional(readOnly = true)
    public List<MemoryDTO> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, LongTermMemory> memoryMap = repository.findAllById(ids).stream()
                .collect(java.util.stream.Collectors.toMap(LongTermMemory::getId, m -> m));

        return ids.stream()
                .map(memoryMap::get)
                .filter(java.util.Objects::nonNull)
                .map(MemoryDTO::fromEntity)
                .collect(Collectors.toList());
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
     * @param type   记忆类型字符串
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
     * 1. 基于向量相似度检索 topK 个记忆（使用全局默认相似度阈值）
     * 2. 过滤非当前用户的记忆（防止数据泄漏）
     * 3. 应用重要性阈值过滤低质量记忆
     *
     * @param userId 用户 ID
     * @param query  查询文本
     * @param topK   返回数量上限
     * @return 相关记忆列表
     */
    @Transactional(readOnly = true)
    public List<MemoryDTO> recall(String userId, String query, int topK) {
        return recall(userId, query, topK, vectorStoreConfig.getSimilarityThreshold());
    }

    /**
     * 语义召回记忆（带自定义相似度阈值）
     *
     * 适用于"零容忍无关记忆"的场景（如每轮对话的长期记忆注入），
     * 通过较高的 minScore 过滤掉语义不相关的记忆，避免上下文污染。
     *
     * @param userId  用户 ID
     * @param query   查询文本
     * @param topK    返回数量上限
     * @param minScore 相似度最低阈值（0~1），高于此值的记忆才会被召回
     * @return 相关记忆列表（带 score 字段）
     */
    @Transactional(readOnly = true)
    public List<MemoryDTO> recall(String userId, String query, int topK, double minScore) {
        List<VectorStoreWrapper.ScoredMemory> scoredMemories = vectorStoreWrapper.searchWithScore(
                userId, query, topK, minScore);

        if (scoredMemories.isEmpty()) {
            return new ArrayList<>();
        }

        int minImportance = vectorStoreConfig.getMinImportance();

        Map<Long, Double> scoreMap = scoredMemories.stream()
                .collect(java.util.stream.Collectors.toMap(
                        VectorStoreWrapper.ScoredMemory::memoryId,
                        VectorStoreWrapper.ScoredMemory::similarity));

        List<Long> memoryIds = scoredMemories.stream()
                .map(VectorStoreWrapper.ScoredMemory::memoryId)
                .toList();

        Map<Long, LongTermMemory> memoryMap = repository.findAllById(memoryIds).stream()
                .collect(java.util.stream.Collectors.toMap(LongTermMemory::getId, m -> m));

        return memoryIds.stream()
                .map(memoryMap::get)
                .filter(java.util.Objects::nonNull)
                .filter(m -> m.getUserId().equals(userId))
                .filter(m -> m.getImportance() >= minImportance)
                .map(m -> MemoryDTO.fromEntity(m, scoreMap.get(m.getId())))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 语义召回记忆（带类型过滤和自定义相似度阈值）
     *
     * 适用于每轮对话的长期记忆注入场景：
     * - 使用较高的 minScore 过滤语义不相关的记忆
     * - 根据意图类型只召回相关类别的记忆，避免上下文污染
     *
     * @param userId          用户 ID
     * @param query           查询文本
     * @param topK            返回数量上限
     * @param minScore        相似度最低阈值（0~1）
     * @param requiredTypes   需要召回的记忆类型白名单（空表示不限制）
     * @param excludedTypes   排除的记忆类型黑名单（空表示不排除）
     * @return 相关记忆列表（带 score 字段）
     */
    @Transactional(readOnly = true)
    public List<MemoryDTO> recall(String userId, String query, int topK, double minScore,
                                   Set<MemoryType> requiredTypes, Set<MemoryType> excludedTypes) {
        List<MemoryDTO> results = recall(userId, query, topK, minScore);

        if ((requiredTypes == null || requiredTypes.isEmpty())
                && (excludedTypes == null || excludedTypes.isEmpty())) {
            return results;
        }

        return results.stream()
                .filter(dto -> {
                    MemoryType type = dto.getMemoryType();
                    if (type == null) {
                        return true; // 无法判断类型时保留
                    }
                    // 黑名单优先：排除指定类型
                    if (excludedTypes != null && excludedTypes.contains(type)) {
                        return false;
                    }
                    // 白名单：只保留指定类型
                    if (requiredTypes != null && !requiredTypes.isEmpty()) {
                        return requiredTypes.contains(type);
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    /**
     * 语义召回记忆（带类型过滤）
     *
     * @param userId 用户 ID
     * @param query  查询文本
     * @param topK   返回数量上限
     * @param types  记忆类型白名单
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

    /**
     * 更新记忆
     *
     * 事务一致性：
     * - 更新数据库记录
     * - 更新向量索引
     *
     * @param id  记忆 ID
     * @param dto 更新的记忆数据
     * @return 更新后的记忆 DTO
     */
    @Transactional
    public MemoryDTO update(Long id, MemoryDTO dto) {
        Optional<LongTermMemory> optionalMemory = repository.findById(id);
        if (optionalMemory.isEmpty()) {
            throw new RuntimeException("Memory not found with id: " + id);
        }

        LongTermMemory entity = optionalMemory.get();

        if (dto.getContent() != null) {
            entity.setContent(dto.getContent());
        }
        if (dto.getType() != null) {
            try {
                entity.setType(MemoryType.valueOf(dto.getType().toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid memory type: {}, using KNOWLEDGE as default", dto.getType());
                entity.setType(MemoryType.KNOWLEDGE);
            }
        }
        if (dto.getImportance() != null) {
            entity.setImportance(dto.getImportance());
        }
        if (dto.getConfidence() != null) {
            entity.setConfidence(dto.getConfidence());
        }
        if (dto.getSource() != null) {
            entity.setSource(dto.getSource());
        }

        entity = repository.save(entity);

        // 更新向量索引
        vectorStoreWrapper.remove(entity.getUserId(), id);
        vectorStoreWrapper.add(entity.getUserId(), entity.getContent(), entity.getId());

        log.info("Updated long-term memory: id={}, userId={}", id, entity.getUserId());

        return MemoryDTO.fromEntity(entity);
    }

    @Transactional(readOnly = true)
    public List<MemoryDTO> findByUserIdAndMinImportance(String userId, Integer minImportance) {
        return repository.findByUserIdAndMinImportance(userId, minImportance).stream()
                .map(MemoryDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 检查是否存在与给定内容语义相似的记忆（向量余弦相似度 ≥ 阈值）
     *
     * 用于记忆提取时的语义去重：当新提取的记忆与已有记忆语义高度相似时，
     * 拒绝存储，避免同一事实以不同表述重复入库。
     *
     * @param userId  用户 ID
     * @param content 待检查的记忆内容
     * @param threshold 相似度阈值（0~1），高于此值判定为相似
     * @return 是否存在相似记忆
     */
    @Transactional(readOnly = true)
    public boolean hasSimilarMemory(String userId, String content, double threshold) {
        List<VectorStoreWrapper.ScoredMemory> matches = vectorStoreWrapper.searchWithScore(
                userId, content, Integer.MAX_VALUE, threshold);
        return !matches.isEmpty();
    }

    /**
     * 查找与给定内容语义相似的记忆条目
     *
     * @param userId  用户 ID
     * @param content 待查询内容
     * @param threshold 相似度阈值
     * @param topK    最大返回数量
     * @return 相似记忆列表（带 score 字段）
     */
    @Transactional(readOnly = true)
    public List<MemoryDTO> findBySimilarity(String userId, String content, double threshold, int topK) {
        List<VectorStoreWrapper.ScoredMemory> scoredMemories = vectorStoreWrapper.searchWithScore(
                userId, content, topK, threshold);

        if (scoredMemories.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, Double> scoreMap = scoredMemories.stream()
                .collect(java.util.stream.Collectors.toMap(
                        VectorStoreWrapper.ScoredMemory::memoryId,
                        VectorStoreWrapper.ScoredMemory::similarity));

        List<Long> memoryIds = scoredMemories.stream()
                .map(VectorStoreWrapper.ScoredMemory::memoryId)
                .toList();

        Map<Long, LongTermMemory> memoryMap = repository.findAllById(memoryIds).stream()
                .collect(java.util.stream.Collectors.toMap(LongTermMemory::getId, m -> m));

        return memoryIds.stream()
                .map(memoryMap::get)
                .filter(java.util.Objects::nonNull)
                .filter(m -> m.getUserId().equals(userId))
                .map(m -> MemoryDTO.fromEntity(m, scoreMap.get(m.getId())))
                .collect(java.util.stream.Collectors.toList());
    }

    @Transactional(readOnly = true)
    public int countByUserId(String userId) {
        return repository.countByUserId(userId);
    }

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
                .source("手动写入")
                .build();
        repository.save(memory);
        vectorStoreWrapper.add(userId, content, memory.getId());
        log.debug("Stored long-term memory for user: {}", userId);
    }

    public List<String> retrieve(String userId) {
        List<LongTermMemory> memories = repository.findByUserIdOrderByCreatedAtDesc(userId);
        return memories.stream()
                .map(LongTermMemory::getContent)
                .toList();
    }

    public List<String> retrieve(String userId, String type) {
        MemoryType memoryType;
        try {
            memoryType = MemoryType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return new ArrayList<>();
        }
        List<LongTermMemory> memories = repository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, memoryType);
        return memories.stream()
                .map(LongTermMemory::getContent)
                .toList();
    }
}
