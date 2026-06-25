package com.example.app.service.impl;

import com.example.app.client.OllamaClient;
import com.example.app.client.OpenAICompatibleClient;
import com.example.app.dto.ContentOptimizationRequest;
import com.example.app.dto.ContentOptimizationResponse;
import com.example.app.dto.ContentOptimizationResponse.OptimizationDetail;
import com.example.app.entity.ModelConfig;
import com.example.app.entity.ModelConfig.ModelType;
import com.example.app.service.ContentOptimizationService;
import com.example.app.service.ModelConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;

/**
 * 内容优化服务实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContentOptimizationServiceImpl implements ContentOptimizationService {

    private final OllamaClient ollamaClient;
    private final OpenAICompatibleClient openAICompatibleClient;
    private final ModelConfigService modelConfigService;

    @Value("${optimization.model:llama3}")
    private String defaultModel;

    @Override
    public ContentOptimizationResponse optimizeContent(ContentOptimizationRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            String content = request.getContent();

            log.info("开始内容优化处理，内容长度: {} 字符, 模型ID: {}", content.length(), request.getModelId());

            String optimizedContent = processOptimization(request);

            long processingTime = System.currentTimeMillis() - startTime;

            List<OptimizationDetail> optimizations = buildOptimizationDetails(request.getOptimizationType());

            log.info("内容优化完成，耗时: {}ms", processingTime);

            return ContentOptimizationResponse.success(
                    optimizedContent,
                    content,
                    optimizations,
                    processingTime);

        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            log.error("内容优化失败，耗时: {}ms, 错误: {}", processingTime, e.getMessage(), e);

            return ContentOptimizationResponse.failure("OPTIMIZATION_FAILED", "内容优化失败: " + e.getMessage());
        }
    }

    /**
     * 执行优化处理
     */
    private String processOptimization(ContentOptimizationRequest request) {
        String content = request.getContent();
        String optimizationType = request.getOptimizationType();
        String systemPrompt = buildSystemPrompt(optimizationType);

        // 优先使用请求中指定的模型配置
        String modelId = request.getModelId();
        String modelType = request.getModelType();
        String baseUrl = request.getBaseUrl();
        String apiKey = request.getApiKey();

        try {
            // 检查请求中是否指定了模型
            if (modelId != null && !modelId.isEmpty()) {
                log.info("使用请求指定的模型: {}, 类型: {}", modelId, modelType);

                // 根据模型类型选择调用方式
                if (isOllamaType(modelType)) {
                    // Ollama 类型模型
                    List<ChatMessage> messages = List.of(
                            SystemMessage.from(systemPrompt),
                            UserMessage.from(content));
                    return ollamaClient.generate(messages, modelId);
                } else {
                    // OpenAI 兼容类型模型（包括 OPENAI_COMPATIBLE, OPENAI, AZURE, CUSTOM）
                    // 如果没有提供完整配置，尝试从数据库获取
                    if ((baseUrl == null || baseUrl.isEmpty()) || (apiKey == null || apiKey.isEmpty())) {
                        log.info("请求缺少 baseUrl 或 apiKey，尝试从数据库获取配置");
                        ModelConfig customConfig = modelConfigService.getConfigByModelId(modelId);
                        if (customConfig != null) {
                            log.info("使用数据库配置的模型: {}", customConfig.getModelId());
                            String actualModelId = extractModelId(customConfig.getModelId(), customConfig.getName());
                            return openAICompatibleClient.chatCompletion(
                                    actualModelId,
                                    customConfig.getBaseUrl(),
                                    customConfig.getApiKey(),
                                    systemPrompt,
                                    content);
                        }
                        log.warn("数据库中未找到模型配置: {}", modelId);
                    }
                    
                    // 使用请求中提供的配置或默认值
                    String actualBaseUrl = baseUrl != null && !baseUrl.isEmpty() ? baseUrl
                            : "https://api.openai.com/v1";
                    String actualApiKey = apiKey != null && !apiKey.isEmpty() ? apiKey : "";
                    log.info("使用请求配置或默认配置: baseUrl={}", actualBaseUrl);
                    return openAICompatibleClient.chatCompletion(modelId, actualBaseUrl, actualApiKey, systemPrompt,
                            content);
                }
            }

            // 如果请求中没有指定模型，尝试从数据库获取用户配置的模型
            log.info("请求未指定模型，尝试从数据库获取配置");
            ModelConfig customConfig = modelConfigService.getConfigByModelId(modelId);
            if (customConfig != null) {
                log.info("使用数据库配置的模型: {}", customConfig.getModelId());

                if (customConfig.getType() == ModelType.OLLAMA) {
                    List<ChatMessage> messages = List.of(
                            SystemMessage.from(systemPrompt),
                            UserMessage.from(content));
                    return ollamaClient.generate(messages, customConfig.getModelId());
                } else {
                    // 提取实际模型ID（移除前缀）
                    String actualModelId = extractModelId(customConfig.getModelId(), customConfig.getName());
                    return openAICompatibleClient.chatCompletion(
                            actualModelId,
                            customConfig.getBaseUrl(),
                            customConfig.getApiKey(),
                            systemPrompt,
                            content);
                }
            }

            // 使用默认模型
            log.info("使用默认模型: {}", defaultModel);
            List<ChatMessage> messages = List.of(
                    SystemMessage.from(systemPrompt),
                    UserMessage.from(content));
            return ollamaClient.generate(messages, defaultModel);

        } catch (Exception e) {
            log.warn("使用指定模型失败，尝试使用备用优化策略: {}", e.getMessage());
            return fallbackOptimization(content);
        }
    }

    /**
     * 判断是否为 Ollama 类型
     */
    private boolean isOllamaType(String modelType) {
        if (modelType == null) {
            return false;
        }
        return modelType.equalsIgnoreCase("OLLAMA") || modelType.equalsIgnoreCase("ollama");
    }

    /**
     * 从模型ID中提取实际模型名称
     */
    private String extractModelId(String fullModelId, String configName) {
        if (fullModelId != null && configName != null && fullModelId.startsWith(configName + "-")) {
            return fullModelId.substring(configName.length() + 1);
        }
        return fullModelId;
    }

    /**
     * 构建系统提示词
     */
    private String buildSystemPrompt(String optimizationType) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("优化文本：");

        if (optimizationType == null || optimizationType.isEmpty() || "all".equalsIgnoreCase(optimizationType)) {
            prompt.append("进行语法纠错、语义优化、格式规范和风格提升");
        } else {
            switch (optimizationType.toLowerCase()) {
                case "grammar":
                    prompt.append("进行语法纠错");
                    break;
                case "semantic":
                    prompt.append("进行语义优化");
                    break;
                case "format":
                    prompt.append("进行格式规范");
                    break;
                case "keyword":
                    prompt.append("进行关键词强化");
                    break;
                default:
                    prompt.append("进行全面优化");
            }
        }

        prompt.append("。保持原意，只输出优化后的文本，不解释，不说明。\n\n");

        return prompt.toString();
    }

    /**
     * 备用优化策略（当LLM不可用时）
     */
    private String fallbackOptimization(String content) {
        StringBuilder result = new StringBuilder(content);

        result = new StringBuilder(result.toString().replaceAll("\\s+", " ").trim());

        result = new StringBuilder(result.toString()
                .replace("。。", "。")
                .replace("，，", "，")
                .replace("！！", "！")
                .replace("？？", "？")
                .replace("；；", "；"));

        String str = result.toString();
        if (!str.isEmpty() && !str.endsWith("。") && !str.endsWith("！") && !str.endsWith("？") && !str.endsWith("；")) {
            str += "。";
        }

        return str;
    }

    /**
     * 构建优化详情列表
     */
    private List<OptimizationDetail> buildOptimizationDetails(String optimizationType) {
        List<OptimizationDetail> details = new ArrayList<>();

        if (optimizationType == null || optimizationType.isEmpty() || "all".equalsIgnoreCase(optimizationType)) {
            details.add(OptimizationDetail.builder().type("grammar").description("语法错误修正").build());
            details.add(OptimizationDetail.builder().type("semantic").description("语义优化").build());
            details.add(OptimizationDetail.builder().type("format").description("格式规范化").build());
            details.add(OptimizationDetail.builder().type("keyword").description("关键词强化").build());
        } else {
            switch (optimizationType.toLowerCase()) {
                case "grammar":
                    details.add(OptimizationDetail.builder().type("grammar").description("语法错误修正").build());
                    break;
                case "semantic":
                    details.add(OptimizationDetail.builder().type("semantic").description("语义优化").build());
                    break;
                case "format":
                    details.add(OptimizationDetail.builder().type("format").description("格式规范化").build());
                    break;
                case "keyword":
                    details.add(OptimizationDetail.builder().type("keyword").description("关键词强化").build());
                    break;
                default:
                    details.add(OptimizationDetail.builder().type("general").description("综合优化").build());
            }
        }

        return details;
    }
}