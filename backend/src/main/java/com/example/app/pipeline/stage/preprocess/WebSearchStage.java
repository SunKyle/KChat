package com.example.app.pipeline.stage.preprocess;

import com.example.app.config.WebSearchConfig;
import com.example.app.dto.WebSearchResult;
import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.service.WebSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSearchStage implements ContextPipelineStage {

    private final WebSearchService webSearchService;
    private final WebSearchConfig webSearchConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Phase getPhase() { return Phase.PREPROCESS; }

    public String getName() {
        return "webSearchStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        try {
            WebSearchResult result = webSearchService.search(ctx.getUserMessage());
            ctx.setRawSearchResult(result);

            // Emit SSE search_results event for streaming clients
            String resultsJson = objectMapper.writeValueAsString(result);
            ctx.emitSseEvent("search_results", resultsJson);

            if (result.getSnippets() != null && !result.getSnippets().isEmpty()) {
                String searchContext = result.getSnippets().stream()
                        .map(s -> "- [" + s.getTitle() + "](" + s.getUrl() + "): " + s.getSnippet())
                        .collect(Collectors.joining("\n"));
                ctx.setSearchContext(searchContext);
            }
        } catch (Exception e) {
            log.warn("Web search failed: {}", e.getMessage());
            ctx.setSearchContext(null);
            // Emit error status for streaming clients
            if (ctx.isStreaming()) {
                try {
                    WebSearchResult errorResult = WebSearchResult.builder()
                            .query(ctx.getUserMessage())
                            .snippets(java.util.List.of())
                            .timestamp(System.currentTimeMillis())
                            .status("error")
                            .errorMessage(e.getMessage())
                            .build();
                    ctx.emitSseEvent("search_results", objectMapper.writeValueAsString(errorResult));
                } catch (Exception ex) {
                    log.warn("Failed to send search error SSE: {}", ex.getMessage());
                }
            }
        }
    }

    @Override
    public boolean isApplicable(ConversationContext ctx) {
        return ctx.isWebSearchEnabled() && webSearchConfig.isEnabled();
    }

    @Override
    public int getOrder() {
        return 200;
    }

    @Override
    public boolean isCritical() {
        return false;
    }
}
