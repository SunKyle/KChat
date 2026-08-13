package com.example.app.service;

import com.example.app.config.CogneeProperties;
import com.example.app.dto.MemoryDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Keeps Cognee's knowledge graph consistent with the JPA long-term-memory table.
 *
 * <p>KChat stores the "ground truth" of structured memories in {@link LongTermMemoryService}
 * (JPA + local vector store). Cognee is an additional graph index over this same data,
 * plus per-conversation incremental ingestions. When memories are deleted in JPA (single
 * item, or the whole user set) there is no stable ID mapping to the data_ids cognee
 * assigns internally — so we take the pragmatic path of rebuilding the graph from
 * scratch asynchronously. For the scale of a personal knowledge base (tens to low
 * hundreds of memories) a full rebuild is cheap, clean, and guarantees no
 * "ghost memories" survive deletion.
 *
 * <h3>When resync triggers</h3>
 * <ul>
 *   <li>{@link LongTermMemoryService#deleteById(Long)} — resyncs for the user owner</li>
 *   <li>{@link LongTermMemoryService#deleteByUserId(String)} — resyncs for that user</li>
 *   <li>Can also be called manually via future admin endpoints</li>
 * </ul>
 *
 * <p><b>Note on circular dependency:</b> LongTermMemoryService injects Optional&lt;CogneeSyncService&gt;
 * (for triggering resync on delete), and CogneeSyncService injects LongTermMemoryService
 * (for loading JPA memories to rebuild). We break the cycle with @Lazy on the
 * LongTermMemoryService injection here — Spring creates a proxy, deferring real
 * initialization until the first method call.
 */
@Service
@Slf4j
public class CogneeSyncService {

    private static final String DATASET_NAME = "main_dataset";

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
    /** @Lazy breaks the circular dependency with LongTermMemoryService. */
    private final LongTermMemoryService longTermMemoryService;

    public CogneeSyncService(
            CogneeClient cogneeClient,
            CogneeProperties cogneeProperties,
            @Lazy @Autowired LongTermMemoryService longTermMemoryService) {
        this.cogneeClient = cogneeClient;
        this.cogneeProperties = cogneeProperties;
        this.longTermMemoryService = longTermMemoryService;
    }

    /**
     * Rebuild Cognee's graph+vector index for this user from the current JPA truth.
     *
     * <p>Steps:
     * <ol>
     *   <li>Forget the cognee dataset (removes graph + vectors + data records)</li>
     *   <li>Load every structured MemoryDTO for this user from JPA</li>
     *   <li>Format them as a type-grouped document (same format as CogneeMemoryIndexStage
     *       uses for incremental structured memory indexing)</li>
     *   <li>Add + cognify into cognee so it produces a fresh, clean graph</li>
     * </ol>
     *
     * <p>Runs asynchronously because the cognify step runs LLM-based entity extraction
     * and must not block the caller (e.g., a DELETE HTTP request returning to the UI).
     *
     * @param userId the user whose memories should be re-synced into cognee
     */
    @Async
    public void resyncUserMemories(String userId) {
        if (!cogneeProperties.isEnabled()) {
            log.debug("[CogneeSync] Cognee disabled; skipping resync for user={}", userId);
            return;
        }
        try {
            log.info("[CogneeSync] Starting resync for user={}", userId);

            // 1. Wipe the current cognee dataset so stale graph edges and orphaned vector
            //    entries definitely don't survive (avoids ghost memories).
            boolean forgot = cogneeClient.forgetDataset(DATASET_NAME);
            if (!forgot) {
                log.warn("[CogneeSync] forgetDataset returned false during resync for user={}; " +
                        "continuing anyway — add/cognify below will still proceed.", userId);
            }

            // 2. Load the current JPA truth.
            List<MemoryDTO> allMemories = longTermMemoryService.findByUserId(userId);
            if (allMemories == null || allMemories.isEmpty()) {
                log.info("[CogneeSync] No JPA memories found for user={}; cognee graph is now empty.", userId);
                return;
            }
            log.info("[CogneeSync] Re-indexing {} JPA memories into cognee", allMemories.size());

            // 3. Format into a type-grouped document (matches CogneeMemoryIndexStage format so
            //    entity extraction behaves consistently between incremental and full rebuilds).
            String doc = formatStructuredMemories(allMemories);

            // 4. Push into cognee via remember() — runs cognify + improve internally
            //    so the graph is immediately searchable.
            boolean ok = cogneeClient.remember(doc);
            if (ok) {
                log.info("[CogneeSync] Resync complete for user={} ({} memories, {} chars)",
                        userId, allMemories.size(), doc.length());
            } else {
                log.warn("[CogneeSync] Resync remember() returned false for user={} — " +
                        "cognee graph may be empty or partial.", userId);
            }
        } catch (Exception e) {
            log.error("[CogneeSync] Resync failed for user={}: {}", userId, e.getMessage(), e);
        }
    }

    /** Mirrors the format in CogneeMemoryIndexStage for consistent entity extraction. */
    private String formatStructuredMemories(List<MemoryDTO> memories) {
        StringBuilder sb = new StringBuilder();
        sb.append("以下是当前用户的所有结构化记忆档案，按类型分组：\n\n");

        memories.stream()
                .collect(Collectors.groupingBy(
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
