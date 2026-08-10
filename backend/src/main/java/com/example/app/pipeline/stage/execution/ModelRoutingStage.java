package com.example.app.pipeline.stage.execution;

import com.example.app.client.OllamaClient;
import com.example.app.client.OpenAICompatibleClient;
import com.example.app.config.OpenAIClientProperties;
import com.example.app.entity.ModelConfig;
import com.example.app.pipeline.ContextPipelineExecutor;
import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.pipeline.stage.agent.ToolDefinitionStage;
import com.example.app.service.ModelConfigService;
import com.example.app.service.ai.AiServiceFactory;
import com.example.app.util.JsonUtils;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
@Slf4j
public class ModelRoutingStage implements ContextPipelineStage {

    private static final String PROMPT_LOGGER_NAME = "PROMPT_LOG";
    private static final org.slf4j.Logger promptLog = org.slf4j.LoggerFactory.getLogger(PROMPT_LOGGER_NAME);

    private final ModelConfigService modelConfigService;
    private final OllamaClient ollamaClient;
    private final OpenAICompatibleClient openAICompatibleClient;
    private final ContextPipelineExecutor pipelineExecutor;
    private final OpenAIClientProperties openAIClientProperties;
    private final AiServiceFactory aiServiceFactory;

    public ModelRoutingStage(ModelConfigService modelConfigService,
            OllamaClient ollamaClient,
            OpenAICompatibleClient openAICompatibleClient,
            OpenAIClientProperties openAIClientProperties,
            AiServiceFactory aiServiceFactory,
            @Lazy ContextPipelineExecutor pipelineExecutor) {
        this.modelConfigService = modelConfigService;
        this.ollamaClient = ollamaClient;
        this.openAICompatibleClient = openAICompatibleClient;
        this.openAIClientProperties = openAIClientProperties;
        this.aiServiceFactory = aiServiceFactory;
        this.pipelineExecutor = pipelineExecutor;
    }

    private static final Map<String, String> LANGUAGE_NAMES = Map.ofEntries(
            Map.entry("zh-CN", "中文（简体）"),
            Map.entry("zh-TW", "中文（繁體）"),
            Map.entry("en", "English"),
            Map.entry("en-US", "English"),
            Map.entry("en-GB", "English"),
            Map.entry("ja", "日本語"),
            Map.entry("ko", "한국어"),
            Map.entry("fr", "Français"),
            Map.entry("de", "Deutsch"),
            Map.entry("es", "Español"),
            Map.entry("ru", "Русский"));

    @Override
    public Phase getPhase() {
        return Phase.EXECUTION;
    }

    public String getName() {
        return "modelRoutingStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        // Agent 模式：走 AiServices + 工具规格的同步调用，由 AGENT 阶段处理工具循环
        if (ctx.isAgentMode()) {
            executeWithTools(ctx);
            return;
        }

        String model = ctx.getModel();
        ModelConfig customConfig = modelConfigService.getConfigByModelId(model);

        logFinalPrompt(ctx, model);

        if (customConfig != null) {
            executeCustomModel(ctx, customConfig, model);
        } else {
            executeOllama(ctx, model);
        }
    }

    @Override
    public boolean isApplicable(ConversationContext ctx) {
        return true;
    }

    /**
     * Agent 模式执行路径：使用 ChatLanguageModel.generate(messages, toolSpecifications) 同步调用。
     *
     * LangChain4j 0.35 流式 + tool 不稳定，AGENT 模式强制同步。
     * 返回的 AiMessage 存入 ctx.agentState，由 ToolCallDetectionStage(610) 解析工具调用，
     * 由 ToolResultAssemblyStage(660) 回填到 assembledMessages 供下一轮调用。
     */
    @SuppressWarnings("unchecked")
    private void executeWithTools(ConversationContext ctx) {
        String model = ctx.getModel();
        logFinalPrompt(ctx, model);

        ChatLanguageModel chatModel = aiServiceFactory.getChatLanguageModel(model);
        List<ToolSpecification> toolSpecs = resolveToolSpecifications(ctx);

        List<ChatMessage> messages = ctx.getAssembledMessages();
        if (messages == null || messages.isEmpty()) {
            log.warn("[ModelRouting][Agent] No assembled messages, skipping LLM call");
            ctx.setLlmResponse("");
            return;
        }

        log.info("[ModelRouting][Agent] Calling LLM with {} message(s) and {} tool spec(s), iteration {}",
                messages.size(), toolSpecs.size(), ctx.getCurrentIteration());

        Response<AiMessage> response = chatModel.generate(messages, toolSpecs);
        AiMessage aiMessage = response.content();

        // 存入 agentState 供 ToolCallDetectionStage / ToolResultAssemblyStage 读取
        ctx.getAgentState().put(ConversationContext.KEY_LAST_AI_MESSAGE, aiMessage);

        // 更新 llmResponse（每轮覆盖，最终值为最后一轮的文本回复）
        String text = aiMessage.text();
        ctx.setLlmResponse(text != null ? text : "");

        if (aiMessage.hasToolExecutionRequests()) {
            log.info("[ModelRouting][Agent] LLM requested {} tool call(s)",
                    aiMessage.toolExecutionRequests().size());
        } else {
            log.info("[ModelRouting][Agent] LLM returned final text response (length={})",
                    text != null ? text.length() : 0);
        }
    }

    /** 从 ctx.agentState 读取 ToolDefinitionStage(480) 注入的工具规格列表 */
    private List<ToolSpecification> resolveToolSpecifications(ConversationContext ctx) {
        Object specs = ctx.getAgentState().get(ToolDefinitionStage.KEY_TOOL_SPECIFICATIONS);
        if (specs instanceof List<?>) {
            return (List<ToolSpecification>) specs;
        }
        return List.of();
    }

    private void logFinalPrompt(ConversationContext ctx, String model) {
        List<ChatMessage> messages = ctx.getAssembledMessages();
        if (messages == null || messages.isEmpty())
            return;

        Object templateVersion = ctx.getAgentState().get(ConversationContext.KEY_PROMPT_TEMPLATE_VERSION);
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("╔═══════════════════════════════════════════════════════════╗\n");
        sb.append("║  Final Prompt → Model: ").append(model)
                .append("  |  Temp: ").append(ctx.isStreaming()
                        ? String.valueOf(openAIClientProperties.getStreamTemperature())
                        : String.valueOf(openAIClientProperties.getSyncTemperature()))
                .append("  |  MaxTokens: ").append(openAIClientProperties.getDefaultMaxTokens())
                .append("  |  Template: ")
                .append(templateVersion instanceof Integer ? "v" + templateVersion : "fallback")
                .append("\n");
        sb.append("║  Messages: ").append(messages.size())
                .append("  |  Tokens: ").append(ctx.getTokenCount())
                .append("  |  Truncated: ").append(ctx.isTruncated()).append("\n");
        sb.append("╠═══════════════════════════════════════════════════════════╣\n");

        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            String role;
            if (msg instanceof dev.langchain4j.data.message.SystemMessage) {
                role = "SYSTEM";
            } else if (msg instanceof dev.langchain4j.data.message.UserMessage) {
                role = "USER";
            } else {
                role = "AI";
            }
            String text = msg.text();
            sb.append("║  [").append(i + 1).append("/").append(messages.size())
                    .append("] ").append(role).append(":\n");
            sb.append("║  ").append(text.replace("\n", "\n║  ")).append("\n");
        }
        sb.append("╚═══════════════════════════════════════════════════════════╝");

        promptLog.info(sb.toString());
    }

    private void executeCustomModel(ConversationContext ctx, ModelConfig config, String modelId) {
        String actualModelId = modelId.substring(config.getName().length() + 1);
        ctx.setCustomModelConfig(config);

        if (ctx.isStreaming()) {
            SseEmitter emitter = (SseEmitter) ctx.getSseEmitter();
            if (openAICompatibleClient.isImageModel(actualModelId)) {
                executeImageModel(ctx, config, actualModelId, emitter);
            } else {
                executeStreamingText(ctx, config, actualModelId, emitter);
            }
        } else {
            List<ChatMessage> messages = ctx.getAssembledMessages();
            String response = openAICompatibleClient.chatCompletion(
                    actualModelId, config.getBaseUrl(), config.getApiKey(), messages);
            ctx.setLlmResponse(response);
        }
    }

    private void executeImageModel(ConversationContext ctx, ModelConfig config,
            String actualModelId, SseEmitter emitter) {
        Consumer<String> onComplete = imageContent -> {
            ctx.setLlmResponse(imageContent);
            pipelineExecutor.executePostProcessing(ctx);
        };

        if (openAICompatibleClient.isStableDiffusionModel(actualModelId)) {
            openAICompatibleClient.generateImageSdWebui(
                    actualModelId, config.getBaseUrl(), config.getApiKey(),
                    ctx.getUserMessage(), ctx.getImageUrls(), emitter, onComplete);
        } else {
            openAICompatibleClient.generateImage(
                    actualModelId, config.getBaseUrl(), config.getApiKey(),
                    ctx.getUserMessage(), ctx.getImageUrls(), emitter, onComplete);
        }
    }

    private void executeStreamingText(ConversationContext ctx, ModelConfig config,
            String actualModelId, SseEmitter emitter) {
        StringBuilder fullResponse = new StringBuilder();
        try {
            List<ChatMessage> messages = ctx.getAssembledMessages();
            openAICompatibleClient.streamChatCompletion(
                    actualModelId, config.getBaseUrl(), config.getApiKey(),
                    messages, ctx.getImageUrls(), emitter,
                    chunk -> fullResponse.append(chunk),
                    () -> {
                        ctx.setLlmResponse(fullResponse.toString());
                        pipelineExecutor.executePostProcessing(ctx);
                    });
        } catch (Exception e) {
            log.error("Custom model streaming failed: {}", e.getMessage());
            failStreaming(ctx, emitter, "模型请求失败: " + e.getMessage());
        }
    }

    private void executeOllama(ConversationContext ctx, String model) {
        List<ChatMessage> messages = ctx.getAssembledMessages();

        if (ctx.isStreaming()) {
            SseEmitter emitter = (SseEmitter) ctx.getSseEmitter();
            StringBuilder fullResponse = new StringBuilder();
            boolean[] completed = { false };

            Consumer<String> callback = chunk -> {
                if (completed[0])
                    return;
                fullResponse.append(chunk);
                try {
                    emitter.send(SseEmitter.event().name("message")
                            .data("{\"content\": \"" + JsonUtils.escapeJson(chunk) + "\"}"));
                } catch (Exception e) {
                    completed[0] = true;
                }
            };

            try {
                if (ctx.getImageUrls() != null && !ctx.getImageUrls().isEmpty()) {
                    ollamaClient.streamGenerateWithImages(messages, ctx.getImageUrls(), callback, model);
                } else {
                    ollamaClient.streamGenerate(messages, callback, model);
                }
                ctx.setLlmResponse(fullResponse.toString());
                pipelineExecutor.executePostProcessing(ctx);
            } catch (Exception e) {
                log.error("Ollama streaming failed: {}", e.getMessage());
                failStreaming(ctx, emitter, "Ollama请求失败: " + e.getMessage());
            }
        } else {
            String response = ollamaClient.generate(messages, model);
            ctx.setLlmResponse(response);
        }
    }

    private void failStreaming(ConversationContext ctx, SseEmitter emitter, String message) {
        try {
            ctx.emitSseEvent("error", "{\"message\": \"" + JsonUtils.escapeJson(message) + "\"}");
            emitter.completeWithError(new RuntimeException(message));
        } catch (Exception ignored) {
            // emitter may already be closed
        }
    }

    @Override
    public int getOrder() {
        return 500;
    }
}
