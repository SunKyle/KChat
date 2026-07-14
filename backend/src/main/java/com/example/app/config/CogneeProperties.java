package com.example.app.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the Cognee AI Memory Platform integration.
 *
 * Cognee provides persistent long-term memory via knowledge graphs + vector search.
 * This configures the connection to the cognee Python service (started via `cognee.serve()`).
 *
 * Default base-url points to cognee's default FastAPI server on localhost:8000.
 * All endpoints are configurable in application.yml under the "cognee" prefix.
 *
 * Settings:
 *   cognee.enabled          — Enable/disable cognee integration entirely
 *   cognee.base-url         — Base URL of the cognee REST API server
 *   cognee.search.top-k     — Default number of search results to return
 *   cognee.search.threshold — Minimum similarity score threshold (0.0–1.0)
 *   cognee.index.enabled    — Whether to auto-index conversations to cognee
 */
@Data
@Component
@ConfigurationProperties(prefix = "cognee")
public class CogneeProperties {

    /** Master switch — set to false to bypass cognee entirely */
    private boolean enabled = false;

    /** Base URL of the cognee REST API server */
    private String baseUrl = "http://localhost:8000";

    /** Search configuration */
    private Search search = new Search();

    /** Index configuration */
    private Index index = new Index();

    @Data
    public static class Search {
        /** Default number of search results per query */
        private int topK = 5;

        /** Minimum relevance threshold (0.0–1.0). Results below this are filtered out */
        private double threshold = 0.3;
    }

    @Data
    public static class Index {
        /** Auto-index conversations after each AI response */
        private boolean enabled = true;
    }
}
