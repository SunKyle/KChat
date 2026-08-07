package com.example.app.pipeline.stage.execution;

import com.example.app.client.OllamaClient;
import com.example.app.client.OpenAICompatibleClient;
import com.example.app.config.ModelCapability;
import com.example.app.config.MultimodalProperties;
import com.example.app.dto.MultimodalArtifact;
import com.example.app.dto.MultimodalConfigDTO;
import com.example.app.dto.MultimodalPlanStep;
import com.example.app.entity.ModelConfig;
import com.example.app.pipeline.ContextPipelineExecutor;
import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.service.ModelConfigService;
import com.example.app.service.MultimodalConfigService;
import com.example.app.util.JsonUtils;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class MultimodalExecutionStage implements ContextPipelineStage {

    private static final org.slf4j.Logger promptLog =
            org.slf4j.LoggerFactory.getLogger("PROMPT_LOG");

    private final MultimodalProperties properties;
    private final ModelConfigService modelConfigService;
    private final OpenAICompatibleClient openAICompatibleClient;
    private final OllamaClient ollamaClient;
    private final ContextPipelineExecutor pipelineExecutor;
    private final MultimodalConfigService multimodalConfigService;

    public MultimodalExecutionStage(MultimodalProperties properties,
            ModelConfigService modelConfigService,
            OpenAICompatibleClient openAICompatibleClient,
            OllamaClient ollamaClient,
            @Lazy ContextPipelineExecutor pipelineExecutor,
            MultimodalConfigService multimodalConfigService) {
        this.properties = properties;
        this.modelConfigService = modelConfigService;
        this.openAICompatibleClient = openAICompatibleClient;
        this.ollamaClient = ollamaClient;
        this.pipelineExecutor = pipelineExecutor;
        this.multimodalConfigService = multimodalConfigService;
    }

    @Override
    public Phase getPhase() {
        return Phase.EXECUTION;
    }

    public String getName() {
        return "multimodalExecutionStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        MultimodalConfigDTO config = multimodalConfigService.getByUserId(ctx.getUserId());
        logFinalPrompt(ctx);
        if (ctx.isStreaming()) {
            executeStreaming(ctx, config);
            pipelineExecutor.executePostProcessing(ctx);
            return;
        }
        executeSync(ctx, config);
    }

    private void executeSync(ConversationContext ctx, MultimodalConfigDTO config) {
        List<MultimodalPlanStep> steps = ctx.getMultimodalPlan();
        if (steps == null || steps.isEmpty()) {
            steps = List.of(new MultimodalPlanStep("text", null, ctx.getUserMessage(), null));
        }

        StringBuilder response = new StringBuilder();
        StringBuilder visionSummary = new StringBuilder();
        List<MultimodalArtifact> artifacts = new ArrayList<>();

        for (MultimodalPlanStep step : steps) {
            try {
                switch (step.type() == null ? "" : step.type()) {
                    case "vision" -> {
                        String visionText = runVision(ctx, step, config);
                        response.append(visionText).append("\n\n");
                        if (!visionText.isBlank()) {
                            visionSummary.append(visionText).append("\n");
                        }
                    }
                    case "image_gen" -> runImageGen(ctx, step, response, artifacts, config);
                    default -> {
                        MultimodalPlanStep textStep = withVisionContext(step, visionSummary, ctx);
                        response.append(runText(ctx, textStep, config)).append("\n\n");
                    }
                }
            } catch (Exception e) {
                log.warn("[MultimodalExecution] step {} failed: {}", step.type(), e.getMessage());
            }
        }

        ctx.setLlmResponse(response.toString().trim());
        ctx.setArtifacts(artifacts);
    }

    private void logFinalPrompt(ConversationContext ctx) {
        List<dev.langchain4j.data.message.ChatMessage> messages = ctx.getAssembledMessages();
        if (messages == null || messages.isEmpty()) {
            promptLog.info("║  [Multimodal] Final Prompt → Model: AUTO (multimodal), no assembled messages");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("╔═══════════════════════════════════════════════════════════╗\n");
        sb.append("║  [Multimodal] Final Prompt → Model: AUTO (multimodal)\n");
        sb.append("║  Messages: ").append(messages.size())
                .append("  |  Tokens: ").append(ctx.getTokenCount())
                .append("  |  Truncated: ").append(ctx.isTruncated()).append("\n");
        sb.append("╠═══════════════════════════════════════════════════════════╣\n");

        for (int i = 0; i < messages.size(); i++) {
            dev.langchain4j.data.message.ChatMessage msg = messages.get(i);
            String role;
            if (msg instanceof dev.langchain4j.data.message.SystemMessage) {
                role = "SYSTEM";
            } else if (msg instanceof dev.langchain4j.data.message.UserMessage) {
                role = "USER";
            } else {
                role = "AI";
            }
            sb.append("║  [").append(i + 1).append("/").append(messages.size())
                    .append("] ").append(role).append(":\n");
            sb.append("║  ").append(msg.text().replace("\n", "\n║  ")).append("\n");
        }

        if (ctx.getMultimodalPlan() != null) {
            sb.append("╠═══════════════════════════════════════════════════════════╣\n");
            sb.append("║  Multimodal Plan: ").append(JsonUtils.toJson(ctx.getMultimodalPlan())).append("\n");
        }
        sb.append("╚═══════════════════════════════════════════════════════════╝");
        promptLog.info(sb.toString());
    }

    private void executeStreaming(ConversationContext ctx, MultimodalConfigDTO config) {
        List<MultimodalPlanStep> steps = ctx.getMultimodalPlan();
        if (steps == null || steps.isEmpty()) {
            steps = List.of(new MultimodalPlanStep("text", null, ctx.getUserMessage(), null));
        }

        StringBuilder response = new StringBuilder();
        StringBuilder visionSummary = new StringBuilder();
        List<MultimodalArtifact> artifacts = new ArrayList<>();

        for (MultimodalPlanStep step : steps) {
            try {
                switch (step.type() == null ? "" : step.type()) {
                    case "vision" -> {
                        String visionText = streamVision(ctx, step, response, config);
                        if (!visionText.isBlank()) {
                            visionSummary.append(visionText).append("\n");
                        }
                    }
                    case "image_gen" -> streamImageGen(ctx, step, response, artifacts, config);
                    default -> streamText(ctx, withVisionContext(step, visionSummary, ctx), response, config);
                }
            } catch (Exception e) {
                log.warn("[MultimodalExecution] step {} failed: {}", step.type(), e.getMessage());
            }
        }

        ctx.setLlmResponse(response.toString().trim());
        ctx.setArtifacts(artifacts);
    }

    private String streamVision(ConversationContext ctx, MultimodalPlanStep step,
            StringBuilder response, MultimodalConfigDTO config) throws Exception {
        String prompt = step.prompt() != null ? step.prompt() : ctx.getUserMessage();
        List<ChatMessage> messages = buildStepMessages(ctx, prompt);
        String model = resolveModel(
                firstNonBlank(config.getVisionModel(), properties.getVisionModel()),
                ModelCapability.IMAGE_IN);
        if (model == null) {
            model = findOllamaVisionModel();
        }
        List<String> images = ctx.getImageUrls();
        if (images == null || images.isEmpty()) {
            return "";
        }
        promptLog.info("[MultimodalExecution] Vision model={}, images={}", model, images.size());

        if (model == null) {
            String message = "未配置图片理解（Vision）模型，无法分析上传的图片";
            log.warn("[MultimodalExecution] {}", message);
            appendResponse(ctx, response, "【系统提示】" + message);
            return message;
        }

        ModelConfig modelConfig = model != null ? modelConfigService.getConfigByModelId(model) : null;
        if (modelConfig != null) {
            String text = openAICompatibleClient.chatCompletionWithImages(
                    extractModelId(model, modelConfig), modelConfig.getBaseUrl(), modelConfig.getApiKey(),
                    messages, images);
            appendResponse(ctx, response, text);
            return text;
        }

        StringBuilder collected = new StringBuilder();
        ollamaClient.streamGenerateWithImages(
                messages, images,
                chunk -> {
                    collected.append(chunk);
                    appendResponse(ctx, response, chunk);
                }, model);
        return collected.toString();
    }

    private void streamText(ConversationContext ctx, MultimodalPlanStep step,
            StringBuilder response, MultimodalConfigDTO config) throws Exception {
        String text = step.text() != null ? step.text() : ctx.getUserMessage();
        List<ChatMessage> messages = buildStepMessages(ctx, text);
        String model = firstNonBlank(config.getTextModel(), properties.getTextModel());
        if (model == null || model.isBlank()) {
            model = modelConfigService.findDefaultTextModelId();
        }

        if (model == null) {
            ollamaClient.streamGenerate(
                    messages,
                    chunk -> appendResponse(ctx, response, chunk), null);
            return;
        }

        ModelConfig modelConfig = modelConfigService.getConfigByModelId(model);
        if (modelConfig != null) {
            String textResponse = openAICompatibleClient.chatCompletion(
                    extractModelId(model, modelConfig), modelConfig.getBaseUrl(), modelConfig.getApiKey(),
                    messages);
            appendResponse(ctx, response, textResponse);
            return;
        }

        ollamaClient.streamGenerate(
                messages,
                chunk -> appendResponse(ctx, response, chunk), model);
    }

    private void streamImageGen(ConversationContext ctx, MultimodalPlanStep step,
            StringBuilder response, List<MultimodalArtifact> artifacts,
            MultimodalConfigDTO config) throws Exception {
        String prompt = step.prompt() != null ? step.prompt() : ctx.getUserMessage();
        String model = resolveModel(
                firstNonBlank(config.getImageModel(), properties.getImageModel()),
                ModelCapability.IMAGE_OUT);
        if (model == null) {
            log.warn("[MultimodalExecution] 未配置文生图模型，跳过 image_gen");
            return;
        }

        ModelConfig modelConfig = modelConfigService.getConfigByModelId(model);
        if (modelConfig == null) {
            log.warn("[MultimodalExecution] 文生图仅支持 OpenAI 兼容模型，当前模型: {}", model);
            return;
        }

        String imageUrl = openAICompatibleClient.generateImageSync(
                extractModelId(model, modelConfig), modelConfig.getBaseUrl(), modelConfig.getApiKey(),
                prompt, ctx.getImageUrls());
        ctx.emitSseEvent("image_done", "{\"url\": \"" + JsonUtils.escapeJson(imageUrl) + "\"}");
        response.append("![Generated Image](").append(imageUrl).append(")");
        artifacts.add(new MultimodalArtifact("image", imageUrl, prompt));
    }

    private void appendResponse(ConversationContext ctx, StringBuilder response, String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        response.append(chunk);
        ctx.emitSseEvent("message", "{\"content\": \"" + JsonUtils.escapeJson(chunk) + "\"}");
    }

    private String runVision(ConversationContext ctx, MultimodalPlanStep step,
            MultimodalConfigDTO config) throws Exception {
        String prompt = step.prompt() != null ? step.prompt() : ctx.getUserMessage();
        List<ChatMessage> messages = buildStepMessages(ctx, prompt);
        String model = resolveModel(
                firstNonBlank(config.getVisionModel(), properties.getVisionModel()),
                ModelCapability.IMAGE_IN);
        if (model == null) {
            model = findOllamaVisionModel();
        }
        List<String> images = ctx.getImageUrls();
        if (images == null || images.isEmpty()) {
            return "";
        }
        promptLog.info("[MultimodalExecution] Vision model={}, images={}", model, images.size());

        if (model == null) {
            String message = "未配置图片理解（Vision）模型，无法分析上传的图片";
            log.warn("[MultimodalExecution] {}", message);
            return "【系统提示】" + message;
        }

        ModelConfig modelConfig = modelConfigService.getConfigByModelId(model);
        if (modelConfig != null) {
            String actualModelId = extractModelId(model, modelConfig);
            return openAICompatibleClient.chatCompletionWithImages(
                    actualModelId, modelConfig.getBaseUrl(), modelConfig.getApiKey(),
                    messages, images);
        }
        return ollamaClient.generateWithImages(
                messages, images, model);
    }

    private String runText(ConversationContext ctx, MultimodalPlanStep step,
            MultimodalConfigDTO config) throws Exception {
        String text = step.text() != null ? step.text() : ctx.getUserMessage();
        List<ChatMessage> messages = buildStepMessages(ctx, text);
        String model = firstNonBlank(config.getTextModel(), properties.getTextModel());
        if (model == null || model.isBlank()) {
            model = modelConfigService.findDefaultTextModelId();
        }
        if (model == null) {
            return ollamaClient.generate(messages, null);
        }

        ModelConfig modelConfig = modelConfigService.getConfigByModelId(model);
        if (modelConfig != null) {
            return openAICompatibleClient.chatCompletion(
                    extractModelId(model, modelConfig), modelConfig.getBaseUrl(), modelConfig.getApiKey(),
                    messages);
        }
        return ollamaClient.generate(messages, model);
    }

    private List<ChatMessage> buildStepMessages(ConversationContext ctx, String stepText) {
        String content = stepText != null && !stepText.isBlank() ? stepText : ctx.getUserMessage();
        List<ChatMessage> messages = ctx.getAssembledMessages() == null
                ? new ArrayList<>()
                : new ArrayList<>(ctx.getAssembledMessages());

        if (content == null) {
            return messages;
        }

        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage) {
                messages.set(i, UserMessage.from(content));
                return messages;
            }
        }
        messages.add(UserMessage.from(content));
        return messages;
    }

    private MultimodalPlanStep withVisionContext(MultimodalPlanStep step,
            StringBuilder visionSummary, ConversationContext ctx) {
        if (visionSummary.length() == 0) {
            return step;
        }
        String text = step.text() != null ? step.text() : ctx.getUserMessage();
        String augmented = "[图片内容]\n" + visionSummary.toString().trim()
                + "\n\n用户问题：\n" + text;
        return new MultimodalPlanStep("text", null, augmented, null);
    }

    private void runImageGen(ConversationContext ctx, MultimodalPlanStep step,
            StringBuilder response, List<MultimodalArtifact> artifacts,
            MultimodalConfigDTO config) throws Exception {
        String prompt = step.prompt() != null ? step.prompt() : ctx.getUserMessage();
        String model = resolveModel(
                firstNonBlank(config.getImageModel(), properties.getImageModel()),
                ModelCapability.IMAGE_OUT);
        if (model == null) {
            log.warn("[MultimodalExecution] 未配置文生图模型，跳过 image_gen");
            return;
        }

        ModelConfig modelConfig = modelConfigService.getConfigByModelId(model);
        if (modelConfig == null) {
            log.warn("[MultimodalExecution] 文生图仅支持 OpenAI 兼容模型，当前模型: {}", model);
            return;
        }

        String imageUrl = openAICompatibleClient.generateImageSync(
                extractModelId(model, modelConfig), modelConfig.getBaseUrl(), modelConfig.getApiKey(),
                prompt, ctx.getImageUrls());
        response.append("![Generated Image](").append(imageUrl).append(")");
        artifacts.add(new MultimodalArtifact("image", imageUrl, prompt));
    }

    private String resolveModel(String configured, String capability) {
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        ModelConfig config = modelConfigService.findFirstModelWithCapability(capability);
        return config != null ? config.getName() + ":" + config.getModelId() : null;
    }

    private String findOllamaVisionModel() {
        try {
            return ollamaClient.listModels().stream()
                    .filter(name -> {
                        String lower = name.toLowerCase();
                        return lower.contains("llava")
                                || lower.contains("vision")
                                || lower.contains("minicpm")
                                || lower.contains("moondream")
                                || lower.contains("qwen2.5-vl")
                                || lower.contains("qwen2-vl")
                                || lower.contains("gemma3");
                    })
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.warn("[MultimodalExecution] Failed to discover Ollama vision model: {}", e.getMessage());
            return null;
        }
    }

    private String extractModelId(String fullModelId, ModelConfig config) {
        return fullModelId.startsWith(config.getName() + ":")
                ? fullModelId.substring(config.getName().length() + 1)
                : fullModelId;
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    @Override
    public boolean isApplicable(ConversationContext ctx) {
        return ctx.isMultimodal();
    }

    @Override
    public int getOrder() {
        return 510;
    }

    @Override
    public boolean isCritical() {
        return false;
    }
}
