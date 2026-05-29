package com.example.app.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Ollama 配置类
 *
 * 配置与本地 Ollama 服务的连接参数和默认模型
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
                .build();
    }
}