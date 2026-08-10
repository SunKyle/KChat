package com.example.app.util;

import dev.langchain4j.data.message.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 默认 Token 估算器
 * 
 * 优先使用精确估算（基于 tiktoken），如果不可用则降级到简单字符数估算
 * 
 * 支持的编码类型：
 * - cl100k_base: GPT-3.5, GPT-4
 * - gpt2: GPT-2
 * - r50k_base: GPT-3
 */
@Component
@Primary
@Slf4j
public class DefaultTokenEstimator implements TokenEstimator {

    /**
     * 编码类型配置
     */
    @Value("${prompt.token.encoding-type:cl100k_base}")
    private String encodingType;

    /**
     * 降级估算器
     */
    private final SimpleTokenEstimator fallbackEstimator;

    /**
     * 是否启用精确估算（tiktoken）
     */
    private final boolean preciseEnabled;

    /**
     * 精确估算器实例（如果可用）
     */
    private PreciseTokenEstimator preciseEstimator;

    public DefaultTokenEstimator(SimpleTokenEstimator fallbackEstimator) {
        this.fallbackEstimator = fallbackEstimator;

        // 检查 tiktoken 是否可用
        boolean enabled = false;
        try {
            Class.forName("com.knuddels.jtokkit.Encodings");
            this.preciseEstimator = new PreciseTokenEstimator();
            enabled = true;
            log.info("Precise token estimation (tiktoken) is enabled");
        } catch (ClassNotFoundException e) {
            log.warn("tiktoken library not found, falling back to simple estimation");
        } catch (Exception e) {
            log.warn("Failed to initialize precise token estimator: {}", e.getMessage());
        }
        this.preciseEnabled = enabled;
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

        // 优先使用精确估算
        if (preciseEnabled && preciseEstimator != null) {
            try {
                int result = preciseEstimator.estimateText(text, encodingType);
                log.debug("Precise token estimate: {} for text length {}", result, text.length());
                return result;
            } catch (Exception e) {
                log.warn("Precise token estimation failed, falling back to simple: {}", e.getMessage());
            }
        }

        // 降级到简单估算
        return fallbackEstimator.estimateText(text);
    }

    @Override
    public String getEncodingType() {
        if (preciseEnabled) {
            return encodingType;
        }
        return "simple-char-count (fallback)";
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

    /**
     * 检查是否使用精确估算
     */
    public boolean isPreciseEnabled() {
        return preciseEnabled;
    }

    /**
     * 精确 Token 估算器（内部类）
     * 
     * 使用 tiktoken 库进行精确 Token 计算
     */
    private static class PreciseTokenEstimator {

        /**
         * 估算文本的 Token 数量
         * 
         * @param text         文本内容
         * @param encodingType 编码类型
         * @return Token 数量
         */
        public int estimateText(String text, String encodingType) {
            try {
                // 使用反射调用 tiktoken，避免编译时依赖
                Class<?> encodingsClass = Class.forName("com.knuddels.jtokkit.Encodings");
                Object registry = encodingsClass.getMethod("getDefaultEncodingRegistry").invoke(null);

                Class<?> registryClass = Class.forName("com.knuddels.jtokkit.api.EncodingRegistry");
                Object encoding = registryClass.getMethod("getEncoding", String.class)
                        .invoke(registry, encodingType);

                Class<?> encodingClass = Class.forName("com.knuddels.jtokkit.api.Encoding");
                Object encoded = encodingClass.getMethod("encode", String.class).invoke(encoding, text);

                // 获取编码结果的大小
                if (encoded instanceof List) {
                    return ((List<?>) encoded).size();
                }

                // 如果是 int[]
                if (encoded instanceof int[]) {
                    return ((int[]) encoded).length;
                }

            } catch (Exception e) {
                throw new RuntimeException("Failed to estimate tokens with tiktoken", e);
            }

            // 默认返回简单估算
            return text.length() / 4;
        }
    }
}