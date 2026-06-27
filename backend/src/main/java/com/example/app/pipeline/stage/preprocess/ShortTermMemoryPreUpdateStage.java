package com.example.app.pipeline.stage.preprocess;

import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.service.ShortTermMemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Streaming-only: saves the user message to short-term memory BEFORE the LLM call.
 * This is part of the streaming two-phase persistence pattern.
 *
 * In sync chat, the user+AI are saved together by {@code ShortTermMemoryUpdateStage} (order 700).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ShortTermMemoryPreUpdateStage implements ContextPipelineStage {

    private final ShortTermMemoryService shortTermMemoryService;

    @Override
    public String getName() {
        return "shortTermMemoryPreUpdateStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        shortTermMemoryService.updateMemoryWithUserMessage(
                ctx.getConversationId(), ctx.getUserMessage());
        ctx.setUserMessageInMemory(true);
    }

    @Override
    public boolean isApplicable(ConversationContext ctx) {
        return ctx.isStreaming();
    }

    @Override
    public int getOrder() {
        return 250;
    }

    @Override
    public boolean isCritical() {
        return false;
    }
}
