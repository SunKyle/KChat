package com.example.app.pipeline.stage.assembly;

import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.CogneeMemoryContext;
import com.example.app.pipeline.context.ConversationContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 记忆格式化 Stage（ASSEMBLY 阶段，order=400）
 *
 * <p>仅格式化 Cognee 语义召回结果，供 SystemPrompt 注入。
 * JPA long_term_memory 已完全迁移至 Cognee，不再使用。
 *
 * <p>解耦：本 Stage 只读取中性载体 {@link CogneeMemoryContext}，
 * 不依赖 {@code CogneeClient} 的具体类型。
 */
@Component
@Slf4j
public class MemoryFormatStage implements ContextPipelineStage {

    @Override
    public Phase getPhase() { return Phase.ASSEMBLY; }

    public String getName() {
        return "memoryFormatStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        CogneeMemoryContext cogneeCtx = ctx.getCogneeContextTyped();
        String cogneeGraph = formatCogneeGraph(cogneeCtx);

        ctx.getAgentState().put(ConversationContext.KEY_FORMATTED_MEMORY_COGNEE, cogneeGraph);

        log.debug("[MemoryFormat] cogneeGraph={} chars", cogneeGraph.length());
    }

    // ── 相关知识图谱 (Cognee) ────────────────────────────────

    private String formatCogneeGraph(CogneeMemoryContext ctx) {
        if (ctx == null || ctx.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("## 相关知识\n");

        // Part A: 语义相关片段
        List<CogneeMemoryContext.Fragment> fragments = ctx.fragments();
        if (fragments != null && !fragments.isEmpty()) {
            sb.append("\n语义相关片段:\n");
            for (CogneeMemoryContext.Fragment fragment : fragments) {
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
        List<CogneeMemoryContext.Relation> relations = ctx.relations();
        if (relations != null && !relations.isEmpty()) {
            sb.append("\n关联关系:\n");
            for (CogneeMemoryContext.Relation rel : relations) {
                sb.append("- ").append(rel.source())
                        .append(" → (").append(rel.relation()).append(") → ")
                        .append(rel.target()).append("\n");
            }
        }

        return sb.toString().trim();
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
