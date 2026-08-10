package com.example.app.pipeline.stage.agent;

import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.service.tool.ToolExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Agent 工具调用执行阶段
 *
 * 遍历 ctx.toolCalls，通过 {@link ToolExecutor} 逐一执行，
 * 将结果（{@link ConversationContext.ToolResultRecord}）追加到 ctx.toolResults。
 *
 * 单个工具执行失败不会中断整体流程：失败结果以 success=false 标记，
 * 后续 LLM 轮次可据此决定是否重试或换一种方式回答。
 *
 * order=650（AGENT 阶段，在 toolCallDetectionStage(610) 之后）
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ToolInvocationStage implements ContextPipelineStage {

    private final ToolExecutor toolExecutor;

    @Override
    public Phase getPhase() {
        return Phase.AGENT;
    }

    @Override
    public String getName() {
        return "toolInvocationStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        if (ctx.getToolCalls().isEmpty()) {
            return;
        }

        // 保留上一轮的 toolResults 历史，仅追加本轮结果
        for (ConversationContext.ToolCallRecord call : ctx.getToolCalls()) {
            ConversationContext.ToolResultRecord result = toolExecutor.execute(call);
            ctx.getToolResults().add(result);
            if (!result.success()) {
                log.warn("[ToolInvocation] Tool '{}' failed: {}", call.toolName(), result.errorMessage());
            }
        }
        log.info("[ToolInvocation] Executed {} tool call(s), {} success, {} failed",
                ctx.getToolCalls().size(),
                ctx.getToolResults().stream().filter(ConversationContext.ToolResultRecord::success).count(),
                ctx.getToolResults().stream().filter(r -> !r.success()).count());
    }

    @Override
    public boolean isApplicable(ConversationContext ctx) {
        return ctx.isAgentMode();
    }

    @Override
    public int getOrder() {
        return 650;
    }

    @Override
    public boolean isCritical() {
        return false;
    }
}
