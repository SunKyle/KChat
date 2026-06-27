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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
@Slf4j
public class ModelRoutingStage implements ContextPipelineStage {

    private final ModelConfigService modelConfigService;
    private final OllamaClient ollamaClient;
    private final OpenAICompatibleClient openAICompatibleClient;
    private final ContextPipelineExecutor pipelineExecutor;

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
    public String getName() {
        return "modelRoutingStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        String model = ctx.getModel();
        ModelConfig customConfig = modelConfigService.getConfigByModelId(model);

        if (customConfig != null) {
            executeCustomModel(ctx, customConfig, model);
        } else {
            executeOllama(ctx, model);
        }
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
            String systemPrompt = buildSyncSystemPrompt(ctx);
            String response = openAICompatibleClient.chatCompletion(
                    actualModelId, config.getBaseUrl(), config.getApiKey(),
                    systemPrompt, ctx.getUserMessage());
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

        openAICompatibleClient.streamChatCompletion(
                actualModelId, config.getBaseUrl(), config.getApiKey(),
                ctx.getUserMessage(), ctx.getImageUrls(), emitter,
                chunk -> fullResponse.append(chunk),
                () -> {
                    ctx.setLlmResponse(fullResponse.toString());
                    pipelineExecutor.executePostProcessing(ctx);
                });
    }

    private void executeOllama(ConversationContext ctx, String model) {
        List<ChatMessage> messages = ctx.getAssembledMessages();

        if (ctx.isStreaming()) {
            SseEmitter emitter = (SseEmitter) ctx.getSseEmitter();
            StringBuilder fullResponse = new StringBuilder();
            boolean[] completed = {false};

            Consumer<String> callback = chunk -> {
                if (completed[0]) return;
                fullResponse.append(chunk);
                try {
                    emitter.send(SseEmitter.event().name("message")
                            .data("{\"content\": \"" + JsonUtils.escapeJson(chunk) + "\"}"));
                } catch (Exception e) {
                    completed[0] = true;
                }
            };

            if (ctx.getImageUrls() != null && !ctx.getImageUrls().isEmpty()) {
                ollamaClient.streamGenerateWithImages(messages, ctx.getImageUrls(), callback, model);
            } else {
                ollamaClient.streamGenerate(messages, callback, model);
            }
            ctx.setLlmResponse(fullResponse.toString());
            // Ollama streaming is synchronous — post-processing runs after stream completes
            pipelineExecutor.executePostProcessing(ctx);
        } else {
            String response = ollamaClient.generate(messages, model);
            ctx.setLlmResponse(response);
        }
    }

    private String buildSyncSystemPrompt(ConversationContext ctx) {
        String languageClause = "";
        if (ctx.getLanguage() != null && !ctx.getLanguage().isBlank()) {
            String languageName = LANGUAGE_NAMES.getOrDefault(ctx.getLanguage(), ctx.getLanguage());
            languageClause = languageName;
        }
        return "You are a helpful assistant. Answer the user's question in a friendly and natural way.\n"
                + (!languageClause.isEmpty() ? "Please respond in " + languageClause + "." : "");
    }

    @Override
    public int getOrder() {
        return 500;
    }
}
