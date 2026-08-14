package com.example.app.pipeline.stage.preprocess;

import com.example.app.config.CogneeProperties;
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
import java.util.regex.Pattern;

/**
 * 长期记忆检索阶段（阶段 310）
 *
 * <p>架构：双源独立召回，结果分别存入 Context，在格式化阶段分块注入。
 * <ul>
 *   <li>Path A: JPA 结构化召回 — 关键词检索 + JPA 精排，按类型分层 (L1/L2/L3)</li>
 *   <li>Path B: Cognee 语义召回 — recallWithContext() 返回片段 + 实体 + 关系</li>
 * </ul>
 *
 * <p>设计原则：默认始终召回，只跳过明确的纯数学计算场景。
 * 意图分类仅用于排序/过滤（决定优先召回哪些类型），不用于门控。
 * 意图识别可能不准确，但漏召的代价远高于多召一条无关记忆。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LongTermMemoryStage implements ContextPipelineStage {

    private final LongTermMemoryService longTermMemoryService;
    private final CogneeClient cogneeClient;
    private final CogneeProperties cogneeProperties;
    private final KeywordRetriever keywordRetriever;
    private final MemoryReranker memoryReranker;

    /** 纯数学计算 — 唯一可靠的非记忆场景，可直接跳过 */
    private static final Pattern PURE_MATH_PATTERN = Pattern.compile(
            "^\\s*\\d+(\\.\\d+)?\\s*[\\+\\-\\*\\/×÷]\\s*\\d+(\\.\\d+)?\\s*=?\\s*\\??\\s*$");

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
            String userMessage = ctx.getUserMessage();

            // ── 唯一可靠的跳过场景：纯数学计算 ─────────────────
            if (userMessage != null && PURE_MATH_PATTERN.matcher(userMessage.trim()).matches()) {
                log.info("[LongTermMemory] Skip memory for pure math: '{}'", truncate(userMessage, 50));
                ctx.setJpaMemories(Map.of("l1", List.of(), "l2", List.of(), "l3", List.of()));
                ctx.setCogneeContext(new ConversationContext.CogneeContext(
                        List.of(), List.of(), List.of()));
                ctx.setLongTermMemory(new ArrayList<>());
                return;
            }

            // ── Query Understanding ────────────────────────────
            QueryAnalysisResult analysis = ctx.getQueryAnalysisResult();
            String recallQuery;
            Set<MemoryType> requiredTypes = Collections.emptySet();
            Set<MemoryType> excludedTypes = Collections.emptySet();

            if (analysis != null) {
                recallQuery = analysis.getEffectiveQuery(userMessage);
                requiredTypes = analysis.getRequiredTypes() != null
                        ? analysis.getRequiredTypes() : Collections.emptySet();
                excludedTypes = analysis.getExcludedTypes() != null
                        ? analysis.getExcludedTypes() : Collections.emptySet();
            } else {
                recallQuery = userMessage;
            }

            // ── Path A: JPA 结构化召回 ─────────────────────────
            Map<String, List<MemoryDTO>> jpaMemories = retrieveJpaMemories(
                    ctx, recallQuery, requiredTypes, excludedTypes);
            ctx.setJpaMemories(jpaMemories);

            // ── Path B: Cognee 语义召回 ────────────────────────
            ConversationContext.CogneeContext cogneeCtx = retrieveCogneeContext(
                    ctx, recallQuery);
            ctx.setCogneeContext(cogneeCtx);

            // ── Legacy longTermMemory (backward compat) ────────
            List<MemoryDTO> legacyFlat = new ArrayList<>();
            legacyFlat.addAll(jpaMemories.getOrDefault("l1", List.of()));
            legacyFlat.addAll(jpaMemories.getOrDefault("l2", List.of()));
            legacyFlat.addAll(jpaMemories.getOrDefault("l3", List.of()));
            ctx.setLongTermMemory(legacyFlat);

            // ── Logging ────────────────────────────────────────
            int l1size = jpaMemories.getOrDefault("l1", List.of()).size();
            int l2size = jpaMemories.getOrDefault("l2", List.of()).size();
            int l3size = jpaMemories.getOrDefault("l3", List.of()).size();
            int cogneeFragments = cogneeCtx.fragments() != null ? cogneeCtx.fragments().size() : 0;
            int cogneeEntities = cogneeCtx.entities() != null ? cogneeCtx.entities().size() : 0;
            int cogneeRelations = cogneeCtx.relations() != null ? cogneeCtx.relations().size() : 0;

            log.info("[LongTermMemory] query='{}' jpa(l1={},l2={},l3={}) cognee(f={},e={},r={})",
                    truncate(recallQuery, 50),
                    l1size, l2size, l3size,
                    cogneeFragments, cogneeEntities, cogneeRelations);

        } catch (Exception e) {
            log.warn("Long-term memory recall failed: {}", e.getMessage(), e);
            ctx.setJpaMemories(Map.of("l1", List.of(), "l2", List.of(), "l3", List.of()));
            ctx.setCogneeContext(new ConversationContext.CogneeContext(
                    List.of(), List.of(), List.of()));
            ctx.setLongTermMemory(new ArrayList<>());
        }
    }

    /**
     * Path A: JPA 结构化召回
     *
     * <p>关键词检索 → JPA 加载 → JPA 内部精排 → 按类型分层 (L1/L2/L3)
     */
    private Map<String, List<MemoryDTO>> retrieveJpaMemories(
            ConversationContext ctx, String recallQuery,
            Set<MemoryType> requiredTypes, Set<MemoryType> excludedTypes) {

        int topK = 20;

        // Step 1: 关键词检索
        List<KeywordRetriever.KeywordMatch> keywordMatches = keywordRetriever.search(
                ctx.getUserId(), recallQuery, topK);

        if (keywordMatches.isEmpty()) {
            return Map.of("l1", List.of(), "l2", List.of(), "l3", List.of());
        }

        // Step 2: 加载 JPA 实体
        Set<Long> keywordHitIds = keywordMatches.stream()
                .map(KeywordRetriever.KeywordMatch::memoryId)
                .collect(java.util.stream.Collectors.toSet());
        List<MemoryDTO> jpaResults = longTermMemoryService.findByIds(new ArrayList<>(keywordHitIds));

        // Apply keyword scores
        Map<Long, Double> keywordScoreMap = keywordMatches.stream()
                .collect(java.util.stream.Collectors.toMap(
                        KeywordRetriever.KeywordMatch::memoryId,
                        KeywordRetriever.KeywordMatch::score,
                        (a, b) -> a));
        for (MemoryDTO dto : jpaResults) {
            Double kwScore = keywordScoreMap.get(dto.getId());
            if (kwScore != null) {
                dto.setScore(kwScore);
            }
        }

        // Step 3: JPA 内部精排
        List<MemoryDTO> reranked;
        if (!keywordMatches.isEmpty()) {
            reranked = memoryReranker.rerank(jpaResults, keywordMatches, 15);
        } else {
            reranked = memoryReranker.rerankDenseOnly(jpaResults, 15);
        }

        // Step 4: 按类型分层
        Map<String, List<MemoryDTO>> result = new HashMap<>();
        List<MemoryDTO> l1 = new ArrayList<>();  // PROFILE
        List<MemoryDTO> l2 = new ArrayList<>();  // FACT, KNOWLEDGE, etc.
        List<MemoryDTO> l3 = new ArrayList<>();  // PREFERENCE, SKILL, RULE

        for (MemoryDTO dto : reranked) {
            MemoryType type = dto.getMemoryType();
            if (type == null) {
                l2.add(dto);
                continue;
            }
            switch (type) {
                case PROFILE -> l1.add(dto);
                case PREFERENCE, SKILL, RULE -> l3.add(dto);
                default -> l2.add(dto);
            }
        }

        // Step 5: 类型过滤（如果指定了 requiredTypes/excludedTypes）
        if (!requiredTypes.isEmpty() || !excludedTypes.isEmpty()) {
            l1 = filterByType(l1, requiredTypes, excludedTypes);
            l2 = filterByType(l2, requiredTypes, excludedTypes);
            l3 = filterByType(l3, requiredTypes, excludedTypes);
        }

        // Step 6: 各层截断
        result.put("l1", l1.stream().limit(5).toList());
        result.put("l2", l2.stream().limit(5).toList());
        result.put("l3", l3.stream().limit(3).toList());

        return result;
    }

    /**
     * Path B: Cognee 语义召回（片段 + 实体 + 关系）
     */
    private ConversationContext.CogneeContext retrieveCogneeContext(
            ConversationContext ctx, String recallQuery) {

        if (!cogneeProperties.isEnabled()) {
            return new ConversationContext.CogneeContext(List.of(), List.of(), List.of());
        }

        try {
            int topK = cogneeProperties.getSearch().getTopK();
            CogneeClient.RecallWithContextResult result = cogneeClient.recallWithContext(
                    ctx.getUserId(), recallQuery, topK, ctx.getConversationId());

            List<CogneeClient.RecallResult> fragments = result.fragments() != null
                    ? result.fragments() : List.of();
            List<String> entities = result.entities() != null
                    ? result.entities() : List.of();
            List<CogneeClient.CogneeRelationRecord> relations = result.relations() != null
                    ? result.relations() : List.of();

            // Convert to ConversationContext inner types
            List<ConversationContext.CogneeRelation> convRelations = relations.stream()
                    .map(r -> new ConversationContext.CogneeRelation(
                            r.source(), r.relation(), r.target()))
                    .toList();

            return new ConversationContext.CogneeContext(fragments, entities, convRelations);

        } catch (Exception e) {
            log.warn("[LongTermMemory] Cognee recall failed: {}", e.getMessage());
            return new ConversationContext.CogneeContext(List.of(), List.of(), List.of());
        }
    }

    private List<MemoryDTO> filterByType(List<MemoryDTO> memories,
                                          Set<MemoryType> requiredTypes,
                                          Set<MemoryType> excludedTypes) {
        return memories.stream()
                .filter(dto -> {
                    MemoryType type = dto.getMemoryType();
                    if (type == null) return true;
                    if (excludedTypes != null && excludedTypes.contains(type)) return false;
                    if (requiredTypes != null && !requiredTypes.isEmpty()) {
                        return requiredTypes.contains(type);
                    }
                    return true;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public int getOrder() {
        return 310;
    }

    @Override
    public boolean isCritical() {
        return false;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
