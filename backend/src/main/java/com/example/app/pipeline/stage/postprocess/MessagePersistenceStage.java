package com.example.app.pipeline.stage.postprocess;

import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.service.MessagePersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MessagePersistenceStage implements ContextPipelineStage {

    private final MessagePersistenceService messagePersistenceService;

    @Override
    public String getName() {
        return "messagePersistenceStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        if (ctx.getLlmResponse() == null) return;
        String aiMessageId = messagePersistenceService.saveMessages(
                ctx.getConversationId(), ctx.getUserMessage(),
                ctx.getLlmResponse(), ctx.getImageUrls());
        ctx.setAiMessageId(aiMessageId);
    }

    @Override
    public int getOrder() {
        return 710;
    }
}
