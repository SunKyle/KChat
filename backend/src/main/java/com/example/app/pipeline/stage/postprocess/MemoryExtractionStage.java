package com.example.app.pipeline.stage.postprocess;

import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.service.AutoMemoryExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MemoryExtractionStage implements ContextPipelineStage {

    private final AutoMemoryExtractor autoMemoryExtractor;

    @Override
    public String getName() {
        return "memoryExtractionStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        int extracted = autoMemoryExtractor.tryExtract(ctx.getConversationId(), ctx.getUserId());
        log.debug("Memory extraction: {} new memories for conversation {}",
                extracted, ctx.getConversationId());
    }

    @Override
    public int getOrder() {
        return 720;
    }

    @Override
    public boolean isCritical() {
        return false;
    }
}
