package com.example.app.pipeline.stage.preprocess;

import com.example.app.config.CogneeProperties;
import com.example.app.dto.QueryAnalysisResult;
import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.service.CogneeClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 长期记忆检索阶段（阶段 310）
 *
 * <p>架构：仅保留 Cognee 语义召回，所有结构化记忆存储在 Cognee 中。
 * JPA long_term_memory 已完全迁移至 Cognee，不再使用。
 *
 * <p>设计原则：默认始终召回，只跳过明确的纯数学计算场景。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LongTermMemoryStage implements ContextPipelineStage {

    private final CogneeClient cogneeClient;
    private final CogneeProperties cogneeProperties;

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
                ctx.setCogneeContext(new ConversationContext.CogneeContext(
                        List.of(), List.of(), List.of()));
                return;
            }

            // ── Query Understanding ────────────────────────────
            QueryAnalysisResult analysis = ctx.getQueryAnalysisResult();
            String recallQuery = analysis != null
                    ? analysis.getEffectiveQuery(userMessage)
                    : userMessage;

            // ── Cognee 语义召回 ────────────────────────────────
            ConversationContext.CogneeContext cogneeCtx = retrieveCogneeContext(
                    ctx, recallQuery);
            ctx.setCogneeContext(cogneeCtx);

            // ── Logging ────────────────────────────────────────
            int cogneeFragments = cogneeCtx.fragments() != null ? cogneeCtx.fragments().size() : 0;
            int cogneeEntities = cogneeCtx.entities() != null ? cogneeCtx.entities().size() : 0;
            int cogneeRelations = cogneeCtx.relations() != null ? cogneeCtx.relations().size() : 0;

            log.info("[LongTermMemory] query='{}' cognee(f={},e={},r={})",
                    truncate(recallQuery, 50),
                    cogneeFragments, cogneeEntities, cogneeRelations);

        } catch (Exception e) {
            log.warn("Long-term memory recall failed: {}", e.getMessage(), e);
            ctx.setCogneeContext(new ConversationContext.CogneeContext(
                    List.of(), List.of(), List.of()));
        }
    }

    /**
     * Cognee 语义召回（片段 + 实体 + 关系）
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
