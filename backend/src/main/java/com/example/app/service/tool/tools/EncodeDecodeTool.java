package com.example.app.service.tool.tools;

import com.example.app.service.tool.ToolComponent;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 编码解码工具
 *
 * 提供 Base64 和 URL 编码解码功能。
 * 当用户需要对文本进行编码或解码时调用。
 */
@Slf4j
@Component
public class EncodeDecodeTool implements ToolComponent {

    private static final int MAX_INPUT_LENGTH = 100000;

    @Tool("""
            对文本进行 Base64 或 URL 编码/解码。
            type 参数可选值：
            - base64_encode: Base64 编码
            - base64_decode: Base64 解码
            - url_encode: URL 编码
            - url_decode: URL 解码
            输入待处理的文本和操作类型，返回处理后的结果。
            适用于：处理传输数据、调试接口参数、查看编码内容等场景。
            """)
    public String encodeText(
            String text,
            String type) {
        if (text == null || text.isBlank()) {
            return "错误：输入文本不能为空";
        }

        if (text.length() > MAX_INPUT_LENGTH) {
            return "错误：文本过长（最多 " + MAX_INPUT_LENGTH + " 字符，当前 " + text.length() + " 字符）";
        }

        String op = (type != null && !type.isBlank()) ? type.trim().toLowerCase() : "base64_encode";

        try {
            return switch (op) {
                case "base64_encode" -> {
                    String result = Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
                    log.info("[EncodeDecodeTool] Base64 encode: {} -> {} chars", text.length(), result.length());
                    yield result;
                }
                case "base64_decode" -> {
                    byte[] decoded = Base64.getDecoder().decode(text);
                    String result = new String(decoded, StandardCharsets.UTF_8);
                    log.info("[EncodeDecodeTool] Base64 decode: {} -> {} chars", text.length(), result.length());
                    yield result;
                }
                case "url_encode" -> {
                    String result = URLEncoder.encode(text, StandardCharsets.UTF_8);
                    log.info("[EncodeDecodeTool] URL encode: {} -> {} chars", text.length(), result.length());
                    yield result;
                }
                case "url_decode" -> {
                    String result = URLDecoder.decode(text, StandardCharsets.UTF_8);
                    log.info("[EncodeDecodeTool] URL decode: {} -> {} chars", text.length(), result.length());
                    yield result;
                }
                default -> "错误：不支持的操作类型 '" + type + "'，可选值：base64_encode, base64_decode, url_encode, url_decode";
            };
        } catch (IllegalArgumentException e) {
            return "解码失败：输入内容不是有效的 " + (op.contains("base64") ? "Base64" : "URL") + " 编码格式 - " + e.getMessage();
        } catch (Exception e) {
            log.error("[EncodeDecodeTool] Error: {}", e.getMessage());
            return "处理失败：" + e.getMessage();
        }
    }
}