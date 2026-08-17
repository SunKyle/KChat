package com.example.app.pipeline.stage.postprocess;

import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Streaming-only: sends the final SSE "done" event with the AI message ID
 * and optionally the generated title, then completes the emitter.
 */
@Component
@Slf4j
public class StreamingDoneStage implements ContextPipelineStage {

    @Override
    public Phase getPhase() { return Phase.POSTPROCESS; }

    public String getName() {
        return "streamingDoneStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        String artifactsJson = ctx.getArtifacts() != null
                ? JsonUtils.toJson(ctx.getArtifacts())
                : "[]";
        String kbRefsJson = ctx.getKbReferenceNames() != null
                ? JsonUtils.toJson(ctx.getKbReferenceNames())
                : "[]";
        StringBuilder doneData = new StringBuilder(
                "{\"messageId\": \"" + ctx.getAiMessageId() + "\"");
        if (ctx.getGeneratedTitle() != null) {
            doneData.append(", \"title\": \"")
                    .append(JsonUtils.escapeJson(ctx.getGeneratedTitle()))
                    .append("\"");
        }
        doneData.append(", \"artifacts\": ").append(artifactsJson)
                .append(", \"kbReferences\": ").append(kbRefsJson).append("}");
        ctx.emitSseEvent("done", doneData.toString());

        try {
            org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter =
                    (org.springframework.web.servlet.mvc.method.annotation.SseEmitter) ctx.getSseEmitter();
            emitter.complete();
        } catch (Exception e) {
            log.warn("Failed to complete SSE emitter: {}", e.getMessage());
        }
    }

    @Override
    public boolean isApplicable(ConversationContext ctx) {
        return ctx.isStreaming();
    }

    @Override
    public int getOrder() {
        return 850;
    }

    @Override
    public boolean isCritical() {
        return false;
    }
}
