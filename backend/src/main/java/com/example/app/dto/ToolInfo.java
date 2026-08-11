package com.example.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Tool 信息 DTO
 *
 * 供前端 Tool 列表页面展示，从 {@link dev.langchain4j.agent.tool.ToolSpecification}
 * 转换而来，避免直接暴露 LangChain4j 内部结构。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolInfo {

    /** 工具名（对应 @Tool 注解的 name 或方法名） */
    private String name;

    /** 工具描述（注入 LLM 提示词，决定模型何时调用该工具） */
    private String description;

    /** 工具参数 schema（JSON Schema 风格，含 properties / required） */
    private Map<String, Object> parameters;

    /**
     * 该工具执行所需的模型能力（如 IMAGE_IN / IMAGE_OUT）。
     * 为 null 表示不依赖特定能力的模型。前端据此在工具箱页面对应工具下提供模型选择。
     */
    private String modelCapability;

    /** 工具是否启用。null 或 true 表示启用，false 表示已关闭（对 LLM 不可见）。 */
    private Boolean enabled;
}
