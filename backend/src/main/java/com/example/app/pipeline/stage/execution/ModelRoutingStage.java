package com.example.app.pipeline.stage.execution;

import com.example.app.client.OllamaClient;
import com.example.app.client.OpenAICompatibleClient;
import com.example.app.entity.ModelConfig;
import com.example.app.pipeline.ContextPipelineExecutor;
import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.service.ModelConfigService;
import com.example.app.util.JsonUtils;
import dev.langchain4j.data.message.ChatMessage;
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

    public ModelRoutingStage(ModelConfigService modelConfigService,
            OllamaClient ollamaClient,
            OpenAICompatibleClient openAICompatibleClient,
            @Lazy ContextPipelineExecutor pipelineExecutor) {
        this.modelConfigService = modelConfigService;
        this.ollamaClient = ollamaClient;
        this.openAICompatibleClient = openAICompatibleClient;
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
        return !ctx.isMultimodal();
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
                        ? OpenAICompatibleClient.STREAM_CHAT_TEMPERATURE
                        : OpenAICompatibleClient.SYNC_CHAT_TEMPERATURE)
                .append("  |  MaxTokens: ").append(OpenAICompatibleClient.DEFAULT_CHAT_MAX_TOKENS)
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
