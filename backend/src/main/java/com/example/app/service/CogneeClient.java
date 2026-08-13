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

/**
 * Java HTTP client for the Cognee AI Memory Platform v1.0 REST API.
 *
 * <p>Cognee is an open-source memory platform that builds self-hosted knowledge graphs
 * from ingested data, providing persistent long-term memory for AI agents via
 * combined vector + graph search.
 *
 * <h3>v1.0 API Methods</h3>
 * <ul>
 *   <li><b>remember()</b> — Store data into permanent graph or session cache.
 *       Equivalent to legacy add() + cognify() + improve() in a single call.
 *       With selfImprovement=true (default), a background improve() bridges
 *       session memories into the permanent graph automatically.</li>
 *   <li><b>recall()</b> — Auto-routed retrieval with session awareness.
 *       Uses cognee's intelligent query router to pick the best retrieval strategy
 *       (graph completion, RAG, chunks, summaries, etc.) based on the query.</li>
 *   <li><b>forgetDataset / forgetEverything</b> — Unified deletion API.</li>
 *   <li><b>improve()</b> — Self-derive cross-document relationships.
 *       Usually triggered automatically by remember(selfImprovement=true),
 *       but exposed for manual invocation after bulk imports.</li>
 *   <li><b>isHealthy()</b> — Health check.</li>
 * </ul>
 *
 * <h3>Integration Points</h3>
 * <ul>
 *   <li><b>LongTermMemoryStage</b> — Calls {@link #recall(String, String, int)} as Path 3
 *       to retrieve graph-enhanced memories and merges them into the conversation context
 *       alongside the existing JPA-based memory results.</li>
 *   <li><b>CogneeMemoryIndexStage</b> — Calls {@link #remember(String)} after the LLM
 *       responds to index structured memories into cognee's knowledge graph.</li>
 *   <li><b>CogneeSyncService</b> — Calls {@link #remember(String)} when rebuilding the
 *       graph from JPA truth after memory deletions.</li>
 * </ul>
 *
 * <p>All requests are non-critical — failures are logged and silently swallowed
 * so they never break the chat pipeline.
 */
@Service
@Slf4j
public class CogneeClient {

    private final CogneeProperties properties;
    /** RestTemplate for long-lived operations (remember, forget, improve) — 120s timeout */
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

    /** A recall result carrying text, score, and the source type (graph/session/trace). */
    public record RecallResult(String text, double score, String source) {}

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
    }

    @Data
    public static class RecallResultItem {
        private String text;
        private double score;
        private String source;

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
     * v1.0 remember() — store content into cognee's permanent graph or session cache.
     *
     * <p>When no sessionId is passed, this is equivalent to add() + cognify() + improve()
     * in a single call. The graph is built and self-improved automatically.
     *
     * <p>When sessionId is passed, content goes to the session cache (fast, no entity extraction).
     * With selfImprovement=true (default), a background improve() bridges it into the permanent graph.
     *
     * @param content           Text to store
     * @param sessionId         Optional session ID for session-scoped memory (null = permanent)
     * @param selfImprovement   Whether to trigger background improve() (default true)
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
     * v1.0 recall() — auto-routed retrieval with session awareness.
     *
     * <p>Uses cognee's intelligent query router to pick the best retrieval strategy
     * (graph completion, RAG, chunks, summaries, etc.) based on the query.
     *
     * <p>When sessionId is provided, recall searches the session cache first (returning
     * source="session" results), then falls back to the permanent graph (source="graph").
     * When sessionId is null, only the permanent graph is searched.
     *
     * @param userId    User identifier (currently not passed to cognee, reserved for multi-tenant)
     * @param query     Natural language query
     * @param topK      Maximum number of results
     * @param sessionId Optional conversation ID for session-scoped recall (null = permanent only)
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
            if (sessionId != null && !sessionId.isBlank()) {
                req.setSessionId(sessionId);
            }

            log.debug("[Cognee] recall: query='{}', topK={}, session={}", query, req.getTopK(),
                    sessionId != null);

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
                            item.getSource() != null ? item.getSource() : "graph"))
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

    // ── health ────────────────────────────────────────────────────

    /**
     * Health check — ping the cognee service.
     *
     * @return true if cognee is reachable and responsive
     */
    public boolean isHealthy() {
        if (!properties.isEnabled()) return false;
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
     * Delete graph nodes, edges, and vector embeddings for a dataset while preserving the
     * underlying dataset records. This allows re-cognifying the raw content afterwards.
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
     * <p><b>DANGER:</b> Equivalent to wiping every dataset / graph / vector entry.
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
     * Trigger cognee.improve() to self-derive cross-document relationships in the graph.
     *
     * <p>Usually triggered automatically by remember(selfImprovement=true), but exposed
     * for manual invocation after bulk imports or when selfImprovement was disabled.
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
