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
     * Re-entrant execution for agent tool-calling loops.
     *
     * 流程：
     * 1. 第一轮运行 PREPROCESS + ASSEMBLY + EXECUTION + AGENT（初始化上下文 + 首次 LLM 调用 + 工具检测/执行）
     * 2. 后续轮次只运行 EXECUTION + AGENT（基于 tool 结果再次调用 LLM）
     *
     * 注意：本方法不运行 POSTPROCESS + OBSERVABILITY。
     * 调用方需在推送最终响应后显式调用 {@link #executePostProcessing}，
     * 以确保 StreamingDoneStage 在 message 事件之后发送 done 事件。
     *
     * 终止条件：toolCalls 为空（LLM 不再调用工具）/ 达到 maxIterations / 出现不可恢复错误。
     */
    public void executeWithAgentLoop(ConversationContext ctx) {
        ctx.setCurrentIteration(0);

        // 第一轮：PREPROCESS + ASSEMBLY + EXECUTION + AGENT
        runStages(resolveStagesUpTo(ctx, ContextPipelineStage.Phase.AGENT), ctx);

        // 后续轮次：EXECUTION + AGENT，直到无 tool_calls 或达 maxIterations
        while (!ctx.getToolCalls().isEmpty()
                && ctx.getCurrentIteration() + 1 < ctx.getMaxAgentIterations()) {
            int next = ctx.getCurrentIteration() + 1;
            ctx.setCurrentIteration(next);
            log.info("[AgentLoop] Iteration {}: {} tool call(s), continuing",
                    next, ctx.getToolCalls().size());
            // 清空上一轮 toolCalls，让 ToolCallDetectionStage 重新检测
            ctx.getToolCalls().clear();
            runStages(resolveStagesBetween(ctx,
                    ContextPipelineStage.Phase.EXECUTION,
                    ContextPipelineStage.Phase.AGENT), ctx);
        }

        if (!ctx.getToolCalls().isEmpty()) {
            log.warn("[AgentLoop] Reached max iterations ({}) with {} pending tool calls",
                    ctx.getMaxAgentIterations(), ctx.getToolCalls().size());
        }
    }

    /** 解析从开始到指定 phase（含）的所有 stage */
    private List<ContextPipelineStage> resolveStagesUpTo(ConversationContext ctx,
                                                          ContextPipelineStage.Phase upper) {
        return resolveStages(ctx).stream()
                .filter(s -> s.getPhase().ordinal() <= upper.ordinal())
                .toList();
    }

    /** 解析从 from（含）到 to（含）之间的 stage */
    private List<ContextPipelineStage> resolveStagesBetween(ConversationContext ctx,
                                                            ContextPipelineStage.Phase from,
                                                            ContextPipelineStage.Phase to) {
        return resolveStages(ctx).stream()
                .filter(s -> s.getPhase().ordinal() >= from.ordinal()
                        && s.getPhase().ordinal() <= to.ordinal())
                .toList();
    }

    /** 解析从指定 phase（含）到结尾的所有 stage */
    private List<ContextPipelineStage> resolveStagesFrom(ConversationContext ctx,
                                                         ContextPipelineStage.Phase from) {
        return resolveStages(ctx).stream()
                .filter(s -> s.getPhase().ordinal() >= from.ordinal())
                .toList();
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
