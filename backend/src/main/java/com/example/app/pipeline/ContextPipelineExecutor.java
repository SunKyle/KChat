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
     *
     * <p>典型调用方：
     * <ul>
     *   <li>旧单 Skill 激活链路（/ 手动选 Skill 或 关键词命中单 Skill）：
     *       SkillResolutionStage 已写 KEY_ACTIVE_SKILL，本循环在单帧上跑原子 Tool ReAct</li>
     *   <li>SkillExecutor 嵌套 SPECIALIST 帧内部：同样在单 Skill 帧上跑原子 Tool ReAct</li>
     * </ul>
     */
    public void executeWithAgentLoop(ConversationContext ctx) {
        // 顶层入口：先跑一次性 PREPROCESS（含 user message 预持久化等），
        // 再跑 ReAct 循环。SPECIALIST 帧不应调此方法（用 executeSpecialistLoop）。
        runStages(resolveStagesUpTo(ctx, ContextPipelineStage.Phase.PREPROCESS), ctx);
        runReActLoop(ctx, "AgentLoop", false);
    }

    /**
     * SPECIALIST 帧内部的 ReAct 循环（不跑 PREPROCESS）。
     *
     * <p>供 SkillExecutor 调用：PREPROCESS 已在顶层入口（executeWithOrchestratorLoop /
     * executeWithAgentLoop）跑过，SPECIALIST 只需要 ASSEMBLY + EXECUTION + AGENT 循环。
     * 这样避免 user message 被重复预持久化等问题。
     */
    public void executeSpecialistLoop(ConversationContext ctx) {
        runReActLoop(ctx, "SpecialistLoop", false);
    }

    /**
     * 顶层 Orchestrator 循环 —— 双层 ReAct 的上层。
     *
     * <p>与 {@link #executeWithAgentLoop} 结构一致，但语义不同：
     * 当前活跃帧应为 ORCHESTRATOR 帧，且 KEY_ACTIVE_SKILL 未设置
     * （即用户没有通过 / 手动指定某个 Skill）。这种情况下：
     * <ul>
     *   <li>SystemPromptAssemblyStage 走 OrchestratorSystemPromptProvider 编排 Prompt</li>
     *   <li>ToolDefinitionStage 只暴露 call_skill_* 伪 functions（不暴露原子 Tool）</li>
     *   <li>ToolInvocationStage 分发 Skill 调用到 SkillExecutor → push SPECIALIST 帧
     *       → 内部递归调用 executeWithAgentLoop 跑原子 Tool ReAct → pop → 结果回填</li>
     * </ul>
     *
     * <p>循环终止：Orchestrator LLM 不再 call_skill_*（即给出最终汇总文本），
     * 或达到 Orchestrator 分派轮次上限（默认 10，见 AgentStack 构造函数）。
     *
     * <p>调用后同样需要显式 executePostProcessing。
     */
    public void executeWithOrchestratorLoop(ConversationContext ctx) {
        // 顶层入口：先跑一次性 PREPROCESS（含 user message 预持久化等），
        // 再跑 Orchestrator ReAct 循环。
        runStages(resolveStagesUpTo(ctx, ContextPipelineStage.Phase.PREPROCESS), ctx);
        runReActLoop(ctx, "OrchestratorLoop", true);
    }

    /**
     * 共享的 ReAct 循环骨架。
     *
     * @param ctx          上下文
     * @param logPrefix    日志前缀（用于区分 AgentLoop / OrchestratorLoop）
     * @param orchestrator 是否是顶层 Orchestrator 循环（仅影响 trace 和日志）
     */
    private void runReActLoop(ConversationContext ctx, String logPrefix, boolean orchestrator) {
        ctx.setCurrentIteration(0);
        int startDepth = ctx.getAgentStack().depth();
        log.info("[{}] Start (role={}, frameId={}, maxIt={}, startDepth={})",
                logPrefix,
                ctx.getAgentStack().peek().getRole(),
                ctx.getAgentStack().currentFrameId(),
                ctx.getMaxAgentIterations(),
                startDepth);

        // 第一轮：ASSEMBLY + EXECUTION + AGENT
        // （PREPROCESS 已在顶层入口 executeWithOrchestratorLoop/executeWithAgentLoop
        //   或 executeSpecialistLoop 之外完成，不在循环内重复跑）
        runStages(resolveStagesBetween(ctx,
                ContextPipelineStage.Phase.ASSEMBLY,
                ContextPipelineStage.Phase.AGENT), ctx);

        // 后续轮次：EXECUTION + AGENT
        // 终止条件：
        //   1. toolCalls 为空（LLM 给出最终回复）
        //   2. 达 maxIterations
        //   3. ctx.clientCancelled（SSE 断连，节省 token 不再继续）
        while (!ctx.getToolCalls().isEmpty()
                && ctx.getCurrentIteration() + 1 < ctx.getMaxAgentIterations()
                && !ctx.isClientCancelled()) {
            int next = ctx.getCurrentIteration() + 1;
            ctx.setCurrentIteration(next);
            log.info("[{}] Iteration {}: {} pending call(s), depth={}, continuing",
                    logPrefix, next, ctx.getToolCalls().size(), ctx.getAgentStack().depth());
            ctx.getTrace().addAgentIteration(next, 0, null, "CONTINUE", ctx.getToolCalls().size());
            ctx.getToolCalls().clear();
            runStages(resolveStagesBetween(ctx,
                    ContextPipelineStage.Phase.EXECUTION,
                    ContextPipelineStage.Phase.AGENT), ctx);
        }

        if (ctx.isClientCancelled()) {
            log.warn("[{}] Aborted: client disconnected, skipping remaining iterations",
                    logPrefix);
            ctx.getTrace().addAgentIteration(ctx.getCurrentIteration(), 0,
                    "client disconnected", "ABORT", ctx.getToolCalls().size());
        } else if (!ctx.getToolCalls().isEmpty()) {
            log.warn("[{}] Reached max iterations ({}), {} pending call(s) remain unfinished",
                    logPrefix, ctx.getMaxAgentIterations(), ctx.getToolCalls().size());
            ctx.getTrace().addAgentIteration(ctx.getCurrentIteration(), 0,
                    "max iterations reached", "TERMINATE", ctx.getToolCalls().size());
        } else {
            log.info("[{}] Ended after {} iteration(s), final depth={}",
                    logPrefix, ctx.getCurrentIteration(), ctx.getAgentStack().depth());
            ctx.getTrace().addAgentIteration(ctx.getCurrentIteration(), 0,
                    orchestrator ? "orchestrator final response" : "no tool calls",
                    "TERMINATE", 0);
        }

        // 防御性检查：Orchestrator 循环退出后栈深度应回到 1
        // 若仍有 SPECIALIST 帧残留（说明 SkillExecutor 异常路径未 pop 干净），
        // 强制 pop 到 depth=1，避免后续 POSTPROCESS 在错误的帧上跑导致数据污染
        if (orchestrator) {
            int finalDepth = ctx.getAgentStack().depth();
            if (finalDepth != 1) {
                log.error("[{}] Stack leak: ended at depth={}, expected 1. Force-popping to depth=1",
                        logPrefix, finalDepth);
                while (ctx.getAgentStack().depth() > 1) {
                    ctx.getAgentStack().popFrame();
                }
                ctx.getTrace().addAgentIteration(ctx.getCurrentIteration(), 0,
                        "stack leak detected, force-popped to depth=1", "RECOVER", 0);
            }
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
                long duration = System.currentTimeMillis() - t0;
                ctx.recordStage(stage.getName(), duration);
                ctx.getTrace().addStage(
                        stage.getName(), stage.getOrder(),
                        stage.getPhase().name(), duration,
                        "SUCCESS", null);
            } catch (Throwable e) {
                long duration = System.currentTimeMillis() - t0;
                ctx.addError(stage.getName(), e.getMessage(), e, !stage.isCritical());
                ctx.recordStage(stage.getName(), duration);
                ctx.getTrace().addStage(
                        stage.getName(), stage.getOrder(),
                        stage.getPhase().name(), duration,
                        "FAILED", e.getMessage());

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
