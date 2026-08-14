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
 * Pipeline stage that indexes newly extracted structured memories into the
 * Cognee knowledge graph.
 *
 * <p>
 * This stage runs at order 725 in POSTPROCESS, immediately after
 * MemoryExtractionStage (720).
 * It takes the clean, structured MemoryDTOs produced by MemoryExtractionStage
 * and writes them
 * directly to Cognee's <b>permanent graph</b> (no session_id).
 *
 * <h3>Why not write raw conversation text?</h3>
 * <ul>
 * <li>Raw conversation (User + Assistant messages) is noisy and redundant — the
 * LLM already
 * sees it in the conversation history. Writing it to Cognee pollutes the graph
 * with
 * DocumentChunk / TextSummary nodes that contain verbatim dialogue.</li>
 * <li>Structured memories have already been deduplicated, threshold-filtered,
 * and typed
 * (PROFILE, PREFERENCE, KNOWLEDGE, etc.) — far higher quality than conversation
 * transcripts.</li>
 * <li>Cognee's cognify extracts cleaner entities from structured text than from
 * raw dialogue.</li>
 * </ul>
 *
 * <h3>Strategy</h3>
 * 
 * <pre>
 * MemoryExtractionStage (720) → newlyExtractedMemories (List<MemoryDTO>)
 *   ↓
 * CogneeMemoryIndexStage (725)
 *   ├── has structured memories? → write to permanent graph (no session_id)
 *   └── no new memories?        → skip (don't write raw conversation)
 * </pre>
 *
 * <p>
 * If cognee is disabled or unreachable, this stage degrades gracefully
 * (non-critical stage) without affecting the chat pipeline.
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
            Map.entry("EVENT", "事件"));

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
        // NOTE: isApplicable is called BEFORE any stage executes (see ContextPipelineExecutor.resolveStages),
        // so we cannot check newlyExtractedMemories here — it's set by MemoryExtractionStage (720)
        // which runs just before this stage (725). The check is done inside execute() instead.
        return cogneeProperties.isEnabled() && cogneeProperties.getIndex().isEnabled();
    }

    @Override
    public void execute(ConversationContext ctx) {
        List<MemoryDTO> newMemories = ctx.getNewlyExtractedMemories();
        if (newMemories == null || newMemories.isEmpty()) {
            log.debug("[CogneeMemoryIndex] No new structured memories to index, skipping");
            return;
        }

        final String content = formatStructuredMemories(newMemories);

        CompletableFuture.runAsync(() -> {
            try {
                // Write to permanent graph (no session_id):
                // - Runs full cognify pipeline (chunk → entity extraction → graph build)
                // - selfImprovement=true triggers background improve() for relation bridging
                boolean indexed = cogneeClient.remember(content);
                if (indexed) {
                    log.info("[CogneeMemoryIndex] Indexed {} structured memories to permanent graph ({} chars)",
                            newMemories.size(), content.length());
                } else {
                    log.warn("[CogneeMemoryIndex] Failed to index structured memories");
                }
            } catch (Exception e) {
                log.warn("[CogneeMemoryIndex] Async indexing failed: {}", e.getMessage());
            }
        });
    }

    /**
     * Format a list of MemoryDTOs into a typed, sectioned document that helps Cognee's entity
     * extractor produce a cleaner graph. Type headers are kept so cognify see
     *  structure
     * instead of unlabelled bullet points.
     * 
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
