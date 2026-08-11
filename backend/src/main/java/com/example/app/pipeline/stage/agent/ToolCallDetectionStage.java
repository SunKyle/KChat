package com.example.app.pipeline.stage.agent;

import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
@RequiredArgsConstructor
@Slf4j
public class ToolCallDetectionStage implements ContextPipelineStage {

    private final ObjectMapper objectMapper;

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

            // 推送 Agent 思考过程：检测到 LLM 发起的一次工具调用。
            // 工具尚未执行，模型未定；若 LLM 在参数里显式指定了 requestedModelId，
            // 则作为"请求模型"展示，否则留空（结果阶段会展示实际使用的模型）。
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("toolName", record.toolName());
            data.put("arguments", record.arguments());
            data.put("toolCallId", record.toolCallId());
            data.put("model", extractRequestedModel(record.arguments()));
            ctx.emitAgentThinking("tool_detection", data);
        }
        log.info("[ToolCallDetection] Detected {} tool call(s): {}",
                ctx.getToolCalls().size(),
                ctx.getToolCalls().stream().map(ConversationContext.ToolCallRecord::toolName).toList());
    }

    /** 从工具参数 JSON 中解析 requestedModelId（若存在）。 */
    private String extractRequestedModel(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> args = objectMapper.readValue(arguments,
                    new TypeReference<Map<String, Object>>() {
                    });
            Object requested = args.get("requestedModelId");
            return requested == null ? null : String.valueOf(requested);
        } catch (Exception e) {
            return null;
        }
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
