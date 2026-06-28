package com.example.app.pipeline.stage.preprocess;

import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.service.MessagePersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Streaming-only: persists the user message to DB BEFORE the LLM call.
 * This is part of the streaming two-phase persistence pattern.
 *
 * In sync chat, both user+AI messages are saved together by
 * {@code MessagePersistenceStage} (order 710).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MessagePrePersistenceStage implements ContextPipelineStage {

    private final MessagePersistenceService messagePersistenceService;

    @Override
    public Phase getPhase() { return Phase.PREPROCESS; }

    public String getName() {
        return "messagePrePersistenceStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        messagePersistenceService.saveUserMessage(
                ctx.getConversationId(), ctx.getUserMessage(), ctx.getImageUrls());
        ctx.setUserMessagePersisted(true);
    }

    @Override
    public boolean isApplicable(ConversationContext ctx) {
        return ctx.isStreaming();
    }

    @Override
    public int getOrder() {
        return 260;
    }

    @Override
    public boolean isCritical() {
        return false;
    }
}
