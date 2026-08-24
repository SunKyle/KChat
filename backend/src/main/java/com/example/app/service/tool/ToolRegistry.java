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
        int duplicateCount = 0;
        for (ToolComponent bean : toolBeans) {
            duplicateCount += registerBean(bean);
        }
        if (duplicateCount > 0) {
            log.error("ToolRegistry initialized with {} tool(s), {} duplicate(s) skipped (first-wins): {}",
                    specByName.size(), duplicateCount, specByName.keySet());
        } else {
            log.info("ToolRegistry initialized with {} tool(s): {}", specByName.size(), specByName.keySet());
        }
    }

    /**
     * 注册单个 Bean 上的所有 @Tool 方法。
     *
     * <p>重名策略：first-wins。已注册的工具名不会被后续注册覆盖，
     * 避免静默 override 导致工具行为漂移。重名以 ERROR 级别告警并打印双方来源，
     * 便于在启动日志中快速定位冲突。
     *
     * @return 本次注册过程中跳过的重名工具数
     */
    private int registerBean(Object bean) {
        int duplicates = 0;
        for (Method method : bean.getClass().getDeclaredMethods()) {
            Tool annotation = method.getAnnotation(Tool.class);
            if (annotation == null) {
                continue;
            }
            String toolName = annotation.name() == null || annotation.name().isBlank()
                    ? method.getName()
                    : annotation.name();
            if (toolByName.containsKey(toolName)) {
                Object previousBean = toolByName.get(toolName);
                log.error(
                        "Duplicate tool name '{}' — keeping first registration {}#{}, skipping {}#{} (first-wins; "
                                + "rename one of the @Tool annotations to avoid silent behavior drift)",
                        toolName,
                        previousBean.getClass().getSimpleName(),
                        methodByName.get(toolName).getName(),
                        bean.getClass().getSimpleName(),
                        method.getName());
                duplicates++;
                continue;
            }
            method.setAccessible(true);
            toolByName.put(toolName, bean);
            methodByName.put(toolName, method);
            specByName.put(toolName, ToolSpecifications.toolSpecificationFrom(method));
        }
        return duplicates;
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

    /** 查询指定工具执行所需的模型能力（null 表示不依赖特定能力）。 */
    public String getRequiredCapability(String name) {
        Object bean = toolByName.get(name);
        if (bean instanceof ToolComponent tc) {
            return tc.requiredCapability();
        }
        return null;
    }

    /** 是否已注册任何工具。 */
    public boolean isEmpty() {
        return toolByName.isEmpty();
    }
}
