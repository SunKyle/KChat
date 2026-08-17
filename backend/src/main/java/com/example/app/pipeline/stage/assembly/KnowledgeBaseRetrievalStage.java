package com.example.app.pipeline.stage.assembly;

import com.example.app.config.CogneeProperties;
import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.service.CogneeClient;
import com.example.app.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库引用召回阶段（ASSEMBLY，order=408）
 *
 * <p>当用户显式引用了知识库（ChatRequest.knowledgeBaseIds 非空）时：
 * 从指定的知识库数据集召回相关片段，格式化为 LLM 可读文本，
 * 写入 agentState，供 SystemPromptAssemblyStage(410) 注入系统提示。
 *
 * <p>设计约束：
 * <ul>
 *   <li>只依赖请求参数（knowledgeBaseIds）判断，是静态可判定的 → 符合 isApplicable 约定</li>
 *   <li>非显式引用时不执行（knowledgeBaseIds 空），避免不必要的检索开销</li>
 *   <li>召回失败只告警不阻断（isCritical=false），与记忆召回行为一致</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KnowledgeBaseRetrievalStage implements ContextPipelineStage {

    /** Key for formatted knowledge base reference text, read by SystemPromptAssemblyStage(410) */
    public static final String KEY_FORMATTED_KB_REFERENCES = "formattedKbReferences";

    private final KnowledgeBaseService knowledgeBaseService;
    private final CogneeClient cogneeClient;
    private final CogneeProperties cogneeProperties;

    @Override
    public Phase getPhase() {
        return Phase.ASSEMBLY;
    }

    @Override
    public String getName() {
        return "knowledgeBaseRetrievalStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        List<String> kbIds = ctx.getKnowledgeBaseIds();
        if (kbIds == null || kbIds.isEmpty()) {
            return;
        }

        if (!cogneeProperties.isEnabled()) {
            log.warn("[KbRetrieval] Cognee disabled, skip KB reference recall");
            return;
        }

        try {
            // kbId → Cognee dataset 名
            List<String> datasets = knowledgeBaseService.getDatasetNames(kbIds);
            if (datasets.isEmpty()) {
                log.info("[KbRetrieval] No datasets resolved for kbIds={}", kbIds);
                return;
            }

            // 记录引用的知识库名称，供持久化为消息的"引用来源"标签
            ctx.setKbReferenceNames(knowledgeBaseService.getKnowledgeBaseNames(kbIds));

            int topK = cogneeProperties.getSearch().getTopK();
            List<CogneeClient.RecallResult> fragments = cogneeClient.recallFromDatasets(
                    ctx.getUserMessage(), topK, datasets);

            String formatted = formatFragments(kbIds, datasets, fragments);
            ctx.getAgentState().put(KEY_FORMATTED_KB_REFERENCES, formatted);
            log.info("[KbRetrieval] kbIds={} datasets={} recalled {} fragments",
                    kbIds, datasets, fragments.size());

        } catch (Exception e) {
            log.warn("[KbRetrieval] recall failed: {}", e.getMessage(), e);
            ctx.getAgentState().put(KEY_FORMATTED_KB_REFERENCES, "");
        }
    }

    private String formatFragments(List<String> kbIds, List<String> datasets,
            List<CogneeClient.RecallResult> fragments) {
        StringBuilder sb = new StringBuilder();
        sb.append("【用户显式引用的知识库内容】\n");
        sb.append("引用知识库: ").append(String.join("、", datasets)).append("\n");
        sb.append("以下是来自这些知识库的相关内容片段，请优先基于此回答，并在回复中明确标注出处：\n");
        sb.append("\n");
        if (fragments == null || fragments.isEmpty()) {
            sb.append("<所选知识库中未检索到与当前问题相关的内容>\n");
        } else {
            for (int i = 0; i < fragments.size(); i++) {
                CogneeClient.RecallResult r = fragments.get(i);
                sb.append("[").append(i + 1).append("] (来源: ")
                        .append(r.source() != null ? r.source() : "知识库").append(")\n");
                sb.append(r.text().trim()).append("\n\n");
            }
        }
        return sb.toString();
    }

    @Override
    public boolean isApplicable(ConversationContext ctx) {
        // 静态可判定：请求参数是否引用了知识库
        return ctx.getKnowledgeBaseIds() != null && !ctx.getKnowledgeBaseIds().isEmpty();
    }

    @Override
    public int getOrder() {
        return 408;
    }

    @Override
    public boolean isCritical() {
        return false;
    }
}