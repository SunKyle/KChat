package com.example.app.controller;

import com.example.app.dto.ToolInfo;
import com.example.app.service.tool.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Tool 信息查询接口
 *
 * 暴露 {@code GET /api/tools} 供前端展示当前注册的工具列表，
 * 数据来自 {@link ToolRegistry#getSpecifications()}。
 *
 * Agent 模式下 LLM 可调用的工具对用户透明可见，便于：
 * <ul>
 *   <li>用户了解 Agent 的能力边界</li>
 *   <li>开发者调试工具注册情况</li>
 *   <li>诊断"为什么没调用某个工具"</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/tools")
@RequiredArgsConstructor
@Slf4j
public class ToolController {

    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ResponseEntity<List<ToolInfo>> listTools() {
        List<ToolSpecification> specs = toolRegistry.getSpecifications();

        List<ToolInfo> tools = specs.stream()
                .map(this::toToolInfo)
                .toList();

        log.debug("[ToolController] listed {} tools", tools.size());
        return ResponseEntity.ok(tools);
    }

    private ToolInfo toToolInfo(ToolSpecification spec) {
        ToolInfo.ToolInfoBuilder builder = ToolInfo.builder()
                .name(spec.name())
                .description(spec.description());

        // ToolSpecification.parameters() 在不同 LangChain4j 版本中结构不同，
        // 通过 Jackson 序列化为通用 Map 避免硬依赖。
        try {
            Object parameters = spec.parameters();
            if (parameters != null) {
                builder.parameters(objectMapper.convertValue(parameters,
                        new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {}));
            }
        } catch (Exception e) {
            log.debug("Failed to convert parameters of tool '{}': {}", spec.name(), e.getMessage());
        }

        return builder.build();
    }
}
