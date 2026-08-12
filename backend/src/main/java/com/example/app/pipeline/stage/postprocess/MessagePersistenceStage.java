package com.example.app.pipeline.stage.postprocess;

import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.service.MessagePersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class MessagePersistenceStage implements ContextPipelineStage {

    private final MessagePersistenceService messagePersistenceService;

    @Override
    public Phase getPhase() { return Phase.POSTPROCESS; }

    public String getName() {
        return "messagePersistenceStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        if (ctx.getLlmResponse() == null) return;
        // Generate messageId if not already set (e.g., streaming path where
        // MessagePrePersistenceStage only saved the user message)
        String messageId = ctx.getAiMessageId();
        if (messageId == null || messageId.isBlank()) {
            messageId = UUID.randomUUID().toString();
        }
        if (ctx.isUserMessagePersisted()) {
            // Streaming: user message already saved pre-LLM, only save AI response
            String aiMessageId = messagePersistenceService.saveAiMessage(
                    ctx.getConversationId(), messageId,
                    ctx.getLlmResponse(), ctx.getArtifacts(),
                    ctx.getAgentThinkingSteps());
            ctx.setAiMessageId(aiMessageId);
        } else {
            // Sync: save AI response (user message not yet persisted)
            String aiMessageId = messagePersistenceService.saveAiMessage(
                    ctx.getConversationId(), messageId,
                    ctx.getLlmResponse(), ctx.getArtifacts(),
                    ctx.getAgentThinkingSteps());
            ctx.setAiMessageId(aiMessageId);
        }
    }

    @Override
    public int getOrder() {
        return 710;
    }
}
