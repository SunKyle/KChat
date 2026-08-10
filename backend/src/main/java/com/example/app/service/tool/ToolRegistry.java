package com.example.app.service.tool;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Tool 注册中心
 *
 * 通过 Spring 注入所有 {@link ToolComponent} Bean，扫描其中带 {@link Tool} 注解的方法，
 * 提供：
 * <ul>
 *   <li>按工具名查找工具实例 + Method（供 {@link ToolExecutor} 反射调用）</li>
 *   <li>统一的 {@link ToolSpecification} 列表（供 LLM 函数调用）</li>
 * </ul>
 *
 * 工具名取自 {@link Tool#name()}（为空时回退到方法名），全局唯一。
 */
@Component
@Slf4j
public class ToolRegistry {

    private final List<ToolComponent> toolBeans;

    /** 按工具名 → 工具实例 */
    private final Map<String, Object> toolByName = new LinkedHashMap<>();
    /** 按工具名 → 反射 Method */
    private final Map<String, Method> methodByName = new LinkedHashMap<>();
    /** 按工具名 → ToolSpecification */
    private final Map<String, ToolSpecification> specByName = new LinkedHashMap<>();

    public ToolRegistry(List<ToolComponent> toolBeans) {
        this.toolBeans = toolBeans != null ? toolBeans : List.of();
    }

    @PostConstruct
    public void init() {
        for (ToolComponent bean : toolBeans) {
            registerBean(bean);
        }
        log.info("ToolRegistry initialized with {} tool(s): {}", specByName.size(), specByName.keySet());
    }

    private void registerBean(Object bean) {
        for (Method method : bean.getClass().getDeclaredMethods()) {
            Tool annotation = method.getAnnotation(Tool.class);
            if (annotation == null) {
                continue;
            }
            String toolName = annotation.name() == null || annotation.name().isBlank()
                    ? method.getName()
                    : annotation.name();
            if (toolByName.containsKey(toolName)) {
                log.warn("Duplicate tool name '{}', overriding previous registration", toolName);
            }
            method.setAccessible(true);
            toolByName.put(toolName, bean);
            methodByName.put(toolName, method);
            specByName.put(toolName, ToolSpecifications.toolSpecificationFrom(method));
        }
    }

    /** 所有已注册工具名（按注册顺序）。 */
    public List<String> getToolNames() {
        return List.copyOf(toolByName.keySet());
    }

    /** 所有 ToolSpecification（供 LLM 函数调用）。 */
    public List<ToolSpecification> getSpecifications() {
        return List.copyOf(specByName.values());
    }

    /** 按名查找工具实例。 */
    public Optional<Object> getTool(String name) {
        return Optional.ofNullable(toolByName.get(name));
    }

    /** 按名查找反射 Method。 */
    public Optional<Method> getMethod(String name) {
        return Optional.ofNullable(methodByName.get(name));
    }

    /** 是否已注册任何工具。 */
    public boolean isEmpty() {
        return toolByName.isEmpty();
    }
}
