package com.example.app.pipeline.stage.assembly;

import com.example.app.dto.MemoryDTO;
import com.example.app.dto.QueryAnalysisResult;
import com.example.app.entity.LongTermMemory.MemoryType;
import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 记忆格式化 Stage（ASSEMBLY 阶段，order=400）
 *
 * <p>按三层优先级格式化记忆，供 SystemPrompt 分层注入：
 * <ul>
 *   <li>L1: 用户档案（PROFILE 类型）— 始终注入，最高优先级</li>
 *   <li>L2: 当前问题相关记忆（精排后的 top 5）— 动态注入</li>
 *   <li>L3: 用户偏好（PREFERENCE 类型）— 可选注入，低优先级</li>
 * </ul>
 *
 * <p>分层注入的好处：
 * <ul>
 *   <li>模型始终能看到用户身份信息（L1），不会张冠李戴</li>
 *   <li>只有与当前问题相关的记忆进入上下文（L2），避免污染</li>
 *   <li>偏好信息仅在需要时注入（L3），节省 token</li>
 * </ul>
 */
@Component
@Slf4j
public class MemoryFormatStage implements ContextPipelineStage {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 类型 → 层级映射 */
    private static final Map<MemoryType, MemoryLayer> TYPE_TO_LAYER = new EnumMap<>(MemoryType.class);

    static {
        TYPE_TO_LAYER.put(MemoryType.PROFILE, MemoryLayer.L1);
        TYPE_TO_LAYER.put(MemoryType.PREFERENCE, MemoryLayer.L3);
        TYPE_TO_LAYER.put(MemoryType.SKILL, MemoryLayer.L2);
        TYPE_TO_LAYER.put(MemoryType.KNOWLEDGE, MemoryLayer.L2);
        TYPE_TO_LAYER.put(MemoryType.PROJECT, MemoryLayer.L2);
        TYPE_TO_LAYER.put(MemoryType.TASK, MemoryLayer.L2);
        TYPE_TO_LAYER.put(MemoryType.EVENT, MemoryLayer.L2);
        TYPE_TO_LAYER.put(MemoryType.RELATION, MemoryLayer.L2);
    }

    /** 每层最多注入的记忆数 */
    private static final int L1_MAX = 3;
    private static final int L2_MAX = 5;
    private static final int L3_MAX = 3;

    @Override
    public Phase getPhase() { return Phase.ASSEMBLY; }

    public String getName() {
        return "memoryFormatStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        List<MemoryDTO> memories = ctx.getLongTermMemory();
        QueryAnalysisResult analysis = ctx.getQueryAnalysisResult();

        if (memories == null || memories.isEmpty()) {
            ctx.getAgentState().put(ConversationContext.KEY_FORMATTED_MEMORY, "");
            ctx.getAgentState().put(ConversationContext.KEY_FORMATTED_MEMORY_L1, "");
            ctx.getAgentState().put(ConversationContext.KEY_FORMATTED_MEMORY_L2, "");
            ctx.getAgentState().put(ConversationContext.KEY_FORMATTED_MEMORY_L3, "");
            return;
        }

        // 按层级分组
        Map<MemoryLayer, List<MemoryDTO>> layered = splitByLayer(memories, analysis);

        // 格式化各层
        String l1 = formatLayer(layered.getOrDefault(MemoryLayer.L1, Collections.emptyList()),
                "用户档案", L1_MAX, true);
        String l2 = formatLayer(layered.getOrDefault(MemoryLayer.L2, Collections.emptyList()),
                "相关记忆", L2_MAX, false);
        String l3 = formatLayer(layered.getOrDefault(MemoryLayer.L3, Collections.emptyList()),
                "用户偏好", L3_MAX, true);

        // 兼容旧格式：合并为单一文本
        String merged = mergeLayers(l1, l2, l3);

        ctx.getAgentState().put(ConversationContext.KEY_FORMATTED_MEMORY, merged);
        ctx.getAgentState().put(ConversationContext.KEY_FORMATTED_MEMORY_L1, l1);
        ctx.getAgentState().put(ConversationContext.KEY_FORMATTED_MEMORY_L2, l2);
        ctx.getAgentState().put(ConversationContext.KEY_FORMATTED_MEMORY_L3, l3);

        log.debug("[MemoryFormat] L1={} items, L2={} items, L3={} items",
                countMemories(l1), countMemories(l2), countMemories(l3));
    }

    /**
     * 按层级分组记忆
     */
    private Map<MemoryLayer, List<MemoryDTO>> splitByLayer(List<MemoryDTO> memories,
                                                            QueryAnalysisResult analysis) {
        Map<MemoryLayer, List<MemoryDTO>> result = new EnumMap<>(MemoryLayer.class);

        // 初始化
        for (MemoryLayer layer : MemoryLayer.values()) {
            result.put(layer, new ArrayList<>());
        }

        // 按类型分层
        for (MemoryDTO dto : memories) {
            MemoryType type = dto.getMemoryType();
            MemoryLayer layer = TYPE_TO_LAYER.getOrDefault(type, MemoryLayer.L2);
            result.get(layer).add(dto);
        }

        // L2 层：按精排分数排序（已在 LongTermMemoryStage 精排过）
        result.get(MemoryLayer.L2).sort((a, b) -> {
            double sa = a.getScore() != null ? a.getScore() : 0.0;
            double sb = b.getScore() != null ? b.getScore() : 0.0;
            return Double.compare(sb, sa);
        });

        // L1/L3 层：按重要性排序
        for (MemoryLayer layer : List.of(MemoryLayer.L1, MemoryLayer.L3)) {
            result.get(layer).sort((a, b) -> {
                int ia = a.getImportance() != null ? a.getImportance() : 0;
                int ib = b.getImportance() != null ? b.getImportance() : 0;
                return Integer.compare(ib, ia);
            });
        }

        // 意图门控：如果意图是 CHAT_SMALLTALK，只保留 L1（昵称）
        if (analysis != null && analysis.getIntentType() == QueryAnalysisResult.IntentType.CHAT_SMALLTALK) {
            result.put(MemoryLayer.L2, Collections.emptyList());
            result.put(MemoryLayer.L3, Collections.emptyList());
        }

        return result;
    }

    /**
     * 格式化单层记忆
     */
    private String formatLayer(List<MemoryDTO> memories, String layerName, int maxCount,
                                boolean includeMetadata) {
        if (memories.isEmpty()) {
            return "";
        }

        List<MemoryDTO> limited = memories.stream().limit(maxCount).toList();

        StringBuilder sb = new StringBuilder();
        sb.append(layerName).append("：\n");

        for (MemoryDTO memory : limited) {
            sb.append("- ");
            LocalDateTime time = memory.getUpdatedAt() != null ? memory.getUpdatedAt() : memory.getCreatedAt();
            if (time != null && includeMetadata) {
                sb.append("[").append(time.format(DATE_FMT)).append("] ");
            }
            sb.append(memory.getContent());

            if (includeMetadata) {
                List<String> tags = new ArrayList<>();
                if (memory.getConfidence() != null) {
                    tags.add("置信度 " + Math.round(memory.getConfidence() * 100) + "%");
                }
                if (memory.getSource() != null && !memory.getSource().isBlank()) {
                    tags.add("来源 " + memory.getSource());
                }
                if (!tags.isEmpty()) {
                    sb.append("（").append(String.join("，", tags)).append("）");
                }
            }
            sb.append("\n");
        }

        return sb.toString().trim();
    }

    /**
     * 合并三层为单一文本（兼容旧格式）
     */
    private String mergeLayers(String l1, String l2, String l3) {
        StringBuilder sb = new StringBuilder();
        sb.append("长期记忆（可能过时，仅作参考）：\n");

        if (!l1.isBlank()) {
            sb.append(l1).append("\n");
        }
        if (!l2.isBlank()) {
            sb.append(l2).append("\n");
        }
        if (!l3.isBlank()) {
            sb.append(l3).append("\n");
        }

        String result = sb.toString().trim();
        // 如果只有标题没有内容，返回空
        if (result.equals("长期记忆（可能过时，仅作参考）：")) {
            return "";
        }
        return result;
    }

    private int countMemories(String formattedLayer) {
        if (formattedLayer == null || formattedLayer.isBlank()) {
            return 0;
        }
        return (int) formattedLayer.lines().filter(l -> l.startsWith("- ")).count();
    }

    @Override
    public int getOrder() {
        return 400;
    }

    @Override
    public boolean isCritical() {
        return false;
    }

    /**
     * 记忆层级枚举
     */
    private enum MemoryLayer {
        /** L1: 用户档案 — 始终注入 */
        L1,
        /** L2: 当前问题相关 — 动态注入 */
        L2,
        /** L3: 用户偏好 — 可选注入 */
        L3
    }
}