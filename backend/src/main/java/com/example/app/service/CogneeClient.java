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
import java.util.stream.Collectors;

/**
 * Java HTTP client for the Cognee AI Memory Platform REST API.
 *
 * Cognee is an open-source memory platform that builds self-hosted knowledge graphs
 * from ingested data, providing persistent long-term memory for AI agents via
 * combined vector + graph search.
 *
 * <p>This client communicates with the cognee Python service started via:
 * <pre>{@code
 * import cognee
 * await cognee.serve()  # Starts the REST API server on port 8000
 * }</pre>
 *
 * <h3>Integration Points</h3>
 * <ul>
 *   <li><b>LongTermMemoryStage</b> — Calls {@link #search(String, String, int)} to retrieve
 *       relevant memories from cognee and merges them into the conversation context alongside
 *       the existing JPA-based memory results.</li>
 *   <li><b>CogneeMemoryIndexStage</b> — Calls {@link #add(String, Map)} after the LLM responds
 *       to index the conversation exchange into cognee's knowledge graph.</li>
 *   <li><b>Frontend Memory API</b> — The frontend can directly call cognee for document
 *       ingestion and recall through the proxy controller.</li>
 * </ul>
 *
 * <p>All requests are non-critical — failures are logged and silently swallowed
 * so they never break the chat pipeline.
 */
@Service
@Slf4j
public class CogneeClient {

    private final CogneeProperties properties;
    /** RestTemplate for long-lived operations (add, health) — 30s timeout */
    private final RestTemplate restTemplate;
    /** RestTemplate for search — shorter timeout so user doesn't wait */
    private final RestTemplate searchRestTemplate;

    public CogneeClient(CogneeProperties properties, RestTemplateBuilder builder) {
        this.properties = properties;
        this.restTemplate = builder
                .setConnectTimeout(java.time.Duration.ofSeconds(5))
                .setReadTimeout(java.time.Duration.ofSeconds(120))
                .build();
        this.searchRestTemplate = builder
                .setConnectTimeout(java.time.Duration.ofSeconds(3))
                .setReadTimeout(java.time.Duration.ofSeconds(5))
                .build();
    }

    /**
     * DTO matching cognee's expected request body for the /add endpoint.
     */
    @Data
    public static class AddRequest {
        private String content;
        private Map<String, Object> metadata = new HashMap<>();

        public AddRequest(String content) {
            this.content = content;
        }

        public AddRequest(String content, Map<String, Object> metadata) {
            this.content = content;
            this.metadata = metadata != null ? metadata : new HashMap<>();
        }
    }

    /**
     * DTO matching cognee's response for the /add endpoint.
     */
    @Data
    public static class AddResponse {
        private String id;
        private boolean success;
        private String message;
    }

    /**
     * DTO matching cognee's expected request body for the /search endpoint.
     */
    @Data
    public static class SearchRequest {
        private String query;
        @JsonProperty("top_k")
        private int topK;

        public SearchRequest(String query, int topK) {
            this.query = query;
            this.topK = topK;
        }
    }

    /**
     * DTO matching individual search result items from cognee.
     */
    @Data
    public static class SearchResultItem {
        private String id;
        private String text;
        private double score;
        private Map<String, Object> metadata;
        @JsonProperty("text_content")
        private String textContent;
        @JsonProperty("content")
        private String content;

        /** Get the best available text representation */
        public String getText() {
            if (text != null && !text.isEmpty()) return text;
            if (textContent != null && !textContent.isEmpty()) return textContent;
            if (content != null && !content.isEmpty()) return content;
            return "";
        }
    }

    /**
     * DTO matching cognee's response for the /search endpoint.
     * Handles multiple response formats for compatibility.
     */
    @Data
    public static class SearchResponse {
        private List<SearchResultItem> results;
        private List<SearchResultItem> data;
        private String status;

        /** Get the best available list of results */
        public List<SearchResultItem> getResults() {
            if (results != null) return results;
            if (data != null) return data;
            return List.of();
        }
    }

    /**
     * Search cognee for relevant memories matching the query.
     *
     * @param userId  User identifier for tenant isolation
     * @param query   Natural language query to search by
     * @param topK    Maximum number of results to return
     * @return List of relevant text snippets, or empty list on failure
     */
    public List<String> search(String userId, String query, int topK) {
        if (!properties.isEnabled()) {
            log.debug("[Cognee] Integration disabled, skipping search");
            return List.of();
        }

        try {
            String url = properties.getBaseUrl() + "/search";
            SearchRequest request = new SearchRequest(query, Math.min(topK, properties.getSearch().getTopK()));

            log.debug("[Cognee] Searching: query='{}', topK={}, url={}", query, request.topK, url);

            ResponseEntity<SearchResponse> response = searchRestTemplate.postForEntity(
                    url, new HttpEntity<>(request), SearchResponse.class);

            if (response.getBody() == null) {
                log.warn("[Cognee] Empty response from search");
                return List.of();
            }

            List<String> results = response.getBody().getResults().stream()
                    .filter(item -> item.getScore() >= properties.getSearch().getThreshold())
                    .filter(item -> !item.getText().isBlank())
                    .map(SearchResultItem::getText)
                    .collect(Collectors.toList());

            log.info("[Cognee] Search returned {} relevant results (from {} raw)", results.size(),
                    response.getBody().getResults().size());

            return results;

        } catch (Exception e) {
            log.warn("[Cognee] Search failed for query='{}': {}", query, e.getMessage());
            return List.of();
        }
    }

    /**
     * Add content to cognee for indexing into the knowledge graph.
     *
     * @param content  Text content to index
     * @param metadata Optional metadata (e.g., conversationId, userId, messageType)
     * @return true if the operation succeeded, false otherwise
     */
    public boolean add(String content, Map<String, Object> metadata) {
        if (!properties.isEnabled()) {
            log.debug("[Cognee] Integration disabled, skipping add");
            return false;
        }

        try {
            String url = properties.getBaseUrl() + "/add";
            AddRequest request = new AddRequest(content, metadata);

            log.debug("[Cognee] Adding content: {} chars, metadata={}", content.length(), metadata);

            ResponseEntity<AddResponse> response = restTemplate.postForEntity(
                    url, new HttpEntity<>(request), AddResponse.class);

            boolean success = response.getBody() != null && response.getBody().isSuccess();
            if (success) {
                log.info("[Cognee] Successfully indexed content (id={})",
                        response.getBody().getId());
            } else {
                log.warn("[Cognee] Add returned failure: {}",
                        response.getBody() != null ? response.getBody().getMessage() : "null body");
            }

            return success;

        } catch (Exception e) {
            log.warn("[Cognee] Add failed (LLM entity extraction timeout): {} ({} chars)",
                    e.getMessage(), content.length());
            return false;
        }
    }

    /**
     * Convenience: add content without metadata.
     */
    public boolean add(String content) {
        return add(content, Map.of());
    }

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
}
