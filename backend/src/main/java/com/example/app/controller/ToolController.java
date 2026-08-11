package com.example.app.controller;

import com.example.app.dto.ToolInfo;
import com.example.app.service.UserSettingService;
import com.example.app.service.tool.ToolRegistry;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNullSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool 信息查询接口
 *
 * 暴露 {@code GET /api/tools} 供前端展示当前注册的工具列表，
 * 数据来自 {@link ToolRegistry#getSpecifications()}。
 *
 * Agent 模式下 LLM 可调用的工具对用户透明可见，便于：
 * <ul>
 * <li>用户了解 Agent 的能力边界</li>
 * <li>开发者调试工具注册情况</li>
 * <li>诊断"为什么没调用某个工具"</li>
 * </ul>
 *
 * <p>
 * 注意：LangChain4j 1.4.0 的 {@link ToolSpecification#parameters()} 返回
 * {@link JsonObjectSchema}，其字段方法为 {@code properties()}/{@code required()}，
 * 而非标准 JavaBean 的 {@code getProperties()}，因此不能直接交给 Jackson
 * 的 {@code convertValue} 转换（会得到 null）。这里手动递归转为通用 Map。
 */
@RestController
@RequestMapping("/api/tools")
@RequiredArgsConstructor
@Slf4j
public class ToolController {

    private final ToolRegistry toolRegistry;
    private final UserSettingService userSettingService;

    @GetMapping
    public ResponseEntity<List<ToolInfo>> listTools(
            @RequestParam(required = false, defaultValue = "default") String userId) {
        List<ToolSpecification> specs = toolRegistry.getSpecifications();

        Map<String, Boolean> enabledTools = userSettingService.getEnabledTools(userId);

        List<ToolInfo> tools = specs.stream()
                .map(spec -> toToolInfo(spec, enabledTools))
                .toList();

        log.debug("[ToolController] listed {} tools for user {}", tools.size(), userId);
        return ResponseEntity.ok(tools);
    }

    private ToolInfo toToolInfo(ToolSpecification spec, Map<String, Boolean> enabledTools) {
        Boolean enabled = enabledTools.get(spec.name());
        // null or true means enabled (default)
        boolean isEnabled = enabled == null || enabled;

        ToolInfo.ToolInfoBuilder builder = ToolInfo.builder()
                .name(spec.name())
                .description(spec.description())
                .modelCapability(toolRegistry.getRequiredCapability(spec.name()))
                .enabled(isEnabled);

        Object parameters = spec.parameters();
        if (parameters instanceof JsonObjectSchema schema) {
            builder.parameters(convertObjectSchema(schema));
        }

        return builder.build();
    }

    /**
     * 把 JsonObjectSchema 转为 {"type":"object","properties":{...},"required":[...]}
     * 的通用 Map。
     */
    private Map<String, Object> convertObjectSchema(JsonObjectSchema schema) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "object");

        Map<String, Object> props = new LinkedHashMap<>();
        schema.properties().forEach((name, element) -> props.put(name, convertElement(element)));
        map.put("properties", props);

        if (schema.required() != null && !schema.required().isEmpty()) {
            map.put("required", schema.required());
        }
        return map;
    }

    /** 递归把单个 JsonSchemaElement 转为通用 Map（含 type / description / enum 等）。 */
    private Map<String, Object> convertElement(JsonSchemaElement element) {
        Map<String, Object> map = new LinkedHashMap<>();

        if (element instanceof JsonStringSchema) {
            map.put("type", "string");
        } else if (element instanceof JsonIntegerSchema) {
            map.put("type", "integer");
        } else if (element instanceof JsonNumberSchema) {
            map.put("type", "number");
        } else if (element instanceof JsonBooleanSchema) {
            map.put("type", "boolean");
        } else if (element instanceof JsonNullSchema) {
            map.put("type", "null");
        } else if (element instanceof JsonArraySchema arraySchema) {
            map.put("type", "array");
            if (arraySchema.items() != null) {
                map.put("items", convertElement(arraySchema.items()));
            }
        } else if (element instanceof JsonEnumSchema enumSchema) {
            map.put("type", "string");
            if (enumSchema.enumValues() != null && !enumSchema.enumValues().isEmpty()) {
                map.put("enum", enumSchema.enumValues());
            }
        } else if (element instanceof JsonObjectSchema objectSchema) {
            map.putAll(convertObjectSchema(objectSchema));
        } else {
            map.put("type", "object");
        }

        String description = element.description();
        if (description != null && !description.isBlank()) {
            map.put("description", description);
        }
        return map;
    }
}