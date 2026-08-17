package com.example.app.service.tool.tools;

import com.example.app.service.tool.ToolComponent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * JSON 格式化工具
 *
 * 提供 JSON 格式化、压缩、验证功能。
 * 当用户需要格式化 JSON 数据、验证 JSON 合法性或压缩 JSON 时调用。
 */
@Slf4j
@Component
public class JsonFormatTool implements ToolComponent {

    private static final int MAX_INPUT_LENGTH = 100000;
    private static final int MAX_OUTPUT_LENGTH = 50000;

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Tool("""
            格式化、验证或压缩 JSON 字符串。
            - 格式化：将压缩的 JSON 转为美观的缩进格式（默认行为）
            - 压缩：去除多余空格和换行，将 JSON 压缩为单行
            - 验证：检查 JSON 是否合法，返回解析结果
            输入待处理的 JSON 字符串和操作类型（format/compress/validate），返回处理后的结果。
            适用于：调试 API 响应、整理配置文件、验证 JSON 数据等场景。
            """)
    public String formatJson(
            String json,
            String action) {
        if (json == null || json.isBlank()) {
            return "错误：JSON 字符串不能为空";
        }

        if (json.length() > MAX_INPUT_LENGTH) {
            return "错误：JSON 过长（最多 " + MAX_INPUT_LENGTH + " 字符，当前 " + json.length() + " 字符）";
        }

        String op = (action != null && !action.isBlank()) ? action.trim().toLowerCase() : "format";

        try {
            JsonNode tree = mapper.readTree(json);

            return switch (op) {
                case "validate" -> {
                    log.info("[JsonFormatTool] Validation passed");
                    yield "JSON 验证通过，结构合法。\n根节点类型：" + tree.getNodeType();
                }
                case "compress" -> {
                    String compact = tree.toString();
                    if (compact.length() > MAX_OUTPUT_LENGTH) {
                        yield "错误：压缩结果过长（" + compact.length() + " 字符），超过限制";
                    }
                    log.info("[JsonFormatTool] Compressed: {} -> {} chars", json.length(), compact.length());
                    yield compact;
                }
                default -> {
                    String formatted = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(tree);
                    if (formatted.length() > MAX_OUTPUT_LENGTH) {
                        yield "错误：格式化结果过长（" + formatted.length() + " 字符），超过限制";
                    }
                    log.info("[JsonFormatTool] Formatted: {} -> {} chars", json.length(), formatted.length());
                    yield formatted;
                }
            };

        } catch (JsonProcessingException e) {
            String msg = e.getMessage();
            log.warn("[JsonFormatTool] Invalid JSON: {}", msg);
            return "JSON 解析失败：\n" + msg;
        } catch (Exception e) {
            log.error("[JsonFormatTool] Error: {}", e.getMessage());
            return "处理失败：" + e.getMessage();
        }
    }
}