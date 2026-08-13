package com.example.app.pipeline.stage.postprocess;

import com.example.app.dto.MemoryDTO;
import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.service.AutoMemoryExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class MemoryExtractionStage implements ContextPipelineStage {

    private final AutoMemoryExtractor autoMemoryExtractor;

    @Override
    public Phase getPhase() { return Phase.POSTPROCESS; }

    public String getName() {
        return "memoryExtractionStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        List<MemoryDTO> extracted = autoMemoryExtractor.tryExtractDtos(
                ctx.getConversationId(), ctx.getUserId(), ctx.getModel());
        ctx.setNewlyExtractedMemories(extracted);
        log.debug("Memory extraction: {} new memories for conversation {}",
                extracted.size(), ctx.getConversationId());
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
