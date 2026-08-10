package com.example.app.service.ai;

import com.example.app.client.OllamaClient;
import com.example.app.client.OpenAiModelFactory;
import com.example.app.entity.ModelConfig;
import com.example.app.service.ModelConfigService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AiServices 工厂
 *
 * 按 modelId 动态构建 LangChain4j {@link AiServices} 代理：
 * <ul>
 *   <li>命中 {@link ModelConfigService#getConfigByModelId(String)} 时走 OpenAI 兼容协议</li>
 *   <li>否则视为 Ollama 本地模型，复用 {@link OllamaClient#chatModel(String)} 缓存</li>
 *   <li>modelId 为空时回退到默认 {@link ChatLanguageModel} Bean</li>
 * </ul>
 *
 * 业务层只关心接口类型与 modelId，模型实例管理与 JSON Schema 注入由框架统一处理。
 */
@Service
@RequiredArgsConstructor
public class AiServiceFactory {

    private final ModelConfigService modelConfigService;
    private final OpenAiModelFactory openAiModelFactory;
    private final OllamaClient ollamaClient;
    private final ChatLanguageModel defaultChatModel;

    /**
     * 按 modelId 创建 AiServices 代理。
     *
     * @param aiServiceClass AI 接口类型（带 {@link dev.langchain4j.service.SystemMessage} /
     *                       {@link dev.langchain4j.service.UserMessage} 注解）
     * @param modelId        模型标识，可携带 {@code configName:} 前缀；为空时用默认模型
     * @param <T>            接口类型
     * @return AiServices 代理实例
     */
    public <T> T create(Class<T> aiServiceClass, String modelId) {
        return AiServices.builder(aiServiceClass)
                .chatLanguageModel(resolveModel(modelId))
                .build();
    }

    /**
     * 按 modelId 创建带工具的 AiServices 代理（Agent 模式使用）。
     *
     * @param aiServiceClass AI 接口类型
     * @param modelId        模型标识
     * @param tools          工具实例列表（含 @Tool 注解方法的对象）
     * @param <T>            接口类型
     * @return AiServices 代理实例（带工具调用能力）
     */
    public <T> T create(Class<T> aiServiceClass, String modelId, List<Object> tools) {
        AiServices<T> builder = AiServices.builder(aiServiceClass)
                .chatLanguageModel(resolveModel(modelId));
        if (tools != null && !tools.isEmpty()) {
            builder.tools(tools.toArray());
        }
        return builder.build();
    }

    /**
     * 解析 modelId 到具体的 {@link ChatLanguageModel}，供需要直接调用底层模型的场景使用
     * （如 ModelRoutingStage 在 Agent 模式下手动发起带 toolSpecifications 的调用）。
     */
    public ChatLanguageModel getChatLanguageModel(String modelId) {
        return resolveModel(modelId);
    }

    /**
     * 解析 modelId 到具体的 {@link ChatLanguageModel}。
     * 命中 ModelConfig 走 OpenAI 兼容协议；否则按 Ollama 模型处理；空值回退默认 Bean。
     */
    private ChatLanguageModel resolveModel(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return defaultChatModel;
        }

        ModelConfig config = modelConfigService.getConfigByModelId(modelId);
        if (config != null) {
            String actualId = modelId.startsWith(config.getName() + ":")
                    ? modelId.substring(config.getName().length() + 1)
                    : modelId;
            return openAiModelFactory.chatModel(config.getBaseUrl(), config.getApiKey(), actualId);
        }

        // 未命中自定义配置，按 Ollama 本地模型处理（复用 OllamaClient 缓存）
        return ollamaClient.chatModel(modelId);
    }
}
