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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class WebSearchServiceImpl implements WebSearchService {

    private static final Logger log = LoggerFactory.getLogger(WebSearchServiceImpl.class);
    private static final String BING_API = "https://api.bing.microsoft.com/v7.0/search";
    private static final String BING_HTML = "https://www.bing.com/search";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final WebSearchConfig config;
    private final HttpClient httpClient;

    public WebSearchServiceImpl(WebSearchConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public WebSearchResult search(String query) {
        if (!config.isEnabled()) {
            return buildResult(query, List.of(), "disabled", null);
        }

        String searchQuery = extractKeywords(query);

        // Prefer Bing API if key is configured
        if (config.getBingApiKey() != null && !config.getBingApiKey().isBlank()) {
            return searchViaBingApi(searchQuery);
        }

        // Fallback to HTML scraping
        return searchViaHtmlScraping(searchQuery);
    }

    private WebSearchResult searchViaBingApi(String query) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = BING_API + "?q=" + encodedQuery + "&count=" + config.getMaxResults()
                    + "&mkt=zh-CN&setLang=zh-cn";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                    .header("Ocp-Apim-Subscription-Key", config.getBingApiKey())
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode webPages = root.path("webPages").path("value");
                List<SearchSnippet> snippets = new ArrayList<>();

                if (webPages.isArray()) {
                    for (JsonNode page : webPages) {
                        if (snippets.size() >= config.getMaxResults()) break;
                        snippets.add(SearchSnippet.builder()
                                .title(page.path("name").asText(""))
                                .url(page.path("url").asText(""))
                                .snippet(page.path("snippet").asText(""))
                                .build());
                    }
                }

                if (snippets.isEmpty()) {
                    return buildResult(query, List.of(), "no_results", null);
                }

                log.info("Bing API search returned {} results", snippets.size());
                return buildResult(query, snippets, "success", null);
            }

            log.warn("Bing API returned {}: {}", response.statusCode(), response.body());
            return buildResult(query, List.of(), "error",
                    "Bing API 返回状态: " + response.statusCode());

        } catch (Exception e) {
            log.warn("Bing API search failed: {}", e.getMessage());
            return buildResult(query, List.of(), "error", "搜索失败: " + e.getMessage());
        }
    }

    private WebSearchResult searchViaHtmlScraping(String query) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = BING_HTML + "?q=" + encodedQuery + "&setlang=zh-cn";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String html = response.body();
                List<SearchSnippet> snippets = parseBingHtml(html);
                if (snippets.isEmpty()) {
                    return buildResult(query, List.of(), "no_results", null);
                }
                log.info("Bing HTML search returned {} results", snippets.size());
                return buildResult(query, snippets, "success", null);
            }

            // Try cn.bing.com as fallback
            log.info("Bing global returned {}, trying cn.bing.com", response.statusCode());
            url = "https://cn.bing.com/search?q=" + encodedQuery + "&setlang=zh-cn";
            request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .GET()
                    .build();

            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                List<SearchSnippet> snippets = parseBingHtml(response.body());
                if (!snippets.isEmpty()) {
                    log.info("cn.bing.com returned {} results", snippets.size());
                    return buildResult(query, snippets, "success", null);
                }
                return buildResult(query, List.of(), "no_results", null);
            }

            log.warn("cn.bing.com returned {}: {}", response.statusCode(),
                    response.body().length() > 200 ? response.body().substring(0, 200) : response.body());
            return buildResult(query, List.of(), "error",
                    "搜索不可用 (status: " + response.statusCode() + ")。请配置 bing-api-key");

        } catch (Exception e) {
            log.warn("HTML search failed: {}", e.getMessage());
            return buildResult(query, List.of(), "error",
                    "搜索不可用: " + e.getMessage() + "。请配置 bing-api-key");
        }
    }

    private String extractKeywords(String query) {
        String cleaned = query
                .replaceAll("请(帮我|你)?", "")
                .replaceAll("[，,。.！!？?]+", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (cleaned.length() > 50) {
            String[] sentences = cleaned.split("[。！？!?\\n]");
            String best = sentences[sentences.length - 1];
            for (String s : sentences) {
                if (s.length() > best.length()) best = s;
            }
            cleaned = best.length() > 10 ? best.trim() : cleaned.substring(0, Math.min(80, cleaned.length()));
        }

        return cleaned;
    }

    private List<SearchSnippet> parseBingHtml(String html) {
        List<SearchSnippet> snippets = new ArrayList<>();

        Pattern algoPattern = Pattern.compile(
                "<li[^>]*class=\"b_algo\"[^>]*>(.*?)</li>",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Pattern linkPattern = Pattern.compile(
                "<a[^>]*href=\"(https?://[^\"]+)\"[^>]*>(.+?)</a>",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

        Matcher algoMatcher = algoPattern.matcher(html);
        while (algoMatcher.find() && snippets.size() < config.getMaxResults()) {
            String block = algoMatcher.group(1);
            Matcher linkMatcher = linkPattern.matcher(block);
            if (linkMatcher.find()) {
                String url = linkMatcher.group(1);
                String title = linkMatcher.group(2).replaceAll("<[^>]*>", "").trim();
                if (url.contains("bing.com") || url.contains("microsoft.com") || url.contains("go.microsoft"))
                    continue;

                // Extract snippet from <p> or <div class="b_caption">
                String snippet = "";
                Pattern snippetP = Pattern.compile(
                        "<(?:p|div)[^>]*class=\"[^\"]*b_(?:caption|lineclamp|snippet)[^\"]*\"[^>]*>(.+?)</(?:p|div)>",
                        Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
                Matcher sm = snippetP.matcher(block);
                if (sm.find()) {
                    snippet = sm.group(1).replaceAll("<[^>]*>", "").replaceAll("\\s+", " ").trim();
                }

                snippets.add(SearchSnippet.builder()
                        .title(cleanHtml(title))
                        .url(url)
                        .snippet(cleanHtml(snippet))
                        .build());
            }
        }

        return snippets;
    }

    private String cleanHtml(String text) {
        if (text == null) return "";
        return text.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#x27;", "'").replace("&nbsp;", " ")
                .replace("&middot;", "·").replace("&mdash;", "—").trim();
    }

    private WebSearchResult buildResult(String query, List<SearchSnippet> snippets, String status, String error) {
        return WebSearchResult.builder()
                .query(query).snippets(snippets)
                .timestamp(System.currentTimeMillis()).status(status)
                .errorMessage(error).build();
    }
}
