package com.example.app.memory;

import com.example.app.dto.MemoryDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 记忆精排器（Relevance Ranking）
 *
 * <p>对粗召回结果做规则精排，综合考虑：
 * <ul>
 *   <li>向量相似度（Dense Score）：语义相关度</li>
 *   <li>关键词匹配度（Sparse Score）：精确匹配度</li>
 *   <li>时间衰减（Temporal Decay）：时效性</li>
 *   <li>重要性（Importance）：人工标注的重要程度</li>
 * </ul>
 *
 * <p>公式：
 * <pre>
 * final_score = 0.4 * dense_similarity
 *             + 0.3 * keyword_match_score
 *             + 0.2 * temporal_decay
 *             + 0.1 * importance_normalized
 * </pre>
 */
@Component
@Slf4j
public class MemoryReranker {

    /** 向量相似度权重 */
    private static final double W_DENSE = 0.4;
    /** 关键词匹配权重 */
    private static final double W_KEYWORD = 0.3;
    /** 时间衰减权重 */
    private static final double W_TEMPORAL = 0.2;
    /** 重要性权重 */
    private static final double W_IMPORTANCE = 0.1;

    /** 时间衰减半衰期（天）：30 天前的记忆衰减到 0.5 */
    private static final double HALF_LIFE_DAYS = 30.0;

    /**
     * 精排：根据多路召回结果综合打分，选出 topK 最相关的记忆
     *
     * @param memories       候选记忆列表（来自向量检索）
     * @param keywordMatches 关键词匹配结果（来自关键词检索）
     * @param topK           返回数量上限
     * @return 精排后的记忆列表（带更新后的 score）
     */
    public List<MemoryDTO> rerank(List<MemoryDTO> memories,
                                   List<KeywordRetriever.KeywordMatch> keywordMatches,
                                   int topK) {
        if (memories == null || memories.isEmpty()) {
            return Collections.emptyList();
        }

        // 构建关键词匹配 Map: memoryId → score
        Map<Long, Double> keywordScoreMap = new HashMap<>();
        if (keywordMatches != null) {
            for (KeywordRetriever.KeywordMatch match : keywordMatches) {
                keywordScoreMap.put(match.memoryId(), match.score());
            }
        }

        LocalDateTime now = LocalDateTime.now();

        // 为每条记忆计算综合分
        List<RerankedMemory> ranked = memories.stream()
                .map(dto -> {
                    double denseScore = dto.getScore() != null ? dto.getScore() : 0.0;
                    double keywordScore = keywordScoreMap.getOrDefault(dto.getId(), 0.0);
                    double temporalScore = computeTemporalDecay(dto, now);
                    double importanceScore = computeImportanceScore(dto);

                    double finalScore = W_DENSE * denseScore
                            + W_KEYWORD * keywordScore
                            + W_TEMPORAL * temporalScore
                            + W_IMPORTANCE * importanceScore;

                    return new RerankedMemory(dto, finalScore, denseScore, keywordScore,
                            temporalScore, importanceScore);
                })
                .sorted((a, b) -> Double.compare(b.finalScore, a.finalScore))
                .limit(topK)
                .toList();

        // 更新 DTO 的 score 为精排后的综合分
        return ranked.stream()
                .map(r -> {
                    MemoryDTO dto = r.memory();
                    dto.setScore(r.finalScore());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * 简化版精排：仅基于向量相似度 + 时间衰减 + 重要性（无关键词匹配）
     */
    public List<MemoryDTO> rerankDenseOnly(List<MemoryDTO> memories, int topK) {
        if (memories == null || memories.isEmpty()) {
            return Collections.emptyList();
        }

        LocalDateTime now = LocalDateTime.now();

        List<RerankedMemory> ranked = memories.stream()
                .map(dto -> {
                    double denseScore = dto.getScore() != null ? dto.getScore() : 0.0;
                    double temporalScore = computeTemporalDecay(dto, now);
                    double importanceScore = computeImportanceScore(dto);

                    // 无关键词匹配时：dense 权重更大
                    double finalScore = 0.6 * denseScore + 0.25 * temporalScore + 0.15 * importanceScore;

                    return new RerankedMemory(dto, finalScore, denseScore, 0.0,
                            temporalScore, importanceScore);
                })
                .sorted((a, b) -> Double.compare(b.finalScore, a.finalScore))
                .limit(topK)
                .toList();

        return ranked.stream()
                .map(r -> {
                    MemoryDTO dto = r.memory();
                    dto.setScore(r.finalScore());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * 计算时间衰减分数（0~1）
     *
     * <p>公式：exp(-days / HALF_LIFE_DAYS)
     * <p>7 天前：0.79；30 天前：0.50；90 天前：0.14
     */
    private double computeTemporalDecay(MemoryDTO dto, LocalDateTime now) {
        LocalDateTime date = dto.getUpdatedAt() != null ? dto.getUpdatedAt() : dto.getCreatedAt();
        if (date == null) {
            return 0.5; // 无时间信息时给予中等分
        }

        long days = ChronoUnit.DAYS.between(date, now);
        if (days < 0) {
            days = 0;
        }

        return Math.exp(-(double) days / HALF_LIFE_DAYS);
    }

    /**
     * 计算重要性归一化分数（0~1）
     *
     * <p>importance 范围 1~10，归一化到 0.1~1.0
     */
    private double computeImportanceScore(MemoryDTO dto) {
        Integer importance = dto.getImportance();
        if (importance == null || importance <= 0) {
            return 0.3;
        }
        return Math.min(1.0, importance / 10.0);
    }

    /**
     * 计算关键词匹配分数
     *
     * @param memoryContent 记忆内容
     * @param queryKeywords 查询关键词列表
     * @return 匹配分数 (0~1)
     */
    public double computeKeywordMatchScore(String memoryContent, List<String> queryKeywords) {
        if (memoryContent == null || queryKeywords == null || queryKeywords.isEmpty()) {
            return 0.0;
        }

        String contentLower = memoryContent.toLowerCase();
        int matchCount = 0;

        for (String keyword : queryKeywords) {
            if (contentLower.contains(keyword.toLowerCase())) {
                matchCount++;
            }
        }

        return (double) matchCount / queryKeywords.size();
    }

    /**
     * 合并多路召回结果（去重 + 保留最高分）
     *
     * @param lists 多路召回结果
     * @return 合并后的结果
     */
    @SafeVarargs
    public final List<MemoryDTO> mergeAndDeduplicate(List<MemoryDTO>... lists) {
        Map<Long, MemoryDTO> map = new LinkedHashMap<>();
        for (List<MemoryDTO> list : lists) {
            if (list == null) continue;
            for (MemoryDTO dto : list) {
                if (dto.getId() == null) continue;
                MemoryDTO existing = map.get(dto.getId());
                if (existing == null) {
                    map.put(dto.getId(), dto);
                } else {
                    // 保留分数更高的
                    double existingScore = existing.getScore() != null ? existing.getScore() : 0.0;
                    double newScore = dto.getScore() != null ? dto.getScore() : 0.0;
                    if (newScore > existingScore) {
                        map.put(dto.getId(), dto);
                    }
                }
            }
        }
        return new ArrayList<>(map.values());
    }

    // ── 内部记录 ──────────────────────────────────────────

    private record RerankedMemory(
            MemoryDTO memory,
            double finalScore,
            double denseScore,
            double keywordScore,
            double temporalScore,
            double importanceScore
    ) {
    }
}