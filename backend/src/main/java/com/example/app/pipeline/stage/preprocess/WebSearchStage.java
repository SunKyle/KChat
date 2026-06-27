package com.example.app.pipeline.stage.preprocess;

import com.example.app.config.WebSearchConfig;
import com.example.app.dto.WebSearchResult;
import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.service.WebSearchService;
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

    @Override
    public String getName() {
        return "webSearchStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        try {
            WebSearchResult result = webSearchService.search(ctx.getUserMessage());
            ctx.setRawSearchResult(result);

            if (result.getSnippets() != null && !result.getSnippets().isEmpty()) {
                String searchContext = result.getSnippets().stream()
                        .map(s -> "- [" + s.getTitle() + "](" + s.getUrl() + "): " + s.getSnippet())
                        .collect(Collectors.joining("\n"));
                ctx.setSearchContext(searchContext);
            }
        } catch (Exception e) {
            log.warn("Web search failed: {}", e.getMessage());
            ctx.setSearchContext(null);
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
