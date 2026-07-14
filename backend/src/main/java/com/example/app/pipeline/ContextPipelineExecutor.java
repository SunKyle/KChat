package com.example.app.pipeline;

import com.example.app.pipeline.context.ConversationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContextPipelineExecutor {

    private final StageRegistry stageRegistry;

    /**
     * Execute all applicable stages from start to finish (sync chat path).
     */
    public void execute(ConversationContext ctx) {
        runStages(resolveStages(ctx), ctx);
    }

    /**
     * Streaming: run stages up through EXECUTION phase, then return.
     * POSTPROCESS and OBSERVABILITY stages run later via {@link #executePostProcessing}
     * which is invoked from within the ModelRoutingStage streaming completion callback.
     */
    public void executeStreaming(ConversationContext ctx) {
        List<ContextPipelineStage> preLlmStages = resolveStages(ctx).stream()
                .filter(s -> s.getPhase().ordinal() <= ContextPipelineStage.Phase.EXECUTION.ordinal())
                .toList();
        runStages(preLlmStages, ctx);
    }

    /**
     * Run POSTPROCESS and OBSERVABILITY stages. Called from within the streaming
     * completion callback after the LLM response is fully received.
     */
    public void executePostProcessing(ConversationContext ctx) {
        List<ContextPipelineStage> postLlmStages = resolveStages(ctx).stream()
                .filter(s -> s.getPhase().ordinal() >= ContextPipelineStage.Phase.POSTPROCESS.ordinal())
                .toList();
        runStages(postLlmStages, ctx);
    }

    /**
     * Re-entrant execution for agent tool-calling loops (Phase 3).
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

    private void runStages(List<ContextPipelineStage> stages, ConversationContext ctx) {
        for (ContextPipelineStage stage : stages) {
            long t0 = System.currentTimeMillis();
            try {
                stage.execute(ctx);
                ctx.recordStage(stage.getName(), System.currentTimeMillis() - t0);
            } catch (Throwable e) {
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

    private List<ContextPipelineStage> resolveStages(ConversationContext ctx) {
        return stageRegistry.getAllStages().stream()
                .filter(s -> s.isApplicable(ctx))
                .sorted(Comparator.comparingInt(ContextPipelineStage::getOrder))
                .toList();
    }
}
