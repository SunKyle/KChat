package com.example.app.pipeline.stage.agent;

import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 工具结果回填阶段
 *
 * 将本轮的工具调用与结果追加到 ctx.assembledMessages，使下一轮 LLM 调用能看到完整的
 * 工具调用上下文。具体操作：
 * <ol>
 *   <li>从 agentState 读取本轮 AiMessage（含 toolExecutionRequests），追加到 assembledMessages</li>
 *   <li>为本轮每个 ToolCallRecord 匹配对应的 ToolResultRecord，生成
 *       {@link ToolExecutionResultMessage} 追加到 assembledMessages</li>
 * </ol>
 *
 * 仅在 ctx.toolCalls 非空时执行（即 LLM 发起了工具调用）。
 * 若 LLM 未发起工具调用（最终文本回复），本阶段为 no-op，回复内容已由
 * ModelRoutingStage 写入 ctx.llmResponse。
 *
 * order=660（AGENT 阶段，在 toolInvocationStage(650) 之后）
 */
@Component
@Slf4j
public class ToolResultAssemblyStage implements ContextPipelineStage {

    @Override
    public Phase getPhase() {
        return Phase.AGENT;
    }

    @Override
    public String getName() {
        return "toolResultAssemblyStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        if (ctx.getToolCalls().isEmpty()) {
            return;
        }

        Object raw = ctx.getAgentState().get(ConversationContext.KEY_LAST_AI_MESSAGE);
        if (!(raw instanceof AiMessage aiMessage)) {
            log.warn("[ToolResultAssembly] AiMessage missing in agentState, cannot assemble results");
            return;
        }

        List<ChatMessage> messages = ctx.getAssembledMessages() != null
                ? ctx.getAssembledMessages()
                : new ArrayList<>();

        // 1. 追加 AiMessage（含 toolExecutionRequests），让 LLM 看到自己的工具调用请求
        messages.add(aiMessage);

        // 2. 为每个 toolCall 匹配 result，追加 ToolExecutionResultMessage
        for (ConversationContext.ToolCallRecord call : ctx.getToolCalls()) {
            String resultText = findResultText(ctx, call.toolCallId());
            ToolExecutionResultMessage resultMsg = ToolExecutionResultMessage.from(
                    call.toolCallId(), call.toolName(), resultText);
            messages.add(resultMsg);
        }

        ctx.setAssembledMessages(messages);
        log.info("[ToolResultAssembly] Appended AiMessage + {} ToolExecutionResultMessage(s) to assembledMessages",
                ctx.getToolCalls().size());
    }

    /** 按 toolCallId 查找执行结果文本，失败时返回错误信息。 */
    private String findResultText(ConversationContext ctx, String toolCallId) {
        return ctx.getToolResults().stream()
                .filter(r -> toolCallId.equals(r.toolCallId()))
                .findFirst()
                .map(r -> r.success()
                        ? String.valueOf(r.result())
                        : "Error: " + r.errorMessage())
                .orElse("Error: no result for toolCallId=" + toolCallId);
    }

    @Override
    public boolean isApplicable(ConversationContext ctx) {
        return ctx.isAgentMode();
    }

    @Override
    public int getOrder() {
        return 660;
    }

    @Override
    public boolean isCritical() {
        return false;
    }
}
