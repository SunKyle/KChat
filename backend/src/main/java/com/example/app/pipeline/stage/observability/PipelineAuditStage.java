package com.example.app.pipeline.stage.observability;

import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Verification stage that logs a summary of pipeline execution.
 *
 * Non-critical — failure here does not affect the chat response.
 * In Phase 1, this stage simply proves the pipeline framework is operational.
 * In Phase 2+, it will log detailed per-stage timing and token metrics.
 */
@Component
@Slf4j
public class PipelineAuditStage implements ContextPipelineStage {

    @Override
    public Phase getPhase() { return Phase.OBSERVABILITY; }

    public String getName() {
        return "pipelineAuditStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        long totalMs = System.currentTimeMillis() - ctx.getPipelineStartTime();

        log.debug("[PipelineAudit] type={}, conversation={}, stages={}, totalTime={}ms",
                ctx.getPipelineType(),
                ctx.getConversationId(),
                ctx.getExecutedStageNames(),
                totalMs);

        // Log per-stage timings at trace level for detailed debugging
        if (log.isTraceEnabled() && !ctx.getStageTimings().isEmpty()) {
            StringBuilder sb = new StringBuilder("[PipelineAudit] Stage timings:\n");
            ctx.getStageTimings().forEach((name, ms) ->
                    sb.append(String.format("  %-40s %5dms%n", name, ms)));
            sb.append(String.format("  %-40s %5dms (total)%n", "TOTAL", totalMs));
            log.trace(sb.toString());
        }
    }

    @Override
    public int getOrder() {
        return 999;
    }

    @Override
    public boolean isCritical() {
        return false;
    }
}
