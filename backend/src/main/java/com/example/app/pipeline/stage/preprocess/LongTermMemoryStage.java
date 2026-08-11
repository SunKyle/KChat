package com.example.app.pipeline.stage.preprocess;

import com.example.app.config.CogneeProperties;
import com.example.app.config.MemoryExtractorConfig;
import com.example.app.config.VectorStoreConfig;
import com.example.app.dto.MemoryDTO;
import com.example.app.dto.QueryAnalysisResult;
import com.example.app.entity.LongTermMemory.MemoryType;
import com.example.app.memory.KeywordRetriever;
import com.example.app.memory.MemoryReranker;
import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.service.CogneeClient;
import com.example.app.service.LongTermMemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class LongTermMemoryStage implements ContextPipelineStage {

    private final LongTermMemoryService longTermMemoryService;
    private final CogneeClient cogneeClient;
    private final CogneeProperties cogneeProperties;
    private final VectorStoreConfig vectorStoreConfig;
    private final MemoryExtractorConfig memoryExtractorConfig;
    private final KeywordRetriever keywordRetriever;
    private final MemoryReranker memoryReranker;

    @Override
    public Phase getPhase() {
        return Phase.PREPROCESS;
    }

    public String getName() {
        return "longTermMemoryStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        try {
            double minScore = vectorStoreConfig.getRecallMinScore();
            QueryAnalysisResult analysis = ctx.getQueryAnalysisResult();

            // ── Stage 5: Memory Gating ──────────────────────────
            if (analysis != null && !analysis.isRequiresMemory()
                    && memoryExtractorConfig.isIntentGatingEnabled()) {
                log.info("[LongTermMemory] Gating: skip memory injection for intent={} query='{}'",
                        analysis.getIntentType(), truncate(ctx.getUserMessage(), 50));
                ctx.setLongTermMemory(new ArrayList<>());
                return;
            }

            // ── Stage 1+2: Query Understanding + Memory Selection ─
            String recallQuery;
            Set<MemoryType> requiredTypes = Collections.emptySet();
            Set<MemoryType> excludedTypes = Collections.emptySet();

            if (analysis != null) {
                recallQuery = analysis.getEffectiveQuery(ctx.getUserMessage());
                requiredTypes = analysis.getRequiredTypes() != null
                        ? analysis.getRequiredTypes() : Collections.emptySet();
                excludedTypes = analysis.getExcludedTypes() != null
                        ? analysis.getExcludedTypes() : Collections.emptySet();
            } else {
                recallQuery = ctx.getUserMessage();
            }

            // ── Stage 3: Multi-Strategy Retrieval ──────────────

            // Path 1: Dense Retrieval (向量检索)
            int denseTopK = 20; // 扩大候选集，后续精排筛选
            List<MemoryDTO> denseResults = longTermMemoryService.recall(
                    ctx.getUserId(), recallQuery, denseTopK, minScore);

            // Path 2: Sparse Retrieval (关键词检索)
            List<KeywordRetriever.KeywordMatch> keywordMatches = keywordRetriever.search(
                    ctx.getUserId(), recallQuery, denseTopK);

            // 关键词匹配到的记忆 ID
            Set<Long> keywordHitIds = keywordMatches.stream()
                    .map(KeywordRetriever.KeywordMatch::memoryId)
                    .collect(java.util.stream.Collectors.toSet());

            // 加载关键词命中的记忆详情（如果不在 dense 结果中）
            List<MemoryDTO> sparseResults = new ArrayList<>();
            if (!keywordHitIds.isEmpty()) {
                Set<Long> denseIds = denseResults.stream()
                        .map(MemoryDTO::getId)
                        .filter(Objects::nonNull)
                        .collect(java.util.stream.Collectors.toSet());

                List<Long> missingIds = keywordHitIds.stream()
                        .filter(id -> !denseIds.contains(id))
                        .toList();

                if (!missingIds.isEmpty()) {
                    sparseResults = longTermMemoryService.findByIds(missingIds);
                    // 为 sparse 结果设置关键词匹配分数
                    Map<Long, Double> keywordScoreMap = keywordMatches.stream()
                            .collect(java.util.stream.Collectors.toMap(
                                    KeywordRetriever.KeywordMatch::memoryId,
                                    KeywordRetriever.KeywordMatch::score,
                                    (a, b) -> a));
                    for (MemoryDTO dto : sparseResults) {
                        Double kwScore = keywordScoreMap.get(dto.getId());
                        if (kwScore != null) {
                            // 用关键词匹配分数作为初始 score
                            dto.setScore(kwScore);
                        }
                    }
                }
            }

            // 合并多路结果
            List<MemoryDTO> merged = memoryReranker.mergeAndDeduplicate(denseResults, sparseResults);

            // ── Stage 4: Relevance Ranking (规则精排) ──────────
            int finalTopK = 5;
            List<MemoryDTO> reranked;

            if (!keywordMatches.isEmpty() && !merged.isEmpty()) {
                reranked = memoryReranker.rerank(merged, keywordMatches, finalTopK);
            } else {
                reranked = memoryReranker.rerankDenseOnly(merged, finalTopK);
            }

            // ── 应用类型过滤 ──────────────────────────────────
            List<MemoryDTO> finalResults = applyTypeFilter(reranked, requiredTypes, excludedTypes);

            // ── 日志 ──────────────────────────────────────────
            if (!finalResults.isEmpty()) {
                double topScore = finalResults.stream()
                        .mapToDouble(m -> m.getScore() != null ? m.getScore() : 0.0)
                        .max().orElse(0.0);
                List<String> types = finalResults.stream()
                        .map(MemoryDTO::getType)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
                log.info("[LongTermMemory] query='{}' denseHits={} keywordHits={} " +
                                "merged={} reranked={} finalHits={} topScore={} types={} " +
                                "filteredByIntent={} keywordMatched={}",
                        truncate(recallQuery, 50),
                        denseResults.size(), keywordMatches.size(),
                        merged.size(), reranked.size(),
                        finalResults.size(), topScore, types,
                        analysis != null, !keywordMatches.isEmpty());
            } else {
                log.info("[LongTermMemory] query='{}' denseHits={} keywordHits={} " +
                                "finalHits=0 minScore={} filteredByIntent={}",
                        truncate(recallQuery, 50),
                        denseResults.size(), keywordMatches.size(),
                        minScore, analysis != null);
            }
            ctx.setLongTermMemory(finalResults);

            // Also search Cognee for relevant memories and merge the results
            if (cogneeProperties.isEnabled()) {
                List<String> cogneeResults = cogneeClient.search(
                        ctx.getUserId(), ctx.getUserMessage(), 5);

                if (!cogneeResults.isEmpty()) {
                    List<MemoryDTO> combined = new ArrayList<>(finalResults);
                    for (int i = 0; i < cogneeResults.size(); i++) {
                        combined.add(MemoryDTO.builder()
                                .content(cogneeResults.get(i))
                                .type("KNOWLEDGE")
                                .userId(ctx.getUserId())
                                .importance(5)
                                .build());
                    }
                    ctx.setLongTermMemory(combined);
                    log.info("[LongTermMemoryStage] Merged {} Cognee results with {} local memories",
                            cogneeResults.size(), finalResults.size());
                }
            }
        } catch (Exception e) {
            log.warn("Long-term memory recall failed: {}", e.getMessage(), e);
            ctx.setLongTermMemory(new ArrayList<>());
        }
    }

    @Override
    public int getOrder() {
        return 310;
    }

    @Override
    public boolean isCritical() {
        return false;
    }

    /**
     * 应用类型过滤：白名单 + 黑名单
     */
    private List<MemoryDTO> applyTypeFilter(List<MemoryDTO> memories,
                                             Set<MemoryType> requiredTypes,
                                             Set<MemoryType> excludedTypes) {
        if ((requiredTypes == null || requiredTypes.isEmpty())
                && (excludedTypes == null || excludedTypes.isEmpty())) {
            return memories;
        }

        return memories.stream()
                .filter(dto -> {
                    MemoryType type = dto.getMemoryType();
                    if (type == null) {
                        return true;
                    }
                    if (excludedTypes != null && excludedTypes.contains(type)) {
                        return false;
                    }
                    if (requiredTypes != null && !requiredTypes.isEmpty()) {
                        return requiredTypes.contains(type);
                    }
                    return true;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}