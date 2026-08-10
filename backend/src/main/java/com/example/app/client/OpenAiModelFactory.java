package com.example.app.client;

import com.example.app.config.OpenAIClientProperties;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenAI 兼容模型工厂
 *
 * 按 (baseUrl, apiKey, modelId) 维度缓存 ChatModel / StreamingChatModel 实例，
 * 避免每次请求都重新构建。线程安全。
 *
 * 设计参考 OllamaClient.modelCache 的做法，统一 OpenAI 兼容模型的实例管理。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OpenAiModelFactory {

    private final OpenAIClientProperties props;
    private final ConcurrentHashMap<String, ChatModel> chatCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, StreamingChatModel> streamCache = new ConcurrentHashMap<>();

    /** 构建缓存 key，apiKey 用 hashCode 避免明文堆积 */
    private String key(String baseUrl, String apiKey, String modelId) {
        return baseUrl + "|" + (apiKey == null ? "" : apiKey.hashCode()) + "|" + modelId;
    }

    /**
     * 获取同步聊天模型（OpenAI 兼容协议）
     */
    public ChatModel chatModel(String baseUrl, String apiKey, String modelId) {
        String k = key(baseUrl, apiKey, modelId);
        return chatCache.computeIfAbsent(k, ignored -> {
            log.debug("Building OpenAiChatModel: baseUrl={}, model={}", baseUrl, modelId);
            return OpenAiChatModel.builder()
                    .baseUrl(normalizeBaseUrl(baseUrl))
                    .apiKey(apiKey != null && !apiKey.isBlank() ? apiKey : "dummy")
                    .modelName(modelId)
                    .maxTokens(props.getDefaultMaxTokens())
                    .temperature(props.getSyncTemperature())
                    .timeout(Duration.ofSeconds(props.getChat().getReadSeconds()))
                    .build();
        });
    }

    /**
     * 获取流式聊天模型（OpenAI 兼容协议）
     */
    public StreamingChatModel streamingModel(String baseUrl, String apiKey, String modelId) {
        String k = key(baseUrl, apiKey, modelId);
        return streamCache.computeIfAbsent(k, ignored -> {
            log.debug("Building OpenAiStreamingChatModel: baseUrl={}, model={}", baseUrl, modelId);
            return OpenAiStreamingChatModel.builder()
                    .baseUrl(normalizeBaseUrl(baseUrl))
                    .apiKey(apiKey != null && !apiKey.isBlank() ? apiKey : "dummy")
                    .modelName(modelId)
                    .maxTokens(props.getDefaultMaxTokens())
                    .temperature(props.getStreamTemperature())
                    .timeout(Duration.ofSeconds(props.getStream().getReadSeconds()))
                    .build();
        });
    }

    /**
     * 模型配置变更时清理缓存
     */
    public void evict(String baseUrl, String apiKey, String modelId) {
        String k = key(baseUrl, apiKey, modelId);
        chatCache.remove(k);
        streamCache.remove(k);
    }

    /** 清理全部缓存（仅用于测试或全局重置） */
    public void evictAll() {
        chatCache.clear();
        streamCache.clear();
    }

    /** LangChain4j OpenAiChatModel 期望 baseUrl 不含路径后缀，统一处理 */
    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://api.openai.com/v1";
        }
        // 移除可能的尾部斜杠
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        // 如果已经包含 /v1 路径，直接返回
        if (normalized.endsWith("/v1")) {
            return normalized;
        }
        // 不含 /v1 时自动追加（OpenAiChatModel 默认会拼 /chat/completions）
        if (!normalized.contains("/v1/chat/completions") && !normalized.contains("/v1/images/generations")) {
            return normalized + "/v1";
        }
        return normalized;
    }
}
