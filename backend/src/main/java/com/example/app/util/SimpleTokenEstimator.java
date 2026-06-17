package com.example.app.util;

import dev.langchain4j.data.message.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 简单 Token 估算器
 * 
 * 基于字符数进行估算，作为精确估算的降级方案
 * - 中文：1 Token ≈ 4 字符
 * - 英文：1 Token ≈ 0.75 词（约 4-5 字符）
 * 
 * 适用于不需要精确计算的场景，或作为精确估算器不可用时的降级方案
 */
@Component("simpleTokenEstimator")
@Slf4j
public class SimpleTokenEstimator implements TokenEstimator {

    /**
     * 字符到 Token 的转换系数
     * 默认使用 4（适用于中文为主的场景）
     */
    private static final int CHARACTERS_PER_TOKEN = 4;

    @Override
    public int estimate(ChatMessage message) {
        if (message == null || message.text() == null) {
            return 0;
        }
        return estimateText(message.text());
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
        // 基于字符数估算，向上取整
        int charCount = text.length();
        int tokens = (charCount + CHARACTERS_PER_TOKEN - 1) / CHARACTERS_PER_TOKEN;
        log.debug("Estimated {} tokens for {} characters", tokens, charCount);
        return tokens;
    }

    @Override
    public String getEncodingType() {
        return "simple-char-count";
    }
}