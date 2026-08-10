package com.example.app.pipeline.stage.agent;

import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Agent 工具调用检测阶段
 *
 * 从 ctx.agentState 读取 ModelRoutingStage 存储的 {@link AiMessage}，
 * 提取其中的 {@link ToolExecutionRequest} 列表，转换为
 * {@link ConversationContext.ToolCallRecord} 写入 ctx.toolCalls。
 *
 * 若 AiMessage 无工具调用请求，ctx.toolCalls 保持为空，
 * {@link ContextPipelineExecutor#executeWithAgentLoop} 将据此退出循环。
 *
 * order=610（AGENT 阶段，在 modelRoutingStage(500) 之后）
 */
@Component
@Slf4j
public class ToolCallDetectionStage implements ContextPipelineStage {

    @Override
    public Phase getPhase() {
        return Phase.AGENT;
    }

    @Override
    public String getName() {
        return "toolCallDetectionStage";
    }

    @Override
    @SuppressWarnings("unchecked")
    public void execute(ConversationContext ctx) {
        Object raw = ctx.getAgentState().get(ConversationContext.KEY_LAST_AI_MESSAGE);
        if (!(raw instanceof AiMessage aiMessage)) {
            log.debug("[ToolCallDetection] No AiMessage in agentState, skipping");
            return;
        }

        ctx.getToolCalls().clear();
        if (!aiMessage.hasToolExecutionRequests()) {
            log.debug("[ToolCallDetection] AiMessage has no tool execution requests");
            return;
        }

        List<ToolExecutionRequest> requests = aiMessage.toolExecutionRequests();
        for (ToolExecutionRequest req : requests) {
            ConversationContext.ToolCallRecord record = new ConversationContext.ToolCallRecord(
                    req.name(),
                    req.arguments() != null ? req.arguments() : "{}",
                    req.id() != null ? req.id() : req.name());
            ctx.getToolCalls().add(record);
        }
        log.info("[ToolCallDetection] Detected {} tool call(s): {}",
                ctx.getToolCalls().size(),
                ctx.getToolCalls().stream().map(ConversationContext.ToolCallRecord::toolName).toList());
    }

    @Override
    public boolean isApplicable(ConversationContext ctx) {
        return ctx.isAgentMode();
    }

    @Override
    public int getOrder() {
        return 610;
    }

    @Override
    public boolean isCritical() {
        return false;
    }
}
