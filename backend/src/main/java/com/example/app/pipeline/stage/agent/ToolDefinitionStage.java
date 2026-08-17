package com.example.app.pipeline.stage.agent;

import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.service.tool.ToolSpecificationProvider;
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

        // 当用户显式引用知识库（@唤起）时，过滤掉 recallMemory 工具，
        // 避免 Agent 从 main_dataset 中兜底检索，只使用指定知识库的片段
        boolean hasExplicitKbRefs = ctx.getKnowledgeBaseIds() != null && !ctx.getKnowledgeBaseIds().isEmpty();
        if (hasExplicitKbRefs) {
            specs = specs.stream()
                    .filter(spec -> !"recallMemory".equals(spec.name()))
                    .toList();
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
