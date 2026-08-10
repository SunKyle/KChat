package com.example.app.config;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 流式聊天模型配置
 *
 * 暴露按 modelName 构建 OllamaStreamingChatModel 的工厂方法，
 * 内部用 ConcurrentHashMap 缓存，避免每次请求都重新构建实例。
 * 默认 Bean 仍保留，便于其他模块按类型注入默认模型。
 */
@Configuration
@RequiredArgsConstructor
public class StreamingConfig {

    private final OllamaConfig ollamaConfig;
    private final ConcurrentHashMap<String, OllamaStreamingChatModel> cache = new ConcurrentHashMap<>();

    /**
     * 按 modelName 获取 Ollama 流式聊天模型实例。
     * 相同 modelName 复用同一实例。
     */
    public StreamingChatLanguageModel streamingModel(String modelName) {
        return cache.computeIfAbsent(modelName, k -> OllamaStreamingChatModel.builder()
                .baseUrl(ollamaConfig.getBaseUrl())
                .modelName(k)
                .timeout(Duration.ofMinutes(ollamaConfig.getTimeoutMinutes()))
                .build());
    }

    /**
     * 默认流式聊天模型 Bean，使用配置中的默认模型。
     * 保留以兼容已有按类型注入的调用方。
     */
    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel() {
        return streamingModel(ollamaConfig.getDefaultModel());
    }

    /** 清理缓存（仅在模型配置变更或测试场景调用） */
    public void evict(String modelName) {
        cache.remove(modelName);
    }

    public void evictAll() {
        cache.clear();
    }
}
