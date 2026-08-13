package com.example.app.pipeline.stage.postprocess;

import com.example.app.config.CogneeProperties;
import com.example.app.dto.MemoryDTO;
import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.service.CogneeClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Pipeline stage that indexes newly extracted structured memories into the Cognee knowledge graph.
 *
 * <p>This stage runs at order 725 in POSTPROCESS, immediately after MemoryExtractionStage (720).
 * It prefers the clean, structured MemoryDTOs produced by MemoryExtractionStage over raw
 * conversation text because:
 * <ul>
 *   <li>Structured memories have already been deduplicated, threshold-filtered, and typed (PROFILE,
 *       PREFERENCE, PROJECT, etc.) — far higher quality than noisy conversation transcripts.</li>
 *   <li>Cognee's cognify doesn't need to re-extract entities from chatter, saving an extra LLM call
 *       and producing a cleaner graph with fewer spurious nodes.</li>
 *   <li>As a fallback, if this run extracted zero memories (threshold not hit, LLM found nothing,
 *       etc.), the original conversation pair is still indexed so no knowledge is lost.</li>
 * </ul>
 *
 * <p>If cognee is disabled or unreachable, this stage degrades gracefully
 * (non-critical stage) without affecting the chat pipeline.
 *
 * <h3>Data Flow</h3>
 * <pre>
 * ctx.newlyExtractedMemories (structured) → format as typed bulleted list → cognee.add()
 * ── OR (fallback) ──
 * User Message + AI Response (raw)        → cognee.add()
 * </pre>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CogneeMemoryIndexStage implements ContextPipelineStage {

    private static final Map<String, String> TYPE_LABELS = Map.ofEntries(
            Map.entry("KNOWLEDGE", "知识"),
            Map.entry("RULE", "规则"),
            Map.entry("FACT", "事实"),
            Map.entry("PREFERENCE", "偏好"),
            Map.entry("EXPERIENCE", "经验"),
            Map.entry("PROFILE", "用户画像"),
            Map.entry("SKILL", "技能"),
            Map.entry("PROJECT", "项目"),
            Map.entry("TASK", "任务"),
            Map.entry("RELATION", "关系"),
            Map.entry("EVENT", "事件")
    );

    private final CogneeClient cogneeClient;
    private final CogneeProperties cogneeProperties;

    @Override
    public Phase getPhase() {
        return Phase.POSTPROCESS;
    }

    @Override
    public String getName() {
        return "cogneeMemoryIndexStage";
    }

    @Override
    public int getOrder() {
        return 725;
    }

    @Override
    public boolean isCritical() {
        return false;
    }

    @Override
    public boolean isApplicable(ConversationContext ctx) {
        if (!cogneeProperties.isEnabled() || !cogneeProperties.getIndex().isEnabled()) {
            return false;
        }
        // Applicable if we have structured memories to index, OR at minimum a conversation pair
        boolean hasNewMemories = ctx.getNewlyExtractedMemories() != null
                && !ctx.getNewlyExtractedMemories().isEmpty();
        boolean hasConversation = ctx.getUserMessage() != null && !ctx.getUserMessage().isBlank()
                && ctx.getLlmResponse() != null && !ctx.getLlmResponse().isBlank();
        return hasNewMemories || hasConversation;
    }

    @Override
    public void execute(ConversationContext ctx) {
        List<MemoryDTO> newMemories = ctx.getNewlyExtractedMemories();

        final String content;
        final String source;
        if (newMemories != null && !newMemories.isEmpty()) {
            content = formatStructuredMemories(newMemories);
            source = "structured-memory";
            log.info("[CogneeMemoryIndex] Using {} structured memories from extraction pipeline",
                    newMemories.size());
        } else {
            content = String.format(
                    "User: %s\n\nAssistant: %s",
                    ctx.getUserMessage(),
                    ctx.getLlmResponse() != null ? ctx.getLlmResponse() : ""
            );
            source = "conversation";
            log.debug("[CogneeMemoryIndex] No new structured memories this run; " +
                    "falling back to raw conversation text ({} chars)", content.length());
        }

        final String finalContent = content;
        final String conversationId = ctx.getConversationId();
        CompletableFuture.runAsync(() -> {
            try {
                // v1.0 session-level remember:
                // - session_id = conversationId → writes to session cache (fast, no entity extraction)
                // - selfImprovement=true → background improve() bridges valuable relations
                //   to the permanent graph automatically (LLM decides what's worth keeping)
                boolean indexed = cogneeClient.remember(finalContent, conversationId, true);
                if (indexed) {
                    log.info("[CogneeMemoryIndex] Indexed conversation {} via remember({}) ({} chars, session={})",
                            conversationId, source, finalContent.length(), conversationId != null);
                } else {
                    log.warn("[CogneeMemoryIndex] Failed to index conversation {}",
                            conversationId);
                }
            } catch (Exception e) {
                log.warn("[CogneeMemoryIndex] Async indexing failed: {}", e.getMessage());
            }
        });
    }

    /**
     * Format a list of MemoryDTOs into a typed, sectioned document that helps Cognee's entity
     * extractor produce a cleaner graph. We keep the type headers so cognify sees structure
     * instead of unlabelled bullet points.
     */
    private String formatStructuredMemories(List<MemoryDTO> memories) {
        StringBuilder sb = new StringBuilder();
        sb.append("以下是本轮对话新提取到的结构化记忆档案，按类型分组：\n\n");

        memories.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        m -> m.getType() != null ? m.getType() : "KNOWLEDGE"))
                .forEach((type, items) -> {
                    String label = TYPE_LABELS.getOrDefault(type, type);
                    sb.append("## ").append(label).append(" (").append(type).append(")\n");
                    for (MemoryDTO m : items) {
                        sb.append("- ").append(m.getContent()).append("\n");
                    }
                    sb.append("\n");
                });

        return sb.toString();
    }
}
