package com.example.app.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 输入安全校验组件
 * 
 * 负责对用户输入进行安全校验和过滤，防止 Prompt 注入攻击
 */
@Component
@Slf4j
public class InputValidator {

    /**
     * 最大输入长度限制（字符）
     */
    @Value("${prompt.security.max-input-length:4096}")
    private int maxInputLength;

    /**
     * 最小输入长度（字符）
     */
    @Value("${prompt.security.min-input-length:1}")
    private int minInputLength;

    /**
     * 危险字符模式列表，用于检测潜在的注入攻击
     */
    private static final List<Pattern> DANGEROUS_PATTERNS = Arrays.asList(
            Pattern.compile("\\{\\{[^}]*\\}\\}"),           // 双花括号模板注入
            Pattern.compile("\\{%[^%]*%\\}"),               // Jinja2 模板语法
            Pattern.compile("<script[^>]*>[^<]*</script>"), // HTML script 标签
            Pattern.compile("javascript\\s*:"),             // JavaScript 伪协议
            Pattern.compile("on\\w+\\s*="),                 // HTML 事件属性
            Pattern.compile("\\bEXEC\\b|\\bEXECUTE\\b", Pattern.CASE_INSENSITIVE), // SQL 执行命令
            Pattern.compile("\\bUNION\\s+SELECT\\b", Pattern.CASE_INSENSITIVE), // SQL UNION
            Pattern.compile("\\bDROP\\s+TABLE\\b", Pattern.CASE_INSENSITIVE),    // SQL DROP
            Pattern.compile("\\bDELETE\\s+FROM\\b", Pattern.CASE_INSENSITIVE),   // SQL DELETE
            // UPDATE 单独出现过于宽泛（合法英文对话常含 "update"），改为更精确的 UPDATE...SET 模式
            Pattern.compile("\\bUPDATE\\s+\\w+\\s+SET\\b", Pattern.CASE_INSENSITIVE), // SQL UPDATE...SET
            Pattern.compile("\\bINSERT\\s+INTO\\b", Pattern.CASE_INSENSITIVE)    // SQL INSERT
    );

    /**
     * 危险字符替换列表
     */
    private static final List<String> DANGEROUS_CHARACTERS = Arrays.asList(
            "{{", "}}", "{%", "%}", "<script", "</script>", "javascript:", "vbscript:"
    );

    /**
     * 校验并清理用户输入
     * 
     * @param input 用户输入
     * @return 清理后的安全输入
     * @throws IllegalArgumentException 如果输入不符合安全要求
     */
    public String validateAndSanitize(String input) {
        if (input == null) {
            throw new IllegalArgumentException("输入不能为空");
        }

        String trimmedInput = input.trim();

        // 检查最小长度
        if (trimmedInput.length() < minInputLength) {
            throw new IllegalArgumentException("输入内容不能为空");
        }

        // 检查最大长度
        if (trimmedInput.length() > maxInputLength) {
            log.warn("Input exceeds max length: {} > {}", trimmedInput.length(), maxInputLength);
            throw new IllegalArgumentException("输入内容过长，最大允许 " + maxInputLength + " 字符");
        }

        // 过滤危险字符
        String sanitizedInput = filterDangerousCharacters(trimmedInput);

        // 检测潜在注入模式
        if (containsInjectionPattern(sanitizedInput)) {
            log.warn("Potential injection attempt detected: {}", sanitizedInput);
            throw new IllegalArgumentException("输入包含不安全内容");
        }

        log.debug("Input validated and sanitized successfully");
        return sanitizedInput;
    }

    /**
     * 过滤危险字符
     * 
     * @param input 原始输入
     * @return 过滤后的输入
     */
    public String filterDangerousCharacters(String input) {
        String result = input;
        for (String dangerousChar : DANGEROUS_CHARACTERS) {
            result = result.replace(dangerousChar, "");
        }
        return result;
    }

    /**
     * 检查是否包含注入模式
     * 
     * @param input 输入内容
     * @return true 如果包含注入模式
     */
    public boolean containsInjectionPattern(String input) {
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(input).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 仅校验，不清理（用于预检查）
     * 
     * @param input 用户输入
     * @return true 如果输入安全
     */
    public boolean isValid(String input) {
        if (input == null || input.trim().isEmpty()) {
            return false;
        }
        if (input.length() > maxInputLength) {
            return false;
        }
        return !containsInjectionPattern(input);
    }

    /**
     * 获取最大输入长度限制
     */
    public int getMaxInputLength() {
        return maxInputLength;
    }

    /**
     * 获取最小输入长度限制
     */
    public int getMinInputLength() {
        return minInputLength;
    }
}