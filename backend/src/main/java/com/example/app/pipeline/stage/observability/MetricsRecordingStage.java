package com.example.app.pipeline.stage.observability;

import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.service.PromptMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Records pipeline metrics (token count, memory count, build duration) via
 * the existing {@link PromptMetricsService}.
 *
 * In Phase 1, this stage is registered but only applicable when metrics
 * are explicitly enabled in the context. In Phase 2+, it will replace the
 * inline metrics recording in PromptAssembler.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MetricsRecordingStage implements ContextPipelineStage {

    private final PromptMetricsService metricsService;

    @Override
    public Phase getPhase() { return Phase.OBSERVABILITY; }

    public String getName() {
        return "metricsRecordingStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        if (ctx.getAssembledMessages() == null || ctx.getAssembledMessages().isEmpty()) {
            return; // nothing to measure yet (Phase 1 — assembly not pipeline-driven)
        }

        long buildDuration = System.currentTimeMillis() - ctx.getPipelineStartTime();
        int memoryCount = ctx.getLongTermMemory() != null ? ctx.getLongTermMemory().size() : 0;

        try {
            metricsService.recordMetrics(
                    ctx.getConversationId() != null ? ctx.getConversationId() : "unknown",
                    ctx.getTokenCount(),
                    memoryCount,
                    buildDuration,
                    ctx.isTruncated());
            log.debug("[Metrics] Recorded: tokens={}, memories={}, duration={}ms",
                    ctx.getTokenCount(), memoryCount, buildDuration);
        } catch (Exception e) {
            log.warn("[Metrics] Failed to record metrics: {}", e.getMessage());
        }
    }

    @Override
    public boolean isApplicable(ConversationContext ctx) {
        return ctx.getAssembledMessages() != null && !ctx.getAssembledMessages().isEmpty();
    }

    @Override
    public int getOrder() {
        return 900;
    }

    @Override
    public boolean isCritical() {
        return false;
    }
}
