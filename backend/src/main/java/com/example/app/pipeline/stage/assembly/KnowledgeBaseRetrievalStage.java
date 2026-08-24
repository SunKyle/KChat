package com.example.app.pipeline.stage.assembly;

import com.example.app.config.CogneeProperties;
import com.example.app.dto.KbReference;
import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.service.CogneeClient;
import com.example.app.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 知识库引用召回阶段（ASSEMBLY，order=408）
 *
 * <p>当用户显式引用了知识库（ChatRequest.knowledgeBaseIds 非空）时：
 * 从指定的知识库数据集召回相关片段，格式化为 LLM 可读文本，
 * 写入 agentState，供 SystemPromptAssemblyStage(410) 注入系统提示。
 *
 * <p>同时构建文档层级的"引用来源"记录（{@link KbReference}：知识库名 + 文档名），
 * 存入 ConversationContext.kbReferences，经消息持久化 + SSE done 事件
 * 透传给前端渲染引用标签。文档名来自 Cognee recall 的溯源元数据
 * （document_name），缺失时降级为仅知识库层级。
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

            int topK = cogneeProperties.getSearch().getTopK();
            int graphTopK = Math.min(3, topK); // 图谱关系更浓缩，取少量控制成本

            // 双轨检索（并行）：正文片段（CHUNKS）+ 图谱关系（GRAPH_COMPLETION）。
            // CHUNKS 提供文档正文（时间线/数字/名单等事实），GRAPH_COMPLETION 提供
            // 实体与关系洞察（跨文档关联、主题结构），两者互补，LLM 既能拿事实又能做结构推理。
            List<CogneeClient.RecallResult> fragments;
            List<CogneeClient.RecallResult> graphResults;
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            try {
                CompletableFuture<List<CogneeClient.RecallResult>> textFuture = CompletableFuture.supplyAsync(
                        () -> cogneeClient.recallFromDatasets(ctx.getUserMessage(), topK, datasets, "CHUNKS"),
                        executor);
                CompletableFuture<List<CogneeClient.RecallResult>> graphFuture = CompletableFuture.supplyAsync(
                        () -> cogneeClient.recallFromDatasets(ctx.getUserMessage(), graphTopK, datasets,
                                "GRAPH_COMPLETION"),
                        executor);
                fragments = textFuture.get(20, TimeUnit.SECONDS);
                graphResults = graphFuture.get(25, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("[KbRetrieval] dual recall failed, fallback to text-only: {}", e.getMessage(), e);
                fragments = cogneeClient.recallFromDatasets(ctx.getUserMessage(), topK, datasets, "CHUNKS");
                graphResults = List.of();
            } finally {
                executor.shutdown();
            }

            // 构建文档层级引用来源（知识库名 + 文档名），供持久化展示（基于正文片段）
            List<KbReference> kbRefs = buildKbReferences(kbIds, fragments);
            ctx.setKbReferences(kbRefs);
            log.info("[KbRetrieval] kbIds={} datasets={} recalled {} text + {} graph fragments, refs={}",
                    kbIds, datasets, fragments.size(), graphResults.size(), kbRefs.size());

            String formatted = formatFragments(kbIds, datasets, fragments);
            String graphFormatted = formatGraphFragments(graphResults);
            if (!graphFormatted.isBlank()) {
                formatted = formatted + "\n" + graphFormatted;
            }
            ctx.getAgentState().put(KEY_FORMATTED_KB_REFERENCES, formatted);

        } catch (Exception e) {
            log.warn("[KbRetrieval] recall failed: {}", e.getMessage(), e);
            ctx.getAgentState().put(KEY_FORMATTED_KB_REFERENCES, "");
        }
    }

    /**
     * 构建引用来源记录。
     *
     * <p>优先使用召回片段携带的溯源元数据：document_name（文档名）+ dataset_name
     * （反查知识库名）。同一 知识库+文档 去重。当没有任何文档级信息时，
     * 降级为仅展示被引用的知识库本身。
     */
    private List<KbReference> buildKbReferences(List<String> kbIds,
            List<CogneeClient.RecallResult> fragments) {
        // datasetName → 知识库名 反查
        Set<String> datasets = fragments.stream()
                .map(CogneeClient.RecallResult::datasetName)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
        Map<String, String> datasetToKbName =
                knowledgeBaseService.getKnowledgeBaseNameByDatasets(datasets);

        // 被引用知识库名（降级兜底用）
        List<String> kbNames = knowledgeBaseService.getKnowledgeBaseNames(kbIds);
        String fallbackKbName = kbNames.isEmpty() ? null : kbNames.get(0);

        List<KbReference> refs = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // 有文档信息的片段优先
        for (CogneeClient.RecallResult f : fragments) {
            String docName = f.documentName();
            if (docName == null || docName.isBlank()) {
                continue;
            }
            String kbName = f.datasetName() != null
                    ? datasetToKbName.get(f.datasetName())
                    : null;
            if (kbName == null || kbName.isBlank()) {
                kbName = fallbackKbName;
            }
            if (kbName == null || kbName.isBlank()) {
                continue;
            }
            String key = kbName + "|" + docName;
            if (seen.add(key)) {
                refs.add(KbReference.of(kbName, docName));
            }
        }

        // 兜底：被引用的知识库本身（无文档级信息时展示知识库层级）
        if (refs.isEmpty()) {
            for (String name : kbNames) {
                if (seen.add("kb|" + name)) {
                    refs.add(KbReference.of(name));
                }
            }
        }
        return refs;
    }

    private String formatFragments(List<String> kbIds, List<String> datasets,
            List<CogneeClient.RecallResult> fragments) {
        // datasetName → 知识库名（用于 LLM 片段标注出处）
        Set<String> datasetSet = fragments.stream()
                .map(CogneeClient.RecallResult::datasetName)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
        Map<String, String> datasetToKbName =
                knowledgeBaseService.getKnowledgeBaseNameByDatasets(datasetSet);

        StringBuilder sb = new StringBuilder();
        sb.append("【用户显式引用的知识库内容】\n");
        sb.append("引用知识库: ").append(String.join("、", datasets)).append("\n");
        sb.append("以下是来自这些知识库的相关内容片段，请优先基于此回答，并在回复中明确标注出处：\n");
        sb.append("\n");
        if (fragments == null || fragments.isEmpty()) {
            sb.append("<所选知识库中未检索到与当前问题相关的内容>\n");
        } else {
            // 片段按知识库分组，便于 LLM 区分来源
            Map<String, List<CogneeClient.RecallResult>> byDataset = new LinkedHashMap<>();
            for (CogneeClient.RecallResult r : fragments) {
                byDataset.computeIfAbsent(
                        r.datasetName() != null ? r.datasetName() : "", k -> new ArrayList<>())
                        .add(r);
            }
            int idx = 0;
            for (Map.Entry<String, List<CogneeClient.RecallResult>> e : byDataset.entrySet()) {
                String ds = e.getKey();
                String kbName = datasetToKbName.getOrDefault(ds, ds);
                for (CogneeClient.RecallResult r : e.getValue()) {
                    idx++;
                    sb.append("[").append(idx).append("] (来源: ").append(kbName);
                    if (r.documentName() != null && !r.documentName().isBlank()) {
                        sb.append(" / ").append(r.documentName());
                    }
                    sb.append(")\n");
                    sb.append(r.text().trim()).append("\n\n");
                }
            }
        }
        return sb.toString();
    }

    /**
     * 格式化图谱关系检索结果（GRAPH_COMPLETION）。
     *
     * <p>返回的是图补全上下文（实体、关系、跨文档关联等结构性信息），
     * 与正文片段（CHUNKS）互补。全部为空时返回空串，由调用方决定是否拼接。
     */
    private String formatGraphFragments(List<CogneeClient.RecallResult> graphResults) {
        if (graphResults == null || graphResults.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【知识库图谱关系】\n");
        sb.append("以下是从知识图谱提取的实体与关联关系，用于理解文档间的结构联系"
                + "（与正文片段互补，回答时以正文事实为准）：\n\n");
        int idx = 0;
        for (CogneeClient.RecallResult r : graphResults) {
            if (r.text() == null || r.text().isBlank()) {
                continue;
            }
            idx++;
            sb.append("[").append(idx).append("]");
            if (r.documentName() != null && !r.documentName().isBlank()) {
                sb.append(" (来源: ").append(r.documentName()).append(")");
            }
            sb.append("\n").append(r.text().trim()).append("\n\n");
        }
        if (idx == 0) {
            return "";
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
