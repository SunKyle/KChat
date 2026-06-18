package com.example.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSearchResult {

    private String query;
    private List<SearchSnippet> snippets;
    private long timestamp;
    private String status; // "success", "no_results", "error"
    private String errorMessage;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchSnippet {
        private String title;
        private String url;
        private String snippet;
    }
}
