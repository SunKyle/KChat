package com.example.app.service.impl;

import com.example.app.config.WebSearchConfig;
import com.example.app.dto.WebSearchResult;
import com.example.app.dto.WebSearchResult.SearchSnippet;
import com.example.app.service.WebSearchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class WebSearchServiceImpl implements WebSearchService {

    private static final Logger log = LoggerFactory.getLogger(WebSearchServiceImpl.class);
    private static final String DUCKDUCKGO_API = "https://api.duckduckgo.com/";

    private final WebSearchConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public WebSearchServiceImpl(WebSearchConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public WebSearchResult search(String query) {
        if (!config.isEnabled()) {
            return WebSearchResult.builder()
                    .query(query)
                    .snippets(List.of())
                    .timestamp(System.currentTimeMillis())
                    .status("disabled")
                    .build();
        }

        List<SearchSnippet> snippets = new ArrayList<>();
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = DUCKDUCKGO_API + "?q=" + encodedQuery + "&format=json&no_html=1&no_redirect=1";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                    .header("User-Agent", "KChat/1.0")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());

                String abstractText = root.path("AbstractText").asText(null);
                String abstractUrl = root.path("AbstractURL").asText(null);
                if (abstractText != null && !abstractText.isEmpty()) {
                    snippets.add(SearchSnippet.builder()
                            .title(root.path("Heading").asText(""))
                            .url(abstractUrl)
                            .snippet(abstractText)
                            .build());
                }

                JsonNode relatedTopics = root.path("RelatedTopics");
                if (relatedTopics.isArray()) {
                    for (JsonNode topic : relatedTopics) {
                        if (snippets.size() >= config.getMaxResults()) break;
                        String text = topic.path("Text").asText(null);
                        String firstUrl = topic.path("FirstURL").asText(null);
                        if (text != null && !text.isEmpty()) {
                            String[] parts = text.split(" - ", 2);
                            String title = parts.length > 1 ? parts[0] : "";
                            String snippet = parts.length > 1 ? parts[1] : text;
                            snippets.add(SearchSnippet.builder()
                                    .title(title)
                                    .url(firstUrl)
                                    .snippet(snippet)
                                    .build());
                        }
                    }
                }
            } else {
                log.warn("DuckDuckGo API returned status {}: {}", response.statusCode(), response.body());
                return WebSearchResult.builder()
                        .query(query)
                        .snippets(List.of())
                        .timestamp(System.currentTimeMillis())
                        .status("error")
                        .errorMessage("搜索服务返回异常状态: " + response.statusCode())
                        .build();
            }
        } catch (Exception e) {
            log.warn("Web search failed for query '{}': {}", query, e.getMessage());
            return WebSearchResult.builder()
                    .query(query)
                    .snippets(List.of())
                    .timestamp(System.currentTimeMillis())
                    .status("error")
                    .errorMessage("搜索失败: " + e.getMessage())
                    .build();
        }

        if (snippets.isEmpty()) {
            log.info("No search results found for query '{}'", query);
            return WebSearchResult.builder()
                    .query(query)
                    .snippets(List.of())
                    .timestamp(System.currentTimeMillis())
                    .status("no_results")
                    .build();
        }

        log.info("Web search for '{}' returned {} results", query, snippets.size());
        return WebSearchResult.builder()
                .query(query)
                .snippets(snippets)
                .timestamp(System.currentTimeMillis())
                .status("success")
                .build();
    }
}
