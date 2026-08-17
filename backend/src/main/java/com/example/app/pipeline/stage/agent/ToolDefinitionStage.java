package com.example.app.pipeline.stage.agent;

import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.service.tool.ToolSpecificationProvider;
import com.example.app.service.tool.tools.MemoryTool;
import dev.langchain4j.agent.tool.ToolSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 工具定义注入阶段
 *
 * 从 {@link ToolSpecificationProvider} 获取所有已注册工具的 ToolSpecification，
 * 写入 ctx.enabledToolNames（用于可观测性）和 ctx.agentState（供 ModelRoutingStage 读取）。
 *
 * 实际的 ToolSpecification 列表通过 agentState 传递，避免在 ConversationContext 中
 * 增加 LangChain4j 特定类型字段。
 *
 * order=480（ASSEMBLY 阶段，在 tokenManagementStage 之后、modelRoutingStage 之前）
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ToolDefinitionStage implements ContextPipelineStage {

    /** Key for tool specifications in agentState, read by ModelRoutingStage(500) */
    public static final String KEY_TOOL_SPECIFICATIONS = "toolSpecifications";

    private final ToolSpecificationProvider toolSpecificationProvider;

    @Override
    public Phase getPhase() {
        return Phase.ASSEMBLY;
    }

    @Override
    public String getName() {
        return "toolDefinitionStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        List<ToolSpecification> specs = toolSpecificationProvider.getToolSpecifications(ctx.getUserId());

        // 显式引用知识库时，禁用 Agent 记忆兜底检索（recallMemory 查 main_dataset）
        boolean explicitKbRef = ctx.getKnowledgeBaseIds() != null
                && !ctx.getKnowledgeBaseIds().isEmpty();
        if (explicitKbRef) {
            specs = specs.stream()
                    .filter(spec -> !MemoryTool.RECALL_MEMORY_TOOL.equals(spec.name()))
                    .collect(java.util.stream.Collectors.toList());
            log.info("[ToolDefinition] Explicit KB reference, disabled memory fallback tool '{}'",
                    MemoryTool.RECALL_MEMORY_TOOL);
        }

        ctx.getEnabledToolNames().clear();
        for (ToolSpecification spec : specs) {
            ctx.getEnabledToolNames().add(spec.name());
        }
        ctx.getAgentState().put(KEY_TOOL_SPECIFICATIONS, specs);
        log.info("[ToolDefinition] {} tool(s) enabled: {}", specs.size(), ctx.getEnabledToolNames());

        // 推送 Agent 思考过程：当前可用的工具列表
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tools", ctx.getEnabledToolNames());
        data.put("count", specs.size());
        ctx.emitAgentThinking("tool_definition", data);
    }

    @Override
    public boolean isApplicable(ConversationContext ctx) {
        return ctx.isAgentMode();
    }

    @Override
    public int getOrder() {
        return 480;
    }

    @Override
    public boolean isCritical() {
        return false;
    }
}
