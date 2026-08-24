package com.example.app.service;

import com.example.app.config.CogneeProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java HTTP client for the Cognee AI Memory Platform v1.0 REST API.
 *
 * <p>
 * Cognee is an open-source memory platform that builds self-hosted knowledge
 * graphs
 * from ingested data, providing persistent long-term memory for AI agents via
 * combined vector + graph search.
 *
 * <h3>v1.0 API Methods</h3>
 * <ul>
 * <li><b>remember()</b> — Store data into permanent graph or session cache.
 * Equivalent to legacy add() + cognify() + improve() in a single call.
 * With selfImprovement=true (default), a background improve() bridges
 * session memories into the permanent graph automatically.</li>
 * <li><b>recall()</b> — Auto-routed retrieval with session awareness.
 * Uses cognee's intelligent query router to pick the best retrieval strategy
 * (graph completion, RAG, chunks, summaries, etc.) based on the query.</li>
 * <li><b>forgetDataset / forgetEverything</b> — Unified deletion API.</li>
 * <li><b>improve()</b> — Self-derive cross-document relationships.
 * Usually triggered automatically by remember(selfImprovement=true),
 * but exposed for manual invocation after bulk imports.</li>
 * <li><b>isHealthy()</b> — Health check.</li>
 * </ul>
 *
 * <h3>Integration Points</h3>
 * <ul>
 * <li><b>LongTermMemoryStage</b> — Calls {@link #recall(String, String, int)}
 * as Path 3
 * to retrieve graph-enhanced memories and merges them into the conversation
 * context
 * alongside the existing JPA-based memory results.</li>
 * <li><b>CogneeMemoryIndexStage</b> — Calls {@link #remember(String)} after the
 * LLM
 * responds to index structured memories into cognee's knowledge graph.</li>
 * <li><b>CogneeSyncService</b> — Calls {@link #remember(String)} when
 * rebuilding the
 * graph from JPA truth after memory deletions.</li>
 * </ul>
 *
 * <p>
 * All requests are non-critical — failures are logged and silently swallowed
 * so they never break the chat pipeline.
 */
@Service
@Slf4j
public class CogneeClient {

    private final CogneeProperties properties;
    /**
     * RestTemplate for long-lived operations (remember, forget, improve) — 120s
     * timeout
     */
    private final RestTemplate restTemplate;
    /** RestTemplate for recall — shorter timeout so user doesn't wait */
    private final RestTemplate searchRestTemplate;

    public CogneeClient(CogneeProperties properties, RestTemplateBuilder builder) {
        this.properties = properties;
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(120))
                .build();
        this.searchRestTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
    }

    // ── DTOs ──────────────────────────────────────────────────────

    @Data
    public static class AddResponse {
        private String id;
        private boolean success;
        private String message;
    }

    /**
     * A recall result carrying text, score, the source type, and provenance
     * metadata (dataId = ingested Data item id, documentName = source doc name,
     * datasetName = 命中结果所属的数据集名).
     */
    public record RecallResult(
            String text,
            double score,
            String source,
            String dataId,
            String documentName,
            String datasetName) {

        /** 便捷构造：无溯源元数据时的降级。 */
        public RecallResult(String text, double score, String source) {
            this(text, score, source, null, null, null);
        }
    }

    /** A graph node (entity) from cognee's knowledge graph. */
    public record GraphNode(String id, String name, String type) {
    }

    /** A graph edge (relation) from cognee's knowledge graph. */
    public record GraphEdge(String id, String source, String target, String label) {
    }

    /** Full graph response from /graph endpoint. */
    @Data
    public static class GraphResponse {
        private List<GraphNode> nodes;
        private List<GraphEdge> edges;
        private int totalNodes;
        private int totalEdges;
    }

    /**
     * Structured recall context combining text fragments with graph entities and
     * relations.
     * Returned by {@link #recallWithContext(String, String, int, String)}.
     */
    public record RecallWithContextResult(
            List<RecallResult> fragments,
            List<String> entities,
            List<CogneeRelationRecord> relations) {
        public boolean isEmpty() {
            return (fragments == null || fragments.isEmpty())
                    && (entities == null || entities.isEmpty())
                    && (relations == null || relations.isEmpty());
        }
    }

    public record CogneeRelationRecord(String source, String relation, String target) {
    }

    @Data
    public static class RememberRequest {
        private String content;
        @JsonProperty("dataset_name")
        private String datasetName = "main_dataset";
        @JsonProperty("session_id")
        private String sessionId;
        @JsonProperty("self_improvement")
        private boolean selfImprovement = true;
        @JsonProperty("run_in_background")
        private boolean runInBackground = false;
    }

    @Data
    public static class RecallRequest {
        private String query;
        @JsonProperty("top_k")
        private int topK;
        @JsonProperty("session_id")
        private String sessionId;
        private List<String> datasets;
        @JsonProperty("only_context")
        private boolean onlyContext = true;
        /**
         * 强制指定检索策略（如 "CHUNKS" / "RAG_COMPLETION" / "GRAPH_COMPLETION"）。
         * 为空时 cognee 走 auto_route 自动路由，无 cue 匹配会回退 GRAPH_COMPLETION，
         * 返回图结构而非文档正文。知识库检索需要正文片段，应显式指定 "CHUNKS"。
         */
        @JsonProperty("search_type")
        private String searchType;
    }

    @Data
    public static class RecallResultItem {
        private String text;
        private double score;
        private String source;
        /** 溯源元数据：入库 Data item id（对应 KnowledgeDocument.cogneeDataId） */
        @JsonProperty("data_id")
        private String dataId;
        /** 溯源元数据：源文档名（Cognee chunk payload 提供，可能为空） */
        @JsonProperty("document_name")
        private String documentName;
        @JsonProperty("chunk_id")
        private String chunkId;
        @JsonProperty("dataset_name")
        private String datasetName;

        public String getText() {
            return text != null ? text : "";
        }
    }

    @Data
    public static class RecallResponseDto {
        private List<RecallResultItem> results;
        private String status;

        public List<RecallResultItem> getResults() {
            return results != null ? results : List.of();
        }
    }

    @Data
    public static class ForgetRequest {
        private String dataset;
        @JsonProperty("data_id")
        private String dataId;
        private boolean everything;
        @JsonProperty("memory_only")
        private boolean memoryOnly;
    }

    @Data
    public static class ForgetResponse {
        private boolean success;
        private String message;
        private Map<String, Object> summary = new HashMap<>();
    }

    @Data
    public static class ImproveRequest {
        private String dataset;
    }

    @Data
    public static class ImproveResponse {
        private boolean success;
        private String message;
        private Map<String, Object> summary = new HashMap<>();
    }

    // ── remember / recall ─────────────────────────────────────────

    /**
     * v1.0 remember() — store content into cognee's permanent graph or session
     * cache.
     *
     * <p>
     * When no sessionId is passed, this is equivalent to add() + cognify() +
     * improve()
     * in a single call. The graph is built and self-improved automatically.
     *
     * <p>
     * When sessionId is passed, content goes to the session cache (fast, no entity
     * extraction).
     * With selfImprovement=true (default), a background improve() bridges it into
     * the permanent graph.
     *
     * @param content         Text to store
     * @param sessionId       Optional session ID for session-scoped memory (null =
     *                        permanent)
     * @param selfImprovement Whether to trigger background improve() (default true)
     * @return true if the operation succeeded, false otherwise
     */
    public boolean remember(String content, String sessionId, boolean selfImprovement) {
        if (!properties.isEnabled()) {
            log.debug("[Cognee] Integration disabled, skipping remember");
            return false;
        }
        try {
            String url = properties.getBaseUrl() + "/remember";
            RememberRequest req = new RememberRequest();
            req.setContent(content);
            if (sessionId != null && !sessionId.isBlank()) {
                req.setSessionId(sessionId);
            }
            req.setSelfImprovement(selfImprovement);
            // Run in background so the HTTP call returns quickly; cognee's cognify
            // (LLM entity extraction) is slow and we don't need to block on it.
            req.setRunInBackground(true);

            log.debug("[Cognee] remember: {} chars, session={}, selfImprovement={}",
                    content.length(), sessionId != null, selfImprovement);

            ResponseEntity<AddResponse> response = restTemplate.postForEntity(
                    url, new HttpEntity<>(req), AddResponse.class);

            boolean success = response.getBody() != null && response.getBody().isSuccess();
            if (success) {
                log.info("[Cognee] remember() succeeded (id={})",
                        response.getBody().getId());
            } else {
                log.warn("[Cognee] remember() returned failure: {}",
                        response.getBody() != null ? response.getBody().getMessage() : "null body");
            }
            return success;
        } catch (Exception e) {
            log.warn("[Cognee] remember() failed ({} chars): {}",
                    content.length(), e.getMessage());
            return false;
        }
    }

    /** Convenience: remember without session (permanent graph write). */
    public boolean remember(String content) {
        return remember(content, null, true);
    }

    /**
     * Remember with a specific dataset name — used by KnowledgeBaseService
     * to write documents into a per-knowledge-base Cognee dataset (kb_{id}).
     *
     * @param content     Text to store
     * @param datasetName Target Cognee dataset (e.g., "kb_{uuid}")
     * @return true if the operation succeeded
     */
    public boolean remember(String content, String datasetName) {
        return rememberWithId(content, datasetName) != null;
    }

    /**
     * Remember with a dataset name and return the generated data_id.
     *
     * <p>与 {@link #remember(String, String)} 语义一致，但额外返回 Cognee
     * 分配的 data_id，供调用方持久化后用于 {@link #forgetData(String, String)}
     * 精确删除，避免删除单条内容时重建整个 dataset。
     *
     * @param content     Text to store
     * @param datasetName Target Cognee dataset (e.g., "kb_{uuid}")
     * @return Cognee data_id，成功时返回；失败/未启用返回 null
     */
    public String rememberWithId(String content, String datasetName) {
        if (!properties.isEnabled()) {
            log.debug("[Cognee] Integration disabled, skipping remember");
            return null;
        }
        try {
            String url = properties.getBaseUrl() + "/remember";
            RememberRequest req = new RememberRequest();
            req.setContent(content);
            req.setDatasetName(datasetName);
            req.setSelfImprovement(true);
            // 必须前台执行（run_in_background=false）：cognee 1.2.2 的后台 remember
            // 任务不可靠，可能只写原始文本、cognify（chunk+向量+图）未完成，导致
            // 后续 CHUNKS/GRAPH 检索不到内容。调用方（ingestToCogneeAsync）本身已是
            // Java @Async 后台线程，此处同步等待不会阻塞用户请求。
            req.setRunInBackground(false);

            log.debug("[Cognee] remember: {} chars, dataset={}", content.length(), datasetName);

            ResponseEntity<AddResponse> response = restTemplate.postForEntity(
                    url, new HttpEntity<>(req), AddResponse.class);

            AddResponse body = response.getBody();
            boolean success = body != null && body.isSuccess();
            if (success) {
                log.info("[Cognee] remember() succeeded for dataset={} (id={})",
                        datasetName, body.getId());
                return body.getId();
            } else {
                log.warn("[Cognee] remember() returned failure for dataset={}: {}",
                        datasetName, body != null ? body.getMessage() : "null body");
                return null;
            }
        } catch (Exception e) {
            log.warn("[Cognee] remember() failed for dataset={} ({} chars): {}",
                    datasetName, content.length(), e.getMessage());
            return null;
        }
    }

    /**
     * v1.0 recall() — auto-routed retrieval with session awareness.
     *
     * <p>
     * Uses cognee's intelligent query router to pick the best retrieval strategy
     * (graph completion, RAG, chunks, summaries, etc.) based on the query.
     *
     * <p>
     * When sessionId is provided, recall searches the session cache first
     * (returning
     * source="session" results), then falls back to the permanent graph
     * (source="graph").
     * When sessionId is null, only the permanent graph is searched.
     *
     * @param userId    User identifier (currently not passed to cognee, reserved
     *                  for multi-tenant)
     * @param query     Natural language query
     * @param topK      Maximum number of results
     * @param sessionId Optional conversation ID for session-scoped recall (null =
     *                  permanent only)
     * @return List of scored results with source tags, or empty list on failure
     */
    public List<RecallResult> recall(String userId, String query, int topK, String sessionId) {
        if (!properties.isEnabled()) {
            log.debug("[Cognee] Integration disabled, skipping recall");
            return List.of();
        }
        try {
            String url = properties.getBaseUrl() + "/recall";
            RecallRequest req = new RecallRequest();
            req.setQuery(query);
            req.setTopK(Math.min(topK, properties.getSearch().getTopK()));
            req.setOnlyContext(true); // Only retrieve context, don't generate LLM answer
            // 显式指定 CHUNKS：避免默认 AUTO_ROUTE 在无匹配时回退成 GRAPH_COMPLETION
            // 返回整张知识图谱（会混入无关的个人资料实体），而 CHUNKS 无匹配时返回空
            req.setSearchType("CHUNKS");
            if (sessionId != null && !sessionId.isBlank()) {
                req.setSessionId(sessionId);
            }

            log.debug("[Cognee] recall: query='{}', topK={}, searchType=CHUNKS, session={}",
                    query, req.getTopK(), sessionId != null);

            ResponseEntity<RecallResponseDto> response = searchRestTemplate.postForEntity(
                    url, new HttpEntity<>(req), RecallResponseDto.class);

            if (response.getBody() == null) {
                log.warn("[Cognee] Empty response from recall");
                return List.of();
            }

            double threshold = properties.getSearch().getThreshold();
            List<RecallResult> results = response.getBody().getResults().stream()
                    .filter(item -> !item.getText().isBlank())
                    .filter(item -> item.getScore() >= threshold || item.getScore() == 0.0)
                    .map(item -> new RecallResult(
                            item.getText(),
                            item.getScore() >= 0.01 ? item.getScore() : threshold,
                            item.getSource() != null ? item.getSource() : "graph",
                            item.getDataId(),
                            item.getDocumentName(),
                            item.getDatasetName()))
                    .toList();

            log.info("[Cognee] recall returned {} results (sources: {})",
                    results.size(),
                    results.stream().map(RecallResult::source).distinct().toList());

            return results;
        } catch (Exception e) {
            log.warn("[Cognee] recall() failed for query='{}': {}", query, e.getMessage());
            return List.of();
        }
    }

    /** Convenience: recall without session (permanent graph only). */
    public List<RecallResult> recall(String userId, String query, int topK) {
        return recall(userId, query, topK, null);
    }

    /**
     * Recall from specific datasets (knowledge bases).
     * Pass a list of Cognee dataset names (e.g., ["kb_uuid1", "kb_uuid2"])
     * to restrict the search to those knowledge bases only.
     *
     * <p>默认使用 CHUNKS（纯文本片段）。知识库问答需要文档正文
     * （时间线、数字、名单等），而非图结构描述。
     *
     * @param query    Natural language query
     * @param topK     Maximum results
     * @param datasets List of dataset names to search
     * @return List of scored results
     */
    public List<RecallResult> recallFromDatasets(String query, int topK, List<String> datasets) {
        return recallFromDatasets(query, topK, datasets, "CHUNKS");
    }

    /**
     * Recall from specific datasets with an explicit retrieval strategy.
     *
     * @param searchType Cognee 检索策略：CHUNKS（正文）/ GRAPH_COMPLETION（图关系）/
     *                   HYBRID_COMPLETION（文本+图混合）等
     */
    public List<RecallResult> recallFromDatasets(String query, int topK, List<String> datasets,
            String searchType) {
        if (!properties.isEnabled()) {
            return List.of();
        }
        try {
            String url = properties.getBaseUrl() + "/recall";
            RecallRequest req = new RecallRequest();
            req.setQuery(query);
            req.setTopK(Math.min(topK, properties.getSearch().getTopK()));
            req.setOnlyContext(true);
            req.setSearchType(searchType);
            if (datasets != null && !datasets.isEmpty()) {
                req.setDatasets(datasets);
            }

            log.debug("[Cognee] recallFromDatasets: query='{}', topK={}, datasets={}",
                    query, req.getTopK(), datasets);

            ResponseEntity<RecallResponseDto> response = searchRestTemplate.postForEntity(
                    url, new HttpEntity<>(req), RecallResponseDto.class);

            if (response.getBody() == null) {
                return List.of();
            }

            double threshold = properties.getSearch().getThreshold();
            return response.getBody().getResults().stream()
                    .filter(item -> !item.getText().isBlank())
                    .filter(item -> item.getScore() >= threshold || item.getScore() == 0.0)
                    .map(item -> new RecallResult(
                            item.getText(),
                            item.getScore() >= 0.01 ? item.getScore() : threshold,
                            item.getSource() != null ? item.getSource() : "graph",
                            item.getDataId(),
                            item.getDocumentName(),
                            item.getDatasetName()))
                    .toList();
        } catch (Exception e) {
            log.warn("[Cognee] recallFromDatasets() failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * v1.0 recall with structured graph context — returns text fragments plus
     * related entities and relations from the knowledge graph.
     *
     * <p>
     * This enriches the raw recall() text results with graph structure so the LLM
     * can reason about relationships (e.g. "kyle uses Java" → can infer "kyle
     * develops KChat which uses Java" without explicit mention).
     *
     * <p>
     * Entity/relation extraction strategy (priority order):
     * <ol>
     * <li><b>Parse from recall text</b> — Cognee recall already returns nodes and
     * connections in the text. We parse "Node: X" and "A --[R]--> B" patterns.</li>
     * <li><b>Fetch from /graph endpoint</b> — Fallback when recall text doesn't
     * contain structured node/connection data (e.g., early session memories
     * that haven't been cognified yet).</li>
     * </ol>
     *
     * @param userId    User identifier
     * @param query     Natural language query
     * @param topK      Maximum number of text results
     * @param sessionId Optional conversation ID for session-scoped recall
     * @return Structured context with fragments, entities, and relations
     */
    public RecallWithContextResult recallWithContext(String userId, String query, int topK, String sessionId) {
        if (!properties.isEnabled()) {
            return new RecallWithContextResult(List.of(), List.of(), List.of());
        }

        // Step 1: Get text fragments via recall
        List<RecallResult> fragments = recall(userId, query, topK, sessionId);
        if (fragments.isEmpty()) {
            return new RecallWithContextResult(List.of(), List.of(), List.of());
        }

        // Step 2: Parse entities and relations from recall text
        // Cognee recall text contains "Node: X" and "A --[R]--> B" patterns
        var parsed = parseEntitiesAndRelations(fragments);
        List<String> entities = parsed.entities();
        List<CogneeRelationRecord> relations = parsed.relations();

        // Step 3: If text parsing didn't find enough, try graph endpoint as fallback
        if (entities.isEmpty() || relations.isEmpty()) {
            try {
                GraphResponse graph = getGraph();
                if (graph != null && graph.getNodes() != null && !graph.getNodes().isEmpty()) {
                    // Merge graph entities/relations with parsed ones
                    Set<String> entitySet = new LinkedHashSet<>(entities);
                    Set<CogneeRelationRecord> relationSet = new LinkedHashSet<>(relations);

                    // Match graph nodes against mentioned entities
                    Set<String> mentionedKeywords = extractMentionedKeywords(fragments);
                    Map<String, String> nodeNameToId = new HashMap<>();
                    for (GraphNode node : graph.getNodes()) {
                        if (node.name() != null && mentionedKeywords.contains(node.name().toLowerCase())) {
                            entitySet.add(node.name());
                            nodeNameToId.put(node.id(), node.name());
                        }
                    }

                    // Extract relations
                    if (graph.getEdges() != null) {
                        for (GraphEdge edge : graph.getEdges()) {
                            String sourceName = nodeNameToId.getOrDefault(edge.source(), edge.source());
                            String targetName = nodeNameToId.getOrDefault(edge.target(), edge.target());
                            boolean sourceMentioned = entitySet.contains(sourceName);
                            boolean targetMentioned = entitySet.contains(targetName);
                            if (sourceMentioned || targetMentioned) {
                                String label = edge.label() != null ? edge.label() : "related_to";
                                relationSet.add(new CogneeRelationRecord(sourceName, label, targetName));
                                if (!entitySet.contains(targetName) && !targetName.equals(sourceName)) {
                                    entitySet.add(targetName);
                                }
                            }
                        }
                    }

                    entities = new ArrayList<>(entitySet);
                    relations = new ArrayList<>(relationSet);
                }
            } catch (Exception e) {
                log.debug("[Cognee] Graph fallback failed: {}", e.getMessage());
            }
        }

        log.info("[Cognee] recallWithContext: fragments={}, entities={}, relations={}",
                fragments.size(), entities.size(), relations.size());

        return new RecallWithContextResult(fragments, entities, relations);
    }

    /**
     * Parse entity names and relations directly from Cognee recall text.
     *
     * <p>
     * Cognee's recall returns formatted text with:
     * <ul>
     * <li>"Node: entityName" — entity definitions</li>
     * <li>"source --[relation]--> target" — relation triples</li>
     * </ul>
     *
     * <p>
     * This is more reliable than getGraph() because it works with session-level
     * memories that haven't been cognified into the permanent graph yet.
     */
    private record ParsedContext(List<String> entities, List<CogneeRelationRecord> relations) {
    }

    private ParsedContext parseEntitiesAndRelations(List<RecallResult> fragments) {
        Set<String> entities = new LinkedHashSet<>();
        Set<CogneeRelationRecord> relations = new LinkedHashSet<>();

        for (RecallResult fragment : fragments) {
            String text = fragment.text();
            if (text == null || text.isBlank())
                continue;

            // Extract entity names from "Node: X" patterns
            // Cognee format: "Node: kchat智能助手" or "Node: product"
            Pattern nodePattern = Pattern.compile("Node:\\s*([^\\n]+?)(?:\\s*$|\\n)");
            Matcher nodeMatcher = nodePattern.matcher(text);
            while (nodeMatcher.find()) {
                String name = nodeMatcher.group(1).trim();
                if (!name.isEmpty()) {
                    entities.add(name);
                }
            }

            // Extract relations from "source --[relation]--> target" patterns
            // Cognee format: "kchat --[is_a]--> product (optional description)"
            Pattern relPattern = Pattern.compile(
                    "([^\\s\\-]+(?:\\s[^\\s\\-]+)*?)\\s*--\\[([^\\]]+)\\]-->\\s*([^\\s(\\n]+(?:\\s[^\\s(\\n]+)*?)(?:\\s*\\(|\\s*$|\\n)");
            Matcher relMatcher = relPattern.matcher(text);
            while (relMatcher.find()) {
                String source = relMatcher.group(1).trim();
                String relation = relMatcher.group(2).trim();
                String target = relMatcher.group(3).trim();
                if (!source.isEmpty() && !target.isEmpty()) {
                    relations.add(new CogneeRelationRecord(source, relation, target));
                    entities.add(source);
                    entities.add(target);
                }
            }

            // Also try simpler relation pattern: "X --[R]--> Y"
            // This catches edge cases where the first pattern doesn't match
            Pattern simpleRelPattern = Pattern.compile(
                    "([^\\s]+)\\s*--\\[([^\\]]+)\\]-->\\s*([^\\s]+)");
            Matcher simpleMatcher = simpleRelPattern.matcher(text);
            while (simpleMatcher.find()) {
                String source = simpleMatcher.group(1).trim();
                String relation = simpleMatcher.group(2).trim();
                String target = simpleMatcher.group(3).trim();
                if (!source.isEmpty() && !target.isEmpty() && !source.equals(target)) {
                    var rel = new CogneeRelationRecord(source, relation, target);
                    if (!relations.contains(rel)) {
                        relations.add(rel);
                        entities.add(source);
                        entities.add(target);
                    }
                }
            }
        }

        return new ParsedContext(new ArrayList<>(entities), new ArrayList<>(relations));
    }

    /**
     * Fetch the full knowledge graph (nodes + edges) from cognee.
     *
     * @return Graph response, or null on failure
     */
    public GraphResponse getGraph() {
        if (!properties.isEnabled()) {
            return null;
        }
        try {
            String url = properties.getBaseUrl() + "/graph";
            ResponseEntity<GraphResponse> response = searchRestTemplate.getForEntity(url, GraphResponse.class);
            return response.getBody();
        } catch (Exception e) {
            log.debug("[Cognee] getGraph() failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Fetch the knowledge graph for a specific dataset (knowledge base).
     *
     * @param datasetName Cognee dataset name (e.g., "kb_{uuid}")
     * @return Graph response, or null on failure
     */
    public GraphResponse getGraph(String datasetName) {
        if (!properties.isEnabled()) {
            return null;
        }
        try {
            String url = properties.getBaseUrl() + "/graph?dataset=" + datasetName;
            ResponseEntity<GraphResponse> response = searchRestTemplate.getForEntity(url, GraphResponse.class);
            return response.getBody();
        } catch (Exception e) {
            log.debug("[Cognee] getGraph(dataset={}) failed: {}", datasetName, e.getMessage());
            return null;
        }
    }

    /**
     * Extract keywords from recall fragments for graph node matching.
     * Uses a simple heuristic: filter stop words from tokenized text.
     *
     * <p>
     * Note: This is a fallback for the graph endpoint. Primary entity/relation
     * extraction is done by {@link #parseEntitiesAndRelations(List)} which parses
     * the recall text directly.
     */
    private Set<String> extractMentionedKeywords(List<RecallResult> fragments) {
        Set<String> entities = new HashSet<>();
        for (RecallResult fragment : fragments) {
            String text = fragment.text().toLowerCase();
            // Split by common delimiters and look for meaningful words
            String[] words = text.split("[\\s,.;:!?\\-\\(\\)\\[\\]\\{\\}\"'`/\\\\]+");
            for (String word : words) {
                if (word.length() >= 2 && !isStopWord(word)) {
                    entities.add(word);
                }
            }
        }
        return entities;
    }

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "shall",
            "should", "may", "might", "can", "could", "to", "of", "in", "for",
            "on", "with", "at", "by", "from", "as", "into", "through", "during",
            "before", "after", "above", "below", "between", "out", "off", "over",
            "under", "again", "further", "then", "once", "here", "there", "when",
            "where", "why", "how", "all", "both", "each", "few", "more", "most",
            "other", "some", "such", "no", "nor", "not", "only", "own", "same",
            "so", "than", "too", "very", "just", "because", "but", "and", "or",
            "if", "while", "about", "up", "down", "kchat", "chat", "app",
            "知道", "什么", "怎么", "如何", "可以", "一下", "这个", "那个",
            "我们", "你们", "他们", "自己", "没有", "就是", "这样", "那样");

    private boolean isStopWord(String word) {
        return STOP_WORDS.contains(word.toLowerCase());
    }

    // ── health ────────────────────────────────────────────────────

    /**
     * Health check — ping the cognee service.
     *
     * @return true if cognee is reachable and responsive
     */
    public boolean isHealthy() {
        if (!properties.isEnabled())
            return false;
        try {
            String url = properties.getBaseUrl() + "/health";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.debug("[Cognee] Health check failed: {}", e.getMessage());
            return false;
        }
    }

    // ── forget / improve primitives ───────────────────────────────

    /**
     * Delete an entire dataset from cognee (graph + vector + raw files).
     * Use with caution — there is no undo.
     *
     * @param dataset Dataset name, e.g. "main_dataset"
     * @return true on success
     */
    public boolean forgetDataset(String dataset) {
        ForgetRequest req = new ForgetRequest();
        req.setDataset(dataset);
        return forgetInternal(req, "forgetDataset");
    }

    /**
     * Delete graph nodes, edges, and vector embeddings for a dataset while
     * preserving the
     * underlying dataset records. This allows re-cognifying the raw content
     * afterwards.
     *
     * @param dataset Dataset name, e.g. "main_dataset"
     * @return true on success
     */
    public boolean forgetDatasetMemoryOnly(String dataset) {
        ForgetRequest req = new ForgetRequest();
        req.setDataset(dataset);
        req.setMemoryOnly(true);
        return forgetInternal(req, "forgetDatasetMemoryOnly");
    }

    /**
     * Delete ALL data the user owns in cognee.
     *
     * <p>
     * <b>DANGER:</b> Equivalent to wiping every dataset / graph / vector entry.
     * This is only exposed for testing / factory-reset scenarios.
     *
     * @return true on success
     */
    public boolean forgetEverything() {
        ForgetRequest req = new ForgetRequest();
        req.setEverything(true);
        return forgetInternal(req, "forgetEverything");
    }

    /**
     * 精确删除单条记忆（data_id 及其关联的节点/边/向量）。
     *
     * <p>Cognee v1.0 的 forget 支持按 data_id 精确删除：该节点及其关系边一起消除，
     * 图的其余部分保持完整，无需重建整个 dataset。用于删除单条文档/单条记忆时，
     * 避免「清空整个 dataset + 全量重灌」的开销。
     *
     * @param dataId  要删除的 data_id（入库时 remember 返回的 id）
     * @param dataset 所属 dataset（如 "kb_{uuid}"，可空）
     * @return true on success
     */
    public boolean forgetData(String dataId, String dataset) {
        if (dataId == null || dataId.isBlank()) {
            log.warn("[Cognee] forgetData skipped: empty dataId");
            return false;
        }
        ForgetRequest req = new ForgetRequest();
        req.setDataId(dataId);
        if (dataset != null && !dataset.isBlank()) {
            req.setDataset(dataset);
        }
        return forgetInternal(req, "forgetData(" + dataId + ")");
    }

    /**
     * Trigger cognee.improve() to self-derive cross-document relationships in the
     * graph.
     *
     * <p>
     * Usually triggered automatically by remember(selfImprovement=true), but
     * exposed
     * for manual invocation after bulk imports or when selfImprovement was
     * disabled.
     *
     * @param dataset Dataset name (pass null for default dataset)
     * @return true on success
     */
    public boolean improve(String dataset) {
        if (!properties.isEnabled()) {
            log.debug("[Cognee] Integration disabled, skipping improve");
            return false;
        }
        try {
            String url = properties.getBaseUrl() + "/improve";
            ImproveRequest req = new ImproveRequest();
            if (dataset != null && !dataset.isBlank()) {
                req.setDataset(dataset);
            }
            ResponseEntity<ImproveResponse> resp = restTemplate.postForEntity(
                    url, new HttpEntity<>(req), ImproveResponse.class);
            boolean ok = resp.getBody() != null && resp.getBody().isSuccess();
            if (ok) {
                log.info("[Cognee] improve() succeeded for dataset={}: {}",
                        req.getDataset(),
                        resp.getBody().getMessage());
            } else {
                log.warn("[Cognee] improve() returned failure for dataset={}: {}",
                        req.getDataset(),
                        resp.getBody() != null ? resp.getBody().getMessage() : "null body");
            }
            return ok;
        } catch (Exception e) {
            log.warn("[Cognee] improve() failed for dataset={}: {}", dataset, e.getMessage());
            return false;
        }
    }

    private boolean forgetInternal(ForgetRequest req, String opName) {
        if (!properties.isEnabled()) {
            log.debug("[Cognee] Integration disabled, skipping {}", opName);
            return false;
        }
        try {
            String url = properties.getBaseUrl() + "/forget";
            ResponseEntity<ForgetResponse> resp = restTemplate.postForEntity(
                    url, new HttpEntity<>(req), ForgetResponse.class);
            boolean ok = resp.getBody() != null && resp.getBody().isSuccess();
            if (ok) {
                log.info("[Cognee] {} succeeded: {}", opName,
                        resp.getBody().getMessage());
            } else {
                log.warn("[Cognee] {} returned failure: {}", opName,
                        resp.getBody() != null ? resp.getBody().getMessage() : "null body");
            }
            return ok;
        } catch (Exception e) {
            log.warn("[Cognee] {} failed: {}", opName, e.getMessage());
            return false;
        }
    }
}
