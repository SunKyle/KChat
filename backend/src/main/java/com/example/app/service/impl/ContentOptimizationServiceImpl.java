package com.example.app.service.impl;

import com.example.app.client.OllamaClient;
import com.example.app.client.OpenAiModelFactory;
import com.example.app.dto.ContentOptimizationRequest;
import com.example.app.dto.ContentOptimizationResponse;
import com.example.app.dto.ContentOptimizationResponse.OptimizationDetail;
import com.example.app.entity.ModelConfig;
import com.example.app.entity.ModelConfig.ModelType;
import com.example.app.service.ContentOptimizationService;
import com.example.app.service.ModelConfigService;
import com.example.app.service.ai.ContentOptimizer;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 内容优化服务实现
 *
 * LLM 调用与 prompt 模板由 LangChain4j {@link AiServices} + {@link ContentOptimizer}
 * 统一处理，用 {@code @SystemMessage} 模板替代原先手写的 {@code buildSystemPrompt}。
 * 请求级模型路由（请求参数 > 数据库配置 > 默认模型）、降级清洗、详情构建保持自实现。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContentOptimizationServiceImpl implements ContentOptimizationService {

    private final OllamaClient ollamaClient;
    private final OpenAiModelFactory openAiModelFactory;
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
     * 执行优化处理：解析模型 → 构建 AiServices 代理 → 调用 LLM
     */
    private String processOptimization(ContentOptimizationRequest request) {
        String content = request.getContent();
        String instruction = buildInstruction(request.getOptimizationType());

        try {
            ChatModel model = resolveModel(request);
            ContentOptimizer optimizer = AiServices.builder(ContentOptimizer.class)
                    .chatModel(model)
                    .build();
            return optimizer.optimize(instruction, content);
        } catch (Exception e) {
            log.warn("使用指定模型失败，尝试使用备用优化策略: {}", e.getMessage());
            return fallbackOptimization(content);
        }
    }

    /**
     * 解析请求到具体的 {@link ChatModel}：
     * <ol>
     * <li>请求指定 modelId + modelType=OLLAMA →
     * {@link OllamaClient#chatModel(String)}</li>
     * <li>请求指定 modelId + OpenAI 兼容 + 完整 baseUrl/apiKey →
     * {@link OpenAiModelFactory#chatModel}</li>
     * <li>请求指定 modelId + OpenAI 兼容 + 缺配置 → 查数据库补全后走 OpenAiModelFactory</li>
     * <li>请求未指定 modelId → 查数据库（命中则按类型路由）</li>
     * <li>都未命中 → 默认 Ollama 模型</li>
     * </ol>
     */
    private ChatModel resolveModel(ContentOptimizationRequest request) {
        String modelId = request.getModelId();
        String modelType = request.getModelType();
        String baseUrl = request.getBaseUrl();
        String apiKey = request.getApiKey();

        if (modelId != null && !modelId.isEmpty()) {
            log.info("使用请求指定的模型: {}, 类型: {}", modelId, modelType);

            if (isOllamaType(modelType)) {
                return ollamaClient.chatModel(modelId);
            }

            // OpenAI 兼容类型：缺 baseUrl/apiKey 时尝试从数据库补全
            if ((baseUrl == null || baseUrl.isEmpty()) || (apiKey == null || apiKey.isEmpty())) {
                log.info("请求缺少 baseUrl 或 apiKey，尝试从数据库获取配置");
                ModelConfig customConfig = modelConfigService.getConfigByModelId(modelId);
                if (customConfig != null) {
                    log.info("使用数据库配置的模型: {}", customConfig.getModelId());
                    String actualModelId = extractModelId(customConfig.getModelId(), customConfig.getName());
                    return openAiModelFactory.chatModel(
                            customConfig.getBaseUrl(), customConfig.getApiKey(), actualModelId);
                }
                log.warn("数据库中未找到模型配置: {}", modelId);
            }

            // 使用请求中提供的配置或默认值
            String actualBaseUrl = baseUrl != null && !baseUrl.isEmpty() ? baseUrl
                    : "https://api.openai.com/v1";
            String actualApiKey = apiKey != null && !apiKey.isEmpty() ? apiKey : "";
            log.info("使用请求配置或默认配置: baseUrl={}", actualBaseUrl);
            return openAiModelFactory.chatModel(actualBaseUrl, actualApiKey, modelId);
        }

        // 请求未指定 modelId，尝试从数据库获取用户配置的模型
        if (modelId != null) {
            log.info("请求未指定模型，尝试从数据库获取配置");
            ModelConfig customConfig = modelConfigService.getConfigByModelId(modelId);
            if (customConfig != null) {
                log.info("使用数据库配置的模型: {}", customConfig.getModelId());
                if (customConfig.getType() == ModelType.OLLAMA) {
                    return ollamaClient.chatModel(customConfig.getModelId());
                }
                String actualModelId = extractModelId(customConfig.getModelId(), customConfig.getName());
                return openAiModelFactory.chatModel(
                        customConfig.getBaseUrl(), customConfig.getApiKey(), actualModelId);
            }
        }

        // 使用默认模型
        log.info("使用默认模型: {}", defaultModel);
        return ollamaClient.chatModel(defaultModel);
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
     * 根据 optimizationType 拼装优化指令，作为 {@link ContentOptimizer#optimize} 的 instruction
     * 参数。
     * 替代原先手写的 buildSystemPrompt。
     */
    private String buildInstruction(String optimizationType) {
        if (optimizationType == null || optimizationType.isEmpty() || "all".equalsIgnoreCase(optimizationType)) {
            return "进行语法纠错、语义优化、格式规范和风格提升";
        }
        return switch (optimizationType.toLowerCase()) {
            case "grammar" -> "进行语法纠错";
            case "semantic" -> "进行语义优化";
            case "format" -> "进行格式规范";
            case "keyword" -> "进行关键词强化";
            default -> "进行全面优化";
        };
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
