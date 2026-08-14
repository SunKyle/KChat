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
import java.util.*;
import java.util.stream.Collectors;

/**
 * 长期记忆服务
 *
 * 核心职责：
 * - 管理用户长期记忆的 CRUD 操作（JPA 持久化）
 * - 语义去重（基于内容模糊匹配）
 * - 删除记忆后异步重建 Cognee 图谱
 *
 * 架构说明：
 * - 本地向量存储已移除，语义检索由 Cognee recall 负责
 * - 关键词检索（KeywordRetriever）基于 JPA SQL，不依赖向量存储
 * - 记忆提取时的去重降级为 content LIKE 匹配
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LongTermMemoryService {

    private final LongTermMemoryRepository repository;

    /**
     * Cognee 同步服务——删除记忆后异步重建 Cognee 图谱，避免"幽灵记忆"
     * （JPA 已删但 Cognee 里还留着节点/边/向量）。
     * Optional 包装是为了在 cognee=false 时不强制依赖该 Bean。
     */
    private final java.util.Optional<CogneeSyncService> cogneeSyncService;

    // ── 写入 ──────────────────────────────────────────────

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

        log.info("Saved long-term memory: id={}, userId={}, type={}",
                entity.getId(), entity.getUserId(), entity.getType());

        return MemoryDTO.fromEntity(entity);
    }

    @Transactional
    public List<MemoryDTO> saveAll(List<MemoryDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return new ArrayList<>();
        }

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

        log.info("Saved {} long-term memories for user {}",
                entities.size(), entities.get(0).getUserId());

        return entities.stream()
                .map(MemoryDTO::fromEntity)
                .collect(Collectors.toList());
    }

    // ── 查询 ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Optional<MemoryDTO> findById(Long id) {
        return repository.findById(id)
                .map(MemoryDTO::fromEntity);
    }

    /**
     * 批量按 ID 查找记忆
     *
     * <p>
     * 用于关键词检索命中后批量加载记忆详情。
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
     * 基于关键词的召回（替代原向量语义召回）
     *
     * <p>本地向量存储已移除，语义检索由 Cognee recall 负责。
     * 此方法仅做 JPA content LIKE 匹配，用于 MemoryTool 等需要本地召回的场景。
     *
     * @param userId 用户 ID
     * @param query  查询文本
     * @param topK   返回数量上限
     * @return 相关记忆列表（score 为基于关键词匹配度的估算值）
     */
    @Transactional(readOnly = true)
    public List<MemoryDTO> recall(String userId, String query, int topK) {
        return recall(userId, query, topK, 0.0);
    }

/**
     * 基于关键词的召回（带最低分数阈值）
     *
     * @param userId   用户 ID
     * @param query    查询文本
     * @param topK     返回数量上限
     * @param minScore 最低匹配分数（0~1），低于此值的记忆被过滤
     * @return 相关记忆列表
     */
    @Transactional(readOnly = true)
    public List<MemoryDTO> recall(String userId, String query, int topK, double minScore) {
        if (query == null || query.isBlank()) {
            return new ArrayList<>();
        }

        // 降级方案：基于 content LIKE 的关键词匹配
        List<LongTermMemory> all = repository.findByUserIdOrderByCreatedAtDesc(userId);
        String queryLower = query.toLowerCase();

        return all.stream()
                .filter(m -> m.getUserId().equals(userId))
                .map(m -> {
                    double score = computeKeywordScore(m.getContent(), queryLower);
                    return Map.entry(m, score);
                })
                .filter(e -> e.getValue() >= minScore)
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(topK)
                .map(e -> MemoryDTO.fromEntity(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

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
                    if (type == null) return true;
                    if (excludedTypes != null && excludedTypes.contains(type)) return false;
                    if (requiredTypes != null && !requiredTypes.isEmpty()) {
                        return requiredTypes.contains(type);
                    }
                    return true;
                })
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

    // ── 删除 ──────────────────────────────────────────────

    @Transactional
    public void deleteById(Long id) {
        Optional<LongTermMemory> memory = repository.findById(id);
        if (memory.isPresent()) {
            LongTermMemory entity = memory.get();
            String userId = entity.getUserId();
            repository.deleteById(id);
            log.info("Deleted long-term memory: id={}, userId={}", id, userId);
            // Asynchronously rebuild the cognee graph so no orphan nodes/edges remain.
            cogneeSyncService.ifPresent(svc -> svc.resyncUserMemories(userId));
        }
    }

    @Transactional
    public void deleteByUserId(String userId) {
        repository.deleteByUserId(userId);
        log.info("Deleted all long-term memories for user {}", userId);
        cogneeSyncService.ifPresent(svc -> svc.resyncUserMemories(userId));
    }

    @Transactional
    public int cleanExpired() {
        int deleted = repository.deleteByExpiresAtBefore(LocalDateTime.now());
        log.info("Cleaned {} expired long-term memories", deleted);
        return deleted;
    }

    // ── 更新 ──────────────────────────────────────────────

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

        log.info("Updated long-term memory: id={}, userId={}", id, entity.getUserId());

        return MemoryDTO.fromEntity(entity);
    }

    // ── 其他查询 ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<MemoryDTO> findByUserIdAndMinImportance(String userId, Integer minImportance) {
        return repository.findByUserIdAndMinImportance(userId, minImportance).stream()
                .map(MemoryDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 检查是否存在内容相似的记忆（降级为 content LIKE 匹配）
     *
     * <p>本地向量存储已移除，原基于向量余弦相似度的语义去重降级为
     * 基于内容子串匹配的精确去重。虽然不如向量相似度精确，
     * 但足以防止完全相同或高度相似的记忆重复入库。
     *
     * @param userId    用户 ID
     * @param content   待检查的记忆内容
     * @param threshold 相似度阈值（0~1），降级模式下按内容长度重叠比例判断
     * @return 是否存在相似记忆
     */
    @Transactional(readOnly = true)
    public boolean hasSimilarMemory(String userId, String content, double threshold) {
        if (content == null || content.isBlank()) {
            return false;
        }

        List<LongTermMemory> existing = repository.findByUserIdOrderByCreatedAtDesc(userId);
        String contentLower = content.toLowerCase();

        for (LongTermMemory m : existing) {
            if (m.getContent() == null) continue;
            String existingLower = m.getContent().toLowerCase();
            // 精确匹配或包含关系 → 判定为相似
            if (existingLower.equals(contentLower)
                    || existingLower.contains(contentLower)
                    || contentLower.contains(existingLower)) {
                return true;
            }
            // 高阈值（≥0.8）时，检查内容重叠比例
            if (threshold >= 0.8) {
                double overlap = computeContentOverlap(contentLower, existingLower);
                if (overlap >= threshold) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 查找与给定内容相似的记忆条目（降级为 content LIKE 匹配）
     */
    @Transactional(readOnly = true)
    public List<MemoryDTO> findBySimilarity(String userId, String content, double threshold, int topK) {
        if (content == null || content.isBlank()) {
            return new ArrayList<>();
        }

        List<LongTermMemory> all = repository.findByUserIdOrderByCreatedAtDesc(userId);
        String contentLower = content.toLowerCase();

        return all.stream()
                .filter(m -> m.getUserId().equals(userId))
                .map(m -> Map.entry(m, computeKeywordScore(m.getContent(), contentLower)))
                .filter(e -> e.getValue() >= threshold)
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(topK)
                .map(e -> MemoryDTO.fromEntity(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public int countByUserId(String userId) {
        return repository.countByUserId(userId);
    }

    // ── 简便方法 ──────────────────────────────────────────

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

    // ── 内部工具 ──────────────────────────────────────────

    /**
     * 基于关键词重叠度计算匹配分数（0~1）
     *
     * <p>简单的 TF 匹配：query 中的词在 content 中出现的比例。
     * 不如向量相似度精确，但足以支持关键词召回场景。
     */
    private double computeKeywordScore(String content, String queryLower) {
        if (content == null || queryLower == null) return 0.0;

        String contentLower = content.toLowerCase();
        if (contentLower.contains(queryLower)) {
            return 1.0; // 完全包含查询
        }

        // 分词匹配
        String[] queryWords = queryLower.split("\\s+");
        int matchCount = 0;
        for (String word : queryWords) {
            if (word.length() >= 2 && contentLower.contains(word)) {
                matchCount++;
            }
        }
        return queryWords.length > 0 ? (double) matchCount / queryWords.length : 0.0;
    }

    /**
     * 计算两个字符串的内容重叠比例（0~1）
     *
     * <p>基于字符级 Jaccard 相似度的简化版本，用于 hasSimilarMemory 的高阈值判断。
     */
    private double computeContentOverlap(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return 0.0;

        // 短文本直接比较
        int minLen = Math.min(a.length(), b.length());
        int maxLen = Math.max(a.length(), b.length());

        // 计算最长公共子串比例
        int lcs = longestCommonSubstring(a, b);
        return (double) lcs / maxLen;
    }

    private int longestCommonSubstring(String a, String b) {
        int m = a.length();
        int n = b.length();
        int max = 0;
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                    if (dp[i][j] > max) max = dp[i][j];
                }
            }
        }
        return max;
    }
}
