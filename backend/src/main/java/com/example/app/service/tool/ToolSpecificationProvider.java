package com.example.app.service.tool;

import com.example.app.service.UserSettingService;
import dev.langchain4j.agent.tool.ToolSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ToolSpecification 提供者
 *
 * 封装 {@link ToolRegistry} 的规格查询，供 AGENT 阶段（如 ToolDefinitionStage、ModelRoutingStage）
 * 获取 LLM 函数调用所需的 {@link ToolSpecification} 列表。
 *
 * 支持按用户过滤已禁用的工具：用户可以在工具箱页面关闭某些工具，
 * 关闭的工具不会出现在 LLM 的 function calling 列表中。
 */
@Component
@RequiredArgsConstructor
public class ToolSpecificationProvider {

    private final ToolRegistry toolRegistry;
    private final UserSettingService userSettingService;

    /**
     * 返回所有已注册工具的 ToolSpecification（不过滤）。
     * 无工具注册时返回空列表（非 null）。
     */
    public List<ToolSpecification> getToolSpecifications() {
        return toolRegistry.getSpecifications();
    }

    /**
     * 返回指定用户启用的工具 ToolSpecification 列表。
     * 已禁用的工具（enabledTools 中值为 false）被过滤掉。
     *
     * @param userId 用户 ID，为 null 时返回全部工具
     */
    public List<ToolSpecification> getToolSpecifications(String userId) {
        List<ToolSpecification> allSpecs = toolRegistry.getSpecifications();
        if (userId == null) {
            return allSpecs;
        }

        Map<String, Boolean> enabledTools = userSettingService.getEnabledTools(userId);
        if (enabledTools.isEmpty()) {
            return allSpecs;
        }

        return allSpecs.stream()
                .filter(spec -> {
                    Boolean enabled = enabledTools.get(spec.name());
                    return enabled == null || enabled;
                })
                .collect(Collectors.toList());
    }

    /** 是否有可用工具。 */
    public boolean hasTools() {
        return !toolRegistry.isEmpty();
    }
}