package com.example.app.service.tool.tools;

import com.example.app.dto.WebSearchResult;
import com.example.app.dto.WebSearchResult.SearchSnippet;
import com.example.app.service.WebSearchService;
import com.example.app.service.tool.ToolComponent;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 联网搜索工具
 *
 * 暴露 {@code webSearch} 工具，供 LLM 在 Agent 模式下按需查询网络信息。
 * 复用 {@link WebSearchService} 的 Bing API / HTML 抓取实现，返回结构化摘要供
 * 模型继续推理。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSearchTool implements ToolComponent {

    private final WebSearchService webSearchService;

    @Tool("搜索互联网以获取最新信息。输入搜索关键词，返回相关网页标题、URL 和摘要列表。当用户询问你不知道的实时信息或最新事件时调用此工具。")
    String webSearch(String query) {
        log.info("[WebSearchTool] query='{}'", query);
        WebSearchResult result = webSearchService.search(query);

        if (result == null) {
            return "搜索失败：未返回结果。";
        }

        String status = result.getStatus();
        if ("error".equals(status)) {
            return "搜索失败：" + (result.getErrorMessage() != null ? result.getErrorMessage() : "未知错误");
        }
        if ("disabled".equals(status)) {
            return "搜索功能未启用。";
        }

        List<SearchSnippet> snippets = result.getSnippets();
        if (snippets == null || snippets.isEmpty()) {
            return "未找到与「" + query + "」相关的搜索结果。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("搜索关键词：").append(result.getQuery()).append("\n");
        sb.append("找到 ").append(snippets.size()).append(" 条结果：\n\n");
        for (int i = 0; i < snippets.size(); i++) {
            SearchSnippet s = snippets.get(i);
            sb.append(i + 1).append(". ").append(s.getTitle()).append("\n");
            sb.append("   URL: ").append(s.getUrl()).append("\n");
            if (s.getSnippet() != null && !s.getSnippet().isBlank()) {
                sb.append("   摘要: ").append(s.getSnippet()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
