package com.example.app.pipeline.stage.postprocess;

import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.service.ShortTermMemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ShortTermMemoryUpdateStage implements ContextPipelineStage {

    private final ShortTermMemoryService shortTermMemoryService;

    @Override
    public Phase getPhase() { return Phase.POSTPROCESS; }

    public String getName() {
        return "shortTermMemoryUpdateStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        if (ctx.getLlmResponse() == null) return;
        if (ctx.isUserMessageInMemory()) {
            // Streaming: user already in memory (added pre-LLM), only add AI
            shortTermMemoryService.updateMemoryWithAiMessage(
                    ctx.getConversationId(), ctx.getLlmResponse());
        } else {
            // Sync: add both user + AI so the NEXT request has full history
            shortTermMemoryService.updateMemory(
                    ctx.getConversationId(), ctx.getUserMessage(), ctx.getLlmResponse());
        }
    }

    @Override
    public int getOrder() {
        return 700;
    }

    @Override
    public boolean isCritical() {
        return false;
    }
}
