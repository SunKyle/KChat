package com.example.app.pipeline.stage.postprocess;

import com.example.app.config.CogneeProperties;
import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.service.CogneeClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.Map;

/**
 * Pipeline stage that indexes conversations into the Cognee knowledge graph
 * after the LLM has responded.
 *
 * <p>This stage runs in the POSTPROCESS phase, after the AI response is complete.
 * It sends the conversation pair (user message + AI response) to cognee for
 * automatic entity extraction, relationship mapping, and vector embedding.
 *
 * <p>If cognee is disabled or unreachable, this stage degrades gracefully
 * (non-critical stage) without affecting the chat pipeline.
 *
 * <h3>Data Flow</h3>
 * <pre>
 * User Message + AI Response → cognee.add() → Knowledge Graph Indexing
 * </pre>
 *
 * <h3>Stage Order</h3>
 * Runs at order 725, immediately after MemoryExtractionStage (720),
 * so both local memory extraction and cognee indexing happen together.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CogneeMemoryIndexStage implements ContextPipelineStage {

    private final CogneeClient cogneeClient;
    private final CogneeProperties cogneeProperties;

    @Override
    public Phase getPhase() {
        return Phase.POSTPROCESS;
    }

    @Override
    public String getName() {
        return "cogneeMemoryIndexStage";
    }

    @Override
    public int getOrder() {
        return 725;
    }

    @Override
    public boolean isCritical() {
        return false;
    }

    @Override
    public boolean isApplicable(ConversationContext ctx) {
        // Only run if cognee integration is enabled globally
        if (!cogneeProperties.isEnabled() || !cogneeProperties.getIndex().isEnabled()) {
            return false;
        }
        // Only index if we have both a user message and an AI response
        return ctx.getUserMessage() != null && !ctx.getUserMessage().isBlank()
                && ctx.getLlmResponse() != null && !ctx.getLlmResponse().isBlank();
    }

    @Override
    public void execute(ConversationContext ctx) {
        String conversationUserMessage = ctx.getUserMessage();
        String aiResponse = ctx.getLlmResponse();

        String conversationContent = String.format(
                "User: %s\n\nAssistant: %s",
                conversationUserMessage,
                aiResponse != null ? aiResponse : ""
        );

        Map<String, Object> metadata = Map.of(
                "conversationId", ctx.getConversationId() != null ? ctx.getConversationId() : "",
                "userId", ctx.getUserId() != null ? ctx.getUserId() : "default",
                "type", "conversation",
                "source", "kchat"
        );

        // Run cognee indexing asynchronously — LLM-based entity extraction is slow
        // and should not block the chat pipeline response.
        final String content = conversationContent;
        CompletableFuture.runAsync(() -> {
            try {
                boolean indexed = cogneeClient.add(content, metadata);
                if (indexed) {
                    log.info("[CogneeMemoryIndex] Indexed conversation {} ({} chars)",
                            ctx.getConversationId(), content.length());
                } else {
                    log.warn("[CogneeMemoryIndex] Failed to index conversation {}",
                            ctx.getConversationId());
                }
            } catch (Exception e) {
                log.warn("[CogneeMemoryIndex] Async indexing failed: {}", e.getMessage());
            }
        });
    }
}
