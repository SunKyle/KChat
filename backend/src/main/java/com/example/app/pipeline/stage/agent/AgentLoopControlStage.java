package com.example.app.pipeline.stage.agent;

import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Agent 循环控制阶段
 *
 * MVP 阶段为日志记录用途：实际的循环终止逻辑由
 * {@link com.example.app.pipeline.ContextPipelineExecutor#executeWithAgentLoop}
 * 在每轮 execute 结束后检查 ctx.toolCalls 是否为空来决定。
 *
 * 此处仅记录当前迭代状态，便于排查 Agent 循环问题。后续可在此实现更复杂的
 * 终止策略（如：检测循环死锁、token 预算耗尽、目标达成判定等）。
 *
 * order=680（AGENT 阶段，在 toolResultAssemblyStage(660) 之后）
 */
@Component
@Slf4j
public class AgentLoopControlStage implements ContextPipelineStage {

    @Override
    public Phase getPhase() {
        return Phase.AGENT;
    }

    @Override
    public String getName() {
        return "agentLoopControlStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        int iteration = ctx.getCurrentIteration();
        int pending = ctx.getToolCalls().size();
        int maxIter = ctx.getMaxAgentIterations();
        if (pending > 0) {
            log.info("[AgentLoopControl] Iteration {}/{}: {} pending tool call(s), will continue",
                    iteration + 1, maxIter, pending);
        } else {
            log.info("[AgentLoopControl] Iteration {}/{}: no tool calls, loop will terminate",
                    iteration + 1, maxIter);
        }
    }

    @Override
    public boolean isApplicable(ConversationContext ctx) {
        return ctx.isAgentMode();
    }

    @Override
    public int getOrder() {
        return 680;
    }

    @Override
    public boolean isCritical() {
        return false;
    }
}
