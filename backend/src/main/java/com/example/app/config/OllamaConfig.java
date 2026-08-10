package com.example.app.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Ollama 配置类
 *
 * 配置与本地 Ollama 服务的连接参数、默认模型、
 * 生成超时时间，以及模型列表缓存 TTL。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ollama")
public class OllamaConfig {

    /**
     * Ollama 服务地址
     * 默认使用本地地址 http://localhost:11434
     */
    private String baseUrl = "http://localhost:11434";

    /**
     * 默认使用的模型名称
     * 默认为 llama3，可根据实际已下载的模型修改
     */
    private String defaultModel = "llama3";

    /**
     * 单次同步生成的超时时间（分钟）
     * CPU 推理较慢时可适当放大
     */
    private int timeoutMinutes = 2;

    /**
     * 模型列表（/api/tags）缓存 TTL（毫秒）
     * 在缓存期间不会重复调用 Ollama 获取模型列表
     */
    private long modelsCacheTtlMs = 30_000L;

    /**
     * 图片转 base64 时的连接/读取超时（毫秒）
     */
    private int imageFetchConnectTimeoutMs = 5000;
    private int imageFetchReadTimeoutMs = 10_000;

    /**
     * 调用 embedding 接口的超时（毫秒）
     */
    private int embedConnectTimeoutMs = 5000;
    private int embedReadTimeoutMs = 30_000;

    /**
     * 获取模型列表的超时（毫秒）
     */
    private int listModelsConnectTimeoutMs = 5000;
    private int listModelsReadTimeoutMs = 10_000;

    /**
     * 创建 ChatLanguageModel Bean
     *
     * 设计考虑：
     * - 作为 Spring Bean 管理，便于依赖注入和测试替换
     * - 支持通过配置灵活切换模型
     *
     * @return 配置好的 OllamaChatModel
     */
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(defaultModel)
                .timeout(Duration.ofMinutes(timeoutMinutes))
                .build();
    }
}
