package com.example.app.pipeline;

import com.example.app.pipeline.context.ConversationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Executes pipeline stages sequentially against a {@link ConversationContext}.
 *
 * Supports two execution modes:
 * <ul>
 *   <li>{@link #execute(ConversationContext)} — standard sequential pipeline</li>
 *   <li>{@link #executeWithAgentLoop(ConversationContext)} — re-entrant execution
 *       for agent tool-calling cycles (Phase 3)</li>
 * </ul>
 *
 * Stage ordering is determined by {@link ContextPipelineStage#getOrder()}.
 * Stages can be conditionally skipped via {@link ContextPipelineStage#isApplicable}.
 * Non-critical stage failures are logged but do not halt the pipeline.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContextPipelineExecutor {

    private final StageRegistry stageRegistry;

    /**
     * Execute the pipeline once from start to finish.
     *
     * Stages are resolved, filtered by applicability, sorted by order,
     * and executed sequentially. On critical failure, the pipeline halts
     * and remaining stages are skipped.
     */
    public void execute(ConversationContext ctx) {
        List<ContextPipelineStage> stages = resolveStages(ctx);

        for (ContextPipelineStage stage : stages) {
            long t0 = System.currentTimeMillis();
            try {
                stage.execute(ctx);
                ctx.recordStage(stage.getName(), System.currentTimeMillis() - t0);
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - t0;
                ctx.addError(stage.getName(), e.getMessage(), e, !stage.isCritical());
                ctx.recordStage(stage.getName(), duration);

                if (stage.isCritical()) {
                    log.error("[Pipeline] Critical stage '{}' failed ({}ms), halting: {}",
                            stage.getName(), duration, e.getMessage());
                    return;
                }
                log.warn("[Pipeline] Non-critical stage '{}' failed ({}ms), continuing: {}",
                        stage.getName(), duration, e.getMessage());
            }
        }
    }

    /**
     * Re-entrant execution for agent tool-calling loops (Phase 3).
     *
     * Loop: execute pipeline → check for tool calls → execute tools
     * → inject results → re-execute pipeline → repeat until done or max iterations.
     */
    public void executeWithAgentLoop(ConversationContext ctx) {
        ctx.setCurrentIteration(0);

        while (ctx.getCurrentIteration() < ctx.getMaxAgentIterations()) {
            execute(ctx);
            ctx.setCurrentIteration(ctx.getCurrentIteration() + 1);

            if (ctx.getToolCalls().isEmpty()) {
                break;
            }

            log.info("[AgentLoop] Iteration {}: {} tool call(s), continuing",
                    ctx.getCurrentIteration(), ctx.getToolCalls().size());
        }

        if (ctx.getCurrentIteration() >= ctx.getMaxAgentIterations()
                && !ctx.getToolCalls().isEmpty()) {
            log.warn("[AgentLoop] Reached max iterations ({}) with {} pending tool calls",
                    ctx.getMaxAgentIterations(), ctx.getToolCalls().size());
        }
    }

    private List<ContextPipelineStage> resolveStages(ConversationContext ctx) {
        return stageRegistry.getAllStages().stream()
                .filter(s -> s.isApplicable(ctx))
                .sorted(Comparator.comparingInt(ContextPipelineStage::getOrder))
                .toList();
    }
}
