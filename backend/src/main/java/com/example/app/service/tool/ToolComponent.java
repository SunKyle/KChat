package com.example.app.service.tool;

import com.example.app.config.ModelCapability;

/**
 * Marker interface for Spring beans that contain @Tool-annotated methods.
 *
 * Implementing this interface allows {@link ToolRegistry} to discover and
 * collect all tool beans via Spring injection (List&lt;ToolComponent&gt;).
 */
public interface ToolComponent {

    /**
     * 该工具执行时所需的模型能力（如 IMAGE_IN / IMAGE_OUT）。
     * 返回 null 表示该工具不依赖特定能力的模型（如 webSearch 使用默认模型）。
     * 工具箱页面据此过滤可选择的模型。
     */
    default String requiredCapability() {
        return null;
    }
}
