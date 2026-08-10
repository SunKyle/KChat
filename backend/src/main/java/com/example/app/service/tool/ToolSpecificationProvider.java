package com.example.app.service.tool;

import dev.langchain4j.agent.tool.ToolSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ToolSpecification 提供者
 *
 * 封装 {@link ToolRegistry} 的规格查询，供 AGENT 阶段（如 ToolDefinitionStage、ModelRoutingStage）
 * 获取 LLM 函数调用所需的 {@link ToolSpecification} 列表。
 */
@Component
@RequiredArgsConstructor
public class ToolSpecificationProvider {

    private final ToolRegistry toolRegistry;

    /**
     * 返回所有已注册工具的 ToolSpecification。
     * 无工具注册时返回空列表（非 null）。
     */
    public List<ToolSpecification> getToolSpecifications() {
        return toolRegistry.getSpecifications();
    }

    /** 是否有可用工具。 */
    public boolean hasTools() {
        return !toolRegistry.isEmpty();
    }
}
