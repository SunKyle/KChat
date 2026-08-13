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

            // Path 1: Dense Retrieval (本地向量检索)
            int denseTopK = 20; // 扩大候选集，后续精排筛选
            List<MemoryDTO> denseResults = longTermMemoryService.recall(
                    ctx.getUserId(), recallQuery, denseTopK, minScore);

            // Path 2: Sparse Retrieval (关键词检索)
            List<KeywordRetriever.KeywordMatch> keywordMatches = keywordRetriever.search(
                    ctx.getUserId(), recallQuery, denseTopK);

            Set<Long> keywordHitIds = keywordMatches.stream()
                    .map(KeywordRetriever.KeywordMatch::memoryId)
                    .collect(java.util.stream.Collectors.toSet());

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
                    Map<Long, Double> keywordScoreMap = keywordMatches.stream()
                            .collect(java.util.stream.Collectors.toMap(
                                    KeywordRetriever.KeywordMatch::memoryId,
                                    KeywordRetriever.KeywordMatch::score,
                                    (a, b) -> a));
                    for (MemoryDTO dto : sparseResults) {
                        Double kwScore = keywordScoreMap.get(dto.getId());
                        if (kwScore != null) {
                            dto.setScore(kwScore);
                        }
                    }
                }
            }

            // Path 3: Cognee Graph Retrieval (图谱增强检索)
            // Pass conversationId as session_id so recall searches session cache first
            // (source="session"), then falls back to permanent graph (source="graph").
            List<MemoryDTO> cogneeResults = Collections.emptyList();
            if (cogneeProperties.isEnabled()) {
                cogneeResults = fetchCogneeAsMemoryDtos(
                        ctx.getUserId(), ctx.getUserMessage(),
                        cogneeProperties.getSearch().getTopK(),
                        ctx.getConversationId());
            }

            // ── Stage 3.5: Merge all recall paths ─────────────
            // mergeAndDeduplicate unifies by memory id (dense + sparse use real JPA ids,
            // cognee uses negative sentinel ids so they survive the merge step).
            List<MemoryDTO> merged = memoryReranker.mergeAndDeduplicate(
                    denseResults, sparseResults, cogneeResults);

            // ── Stage 4: Relevance Ranking (统一规则精排) ──────
            int finalTopK = 5;
            List<MemoryDTO> reranked;

            if (!keywordMatches.isEmpty() && !merged.isEmpty()) {
                reranked = memoryReranker.rerank(merged, keywordMatches, finalTopK + 5);
            } else {
                reranked = memoryReranker.rerankDenseOnly(merged, finalTopK + 5);
            }

            // Cross-source content-level dedup: remove cognee entries whose text is
            // substring-equal (ignoring whitespace/case) to any local JPA result.
            // This guarantees we don't double-inject the same fact just because it
            // was indexed in both stores.
            List<MemoryDTO> deduped = deduplicateAcrossSources(reranked);

            // Cap to final top-K after cross-source dedup.
            if (deduped.size() > finalTopK) {
                deduped = deduped.subList(0, finalTopK);
            }

            // ── 应用类型过滤 ──────────────────────────────────
            List<MemoryDTO> finalResults = applyTypeFilter(deduped, requiredTypes, excludedTypes);

            // ── 日志 ──────────────────────────────────────────
            long localCount = finalResults.stream()
                    .filter(m -> m.getId() == null || m.getId() >= 0).count();
            long cogneeCount = finalResults.size() - localCount;
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
                                "cogneeHits={} merged={} reranked={} " +
                                "finalHits={} (local={}, cognee={}) topScore={} types={} " +
                                "filteredByIntent={}",
                        truncate(recallQuery, 50),
                        denseResults.size(), keywordMatches.size(), cogneeResults.size(),
                        merged.size(), reranked.size(),
                        finalResults.size(), localCount, cogneeCount, topScore, types,
                        analysis != null);
            } else {
                log.info("[LongTermMemory] query='{}' denseHits={} keywordHits={} " +
                                "cogneeHits={} finalHits=0 minScore={} filteredByIntent={}",
                        truncate(recallQuery, 50),
                        denseResults.size(), keywordMatches.size(), cogneeResults.size(),
                        minScore, analysis != null);
            }
            ctx.setLongTermMemory(finalResults);

        } catch (Exception e) {
            log.warn("Long-term memory recall failed: {}", e.getMessage(), e);
            ctx.setLongTermMemory(new ArrayList<>());
        }
    }

    /**
     * Fetch relevant memories from Cognee and adapt them into MemoryDTOs so they can
     * participate in the unified merge + rerank pipeline.
     *
     * <p>Cognee results don't have a JPA memory id, so we assign deterministic negative
     * sentinel ids that stay stable per content hash. This prevents mergeAndDeduplicate
     * (which groups by id) from dropping them, while also avoiding collisions with real
     * JPA primary keys (which are always positive).
     */
    private List<MemoryDTO> fetchCogneeAsMemoryDtos(String userId, String query, int topK, String sessionId) {
        // v1.0 recall() — auto-routed retrieval with source tags.
        // When sessionId is provided, recall searches session cache first (source="session"),
        // then falls back to permanent graph (source="graph").
        List<CogneeClient.RecallResult> recalled = cogneeClient.recall(userId, query, topK, sessionId);
        if (recalled.isEmpty()) return List.of();

        List<MemoryDTO> out = new ArrayList<>(recalled.size());
        int seq = 0;
        for (CogneeClient.RecallResult item : recalled) {
            String text = item.text();
            if (text == null || text.isBlank()) continue;

            // Derive a stable pseudo-id (negative, never collides with real JPA ids).
            long pseudoId = -(10_000L + Math.abs(text.hashCode()) % 9_000L) - seq;
            out.add(MemoryDTO.builder()
                    .id(pseudoId)
                    .userId(userId)
                    .content(text)
                    .score(item.score())
                    .type("KNOWLEDGE")
                    .importance(6)
                    // Tag the source (graph/session/trace) so logs and downstream
                    // stages can distinguish where each result came from.
                    .source("cognee:" + item.source())
                    .build());
            seq++;
        }
        return out;
    }

    /**
     * Content-level deduplication across recall sources.
     *
     * <p>mergeAndDeduplicate only deduplicates by memory id, which works for JPA memories.
     * Cognee entries have their own pseudo-ids, so if the same fact lives in both stores
     * (a JPA KNOWLEDGE entry that was also cognified), two copies would survive id-based
     * merging. This pass removes the cognee copy whose normalized content is already
     * present in a non-cognee entry (id >= 0 or null).
     *
     * <p>Input is assumed to be already sorted by score descending; we keep the earlier
     * (higher-scored) copy for ties.
     */
    private List<MemoryDTO> deduplicateAcrossSources(List<MemoryDTO> reranked) {
        if (reranked == null || reranked.isEmpty()) return reranked;

        Set<String> seenLocal = new java.util.HashSet<>();
        List<MemoryDTO> out = new ArrayList<>(reranked.size());

        for (MemoryDTO dto : reranked) {
            String key = normalizeForContentDedup(dto.getContent());
            if (key.isEmpty()) {
                out.add(dto);
                continue;
            }
            boolean isCogneeEntry = (dto.getId() != null && dto.getId() < 0)
                    || (dto.getSource() != null && dto.getSource().startsWith("cognee"));
            if (isCogneeEntry && seenLocal.contains(key)) {
                // Same content as an earlier local memory — drop the cognee duplicate.
                log.debug("[LongTermMemory] Dedup: dropping cognee entry '{}' " +
                        "(already present in local results)", dto.getContent());
                continue;
            }
            if (!isCogneeEntry) {
                seenLocal.add(key);
            }
            out.add(dto);
        }
        return out;
    }

    private static String normalizeForContentDedup(String content) {
        if (content == null) return "";
        return content.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
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