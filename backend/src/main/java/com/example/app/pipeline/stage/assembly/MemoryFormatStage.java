package com.example.app.pipeline.stage.assembly;

import com.example.app.dto.MemoryDTO;
import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.service.CogneeClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 记忆格式化 Stage（ASSEMBLY 阶段，order=400）
 *
 * <p>按四块独立格式化记忆，供 SystemPrompt 分块注入：
 * <ul>
 *   <li>块 1: 用户档案 (JPA L1, PROFILE 类型) — 始终注入</li>
 *   <li>块 2: 相关知识图谱 (Cognee, 片段+实体+关系) — 动态注入</li>
 *   <li>块 3: 用户偏好 (JPA L3, PREFERENCE/SKILL/RULE 类型) — 可选注入</li>
 *   <li>块 4: 精确记忆 (JPA L2, FACT/KNOWLEDGE 精确匹配) — 动态注入</li>
 * </ul>
 *
 * <p>双源分离设计：JPA 负责结构化记忆（画像/偏好/精确事实），
 * Cognee 负责语义知识和图谱关系，不再强行合并为统一列表。
 */
@Component
@Slf4j
public class MemoryFormatStage implements ContextPipelineStage {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public Phase getPhase() { return Phase.ASSEMBLY; }

    public String getName() {
        return "memoryFormatStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        // Read from new separated sources
        Map<String, List<MemoryDTO>> jpaMemories = ctx.getJpaMemories() != null
                ? ctx.getJpaMemories() : Map.of();
        ConversationContext.CogneeContext cogneeCtx = ctx.getCogneeContextTyped();

        // Block 1: 用户档案 (JPA L1)
        String l1Profile = formatUserProfile(jpaMemories.getOrDefault("l1", List.of()));

        // Block 2: 相关知识图谱 (Cognee)
        String cogneeGraph = formatCogneeGraph(cogneeCtx);

        // Block 3: 用户偏好 (JPA L3)
        String l3Preference = formatUserPreference(jpaMemories.getOrDefault("l3", List.of()));

        // Block 4: 精确记忆 (JPA L2)
        String preciseMemory = formatPreciseMemory(jpaMemories.getOrDefault("l2", List.of()));

        // Also build legacy combined format for backward compat
        String legacyCombined = buildLegacyCombined(l1Profile, cogneeGraph, l3Preference, preciseMemory);

        ctx.getAgentState().put(ConversationContext.KEY_FORMATTED_MEMORY, legacyCombined);
        ctx.getAgentState().put(ConversationContext.KEY_FORMATTED_MEMORY_L1, l1Profile);
        ctx.getAgentState().put(ConversationContext.KEY_FORMATTED_MEMORY_L2, preciseMemory);
        ctx.getAgentState().put(ConversationContext.KEY_FORMATTED_MEMORY_L3, l3Preference);
        ctx.getAgentState().put(ConversationContext.KEY_FORMATTED_MEMORY_COGNEE, cogneeGraph);
        ctx.getAgentState().put(ConversationContext.KEY_FORMATTED_MEMORY_PRECISE, preciseMemory);

        log.debug("[MemoryFormat] l1Profile={} chars, cogneeGraph={} chars, l3Preference={} chars, preciseMemory={} chars",
                l1Profile.length(), cogneeGraph.length(), l3Preference.length(), preciseMemory.length());
    }

    // ── Block 1: 用户档案 (JPA L1, PROFILE) ────────────────────

    private String formatUserProfile(List<MemoryDTO> memories) {
        if (memories.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("## 用户档案\n");

        for (MemoryDTO dto : memories) {
            sb.append("- ").append(dto.getContent());
            if (dto.getImportance() != null && dto.getImportance() >= 8) {
                sb.append(" (重要)");
            }
            sb.append("\n");
        }

        return sb.toString().trim();
    }

    // ── Block 2: 相关知识图谱 (Cognee) ──────────────────────────

    private String formatCogneeGraph(ConversationContext.CogneeContext ctx) {
        if (ctx == null || ctx.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("## 相关知识\n");

        // Part A: 语义相关片段
        List<CogneeClient.RecallResult> fragments = ctx.fragments();
        if (fragments != null && !fragments.isEmpty()) {
            sb.append("\n语义相关片段:\n");
            for (CogneeClient.RecallResult fragment : fragments) {
                double score = fragment.score();
                sb.append("- [相似度:").append(String.format("%.2f", score)).append("] ")
                        .append(fragment.text()).append("\n");
            }
        }

        // Part B: 关联实体
        List<String> entities = ctx.entities();
        if (entities != null && !entities.isEmpty()) {
            sb.append("\n关联实体: ").append(String.join(", ", entities)).append("\n");
        }

        // Part C: 关联关系
        List<ConversationContext.CogneeRelation> relations = ctx.relations();
        if (relations != null && !relations.isEmpty()) {
            sb.append("\n关联关系:\n");
            for (ConversationContext.CogneeRelation rel : relations) {
                sb.append("- ").append(rel.source())
                        .append(" → (").append(rel.relation()).append(") → ")
                        .append(rel.target()).append("\n");
            }
        }

        return sb.toString().trim();
    }

    // ── Block 3: 用户偏好 (JPA L3, PREFERENCE/SKILL/RULE) ──────

    private String formatUserPreference(List<MemoryDTO> memories) {
        if (memories.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("## 用户偏好\n");

        for (MemoryDTO dto : memories) {
            sb.append("- ").append(dto.getContent());
            if (dto.getConfidence() != null) {
                sb.append(" (置信度 ").append(Math.round(dto.getConfidence() * 100)).append("%)");
            }
            sb.append("\n");
        }

        return sb.toString().trim();
    }

    // ── Block 4: 精确记忆 (JPA L2, FACT/KNOWLEDGE 等) ──────────

    private String formatPreciseMemory(List<MemoryDTO> memories) {
        if (memories.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("## 精确记忆\n");

        for (MemoryDTO dto : memories) {
            String typeLabel = getTypeLabel(dto.getMemoryType());
            sb.append("- ");
            if (typeLabel != null) {
                sb.append(typeLabel).append(": ");
            }
            sb.append(dto.getContent());
            sb.append("\n");
        }

        return sb.toString().trim();
    }

    private String getTypeLabel(com.example.app.entity.LongTermMemory.MemoryType type) {
        if (type == null) return null;
        return switch (type) {
            case PROFILE -> "档案";
            case PREFERENCE -> "偏好";
            case RULE -> "规则";
            case FACT -> "事实";
            case KNOWLEDGE -> "知识";
            case EXPERIENCE -> "经验";
            case PROJECT -> "项目";
            case TASK -> "任务";
            case RELATION -> "关系";
            case SKILL -> "技能";
            case EVENT -> "事件";
        };
    }

    // ── Legacy combined format (backward compat) ──────────────

    private String buildLegacyCombined(String l1, String cognee, String l3, String precise) {
        StringBuilder sb = new StringBuilder();
        sb.append("长期记忆（可能过时，仅作参考）：\n");

        if (!l1.isBlank()) sb.append(l1).append("\n");
        if (!cognee.isBlank()) sb.append(cognee).append("\n");
        if (!l3.isBlank()) sb.append(l3).append("\n");
        if (!precise.isBlank()) sb.append(precise).append("\n");

        String result = sb.toString().trim();
        if (result.equals("长期记忆（可能过时，仅作参考）：")) {
            return "";
        }
        return result;
    }

    @Override
    public int getOrder() {
        return 400;
    }

    @Override
    public boolean isCritical() {
        return false;
    }
}
