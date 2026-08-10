package com.example.app.util;

import dev.langchain4j.data.message.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 简单 Token 估算器
 *
 * 基于字符数进行估算，作为精确估算的降级方案。
 * 按 Unicode 范围区分中文和英文，分别应用不同的估算系数：
 * - 中文字符（CJK Unified Ideographs + Extensions）：~1.5 字符/Token
 * - 英文/其他字符：~4 字符/Token
 *
 * 适用于不需要精确计算的场景，或作为精确估算器不可用时的降级方案
 */
@Component("simpleTokenEstimator")
@Slf4j
public class SimpleTokenEstimator implements TokenEstimator {

    /**
     * 中文字符到 Token 的转换系数（约 1 Token ≈ 1.5 个汉字）
     */
    private static final double CJK_CHARS_PER_TOKEN = 1.5;

    /**
     * 非中文字符到 Token 的转换系数（约 1 Token ≈ 4 个英文字母）
     */
    private static final double NON_CJK_CHARS_PER_TOKEN = 4.0;

    /**
     * 判断是否为 CJK 字符（中文、日文汉字等）
     * 覆盖范围：CJK Unified Ideographs (U+4E00–U+9FFF)、
     * CJK Extension A (U+3400–U+4DBF)、
     * CJK Compatibility Ideographs (U+F900–U+FAFF)、
     * CJK Radicals Supplement (U+2E80–U+2EFF)、
     * Kangxi Radicals (U+2F00–U+2FDF)、
     * 全角标点 (U+3000–U+303F, U+FF00–U+FFEF)
     */
    private static boolean isCjkOrFullwidth(int codePoint) {
        return (codePoint >= 0x4E00 && codePoint <= 0x9FFF) // CJK Unified
                || (codePoint >= 0x3400 && codePoint <= 0x4DBF) // CJK Ext-A
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF) // CJK Compat
                || (codePoint >= 0x2E80 && codePoint <= 0x2FDF) // CJK Radicals
                || (codePoint >= 0x3000 && codePoint <= 0x303F) // CJK Punctuation
                || (codePoint >= 0xFF00 && codePoint <= 0xFFEF); // Fullwidth Forms
    }

    @Override
    public int estimate(ChatMessage message) {
        if (message == null || getMessageText(message) == null) {
            return 0;
        }
        return estimateText(getMessageText(message));
    }

    @Override
    public int estimate(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (ChatMessage message : messages) {
            total += estimate(message);
        }
        return total;
    }

    @Override
    public int estimateText(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int cjkChars = 0;
        int otherChars = 0;

        for (int i = 0; i < text.length(); i++) {
            int cp = text.codePointAt(i);
            if (Character.isSupplementaryCodePoint(cp)) {
                i++; // 跳过低代理项
            }
            if (isCjkOrFullwidth(cp)) {
                cjkChars++;
            } else {
                // 空白字符不计入 Token
                if (!Character.isWhitespace(cp)) {
                    otherChars++;
                }
            }
        }

        double estimatedTokens = (cjkChars / CJK_CHARS_PER_TOKEN) + (otherChars / NON_CJK_CHARS_PER_TOKEN);
        int result = Math.max(1, (int) Math.ceil(estimatedTokens));

        log.debug("Estimated {} tokens: CJK={}, other={} (total chars={})",
                result, cjkChars, otherChars, text.length());
        return result;
    }

    @Override
    public String getEncodingType() {
        return "cjk-aware-char-count";
    }

    private static String getMessageText(ChatMessage message) {
        if (message instanceof dev.langchain4j.data.message.UserMessage userMsg) {
            return userMsg.singleText();
        } else if (message instanceof dev.langchain4j.data.message.AiMessage aiMsg) {
            return aiMsg.text();
        } else if (message instanceof dev.langchain4j.data.message.SystemMessage sysMsg) {
            return sysMsg.text();
        }
        return null;
    }
}