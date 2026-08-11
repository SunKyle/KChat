package com.example.app.pipeline.stage.execution;

import com.example.app.client.OllamaClient;
import com.example.app.client.OpenAICompatibleClient;
import com.example.app.config.ModelCapability;
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
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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
     * Agent 模式执行路径：使用 StreamingChatModel.chat() + StreamingChatResponseHandler
     * 实现真正的流式输出，同时通过 CountDownLatch 保持同步语义供 Agent 循环使用。
     *
     * LangChain4j 1.4.0 已支持流式 + tool，onPartialResponse 推送 token 到 SSE，
     * onCompleteResponse 交付完整 AiMessage（含 toolExecutionRequests）。
     * 返回的 AiMessage 存入 ctx.agentState，由 ToolCallDetectionStage 解析工具调用，
     * 由 ToolResultAssemblyStage 回填到 assembledMessages 供下一轮调用。
     */
    @SuppressWarnings("unchecked")
    private void executeWithTools(ConversationContext ctx) {
        String model = ctx.getModel();
        logFinalPrompt(ctx, model);

        boolean isStreaming = ctx.isStreaming();
        List<ToolSpecification> toolSpecs = resolveToolSpecifications(ctx);

        List<ChatMessage> messages = ctx.getAssembledMessages();
        if (messages == null || messages.isEmpty()) {
            log.warn("[ModelRouting][Agent] No assembled messages, skipping LLM call");
            ctx.setLlmResponse("");
            return;
        }

        // Agent 模式下附加用户上传的图片到最后一条 UserMessage。
        // 仅在第一轮迭代附加：后续轮次的消息是工具执行结果（ToolExecutionResultMessage），
        // 不应携带图片；且图片已在第一轮被模型"看到"，无需重复发送。
        // attachImagesToLastUserMessage 返回新列表，不修改 ctx.assembledMessages，
        // 避免污染上下文导致下一轮重复附加。
        if (ctx.getCurrentIteration() == 0) {
            List<String> imageUrls = ctx.getImageUrls();
            if (imageUrls != null && !imageUrls.isEmpty()) {
                boolean hasVision = modelConfigService.getCapabilities(model)
                        .contains(ModelCapability.IMAGE_IN);
                if (hasVision) {
                    int before = messages.size();
                    messages = openAICompatibleClient.attachImagesToLastUserMessage(messages, imageUrls);
                    log.info("[ModelRouting][Agent] Attached {} image(s) to last user message (messages: {} -> {})",
                            imageUrls.size(), before, messages.size());
                } else {
                    log.warn("[ModelRouting][Agent] Model {} does NOT support IMAGE_IN, skipping image attachment. "
                            + "User uploaded {} image(s) but they will be invisible to the model.",
                            model, imageUrls.size());
                    messages = annotateMissingVisionInUserMessage(messages, imageUrls, true);
                }
            }
        }

        log.info("[ModelRouting][Agent] Calling LLM with {} message(s) and {} tool spec(s), iteration {}",
                messages.size(), toolSpecs.size(), ctx.getCurrentIteration());

        // 推送 Agent 思考过程：LLM 调用开始（携带本轮上下文概要，供前端展示）
        Map<String, Object> callData = new LinkedHashMap<>();
        callData.put("model", model);
        callData.put("messageCount", messages.size());
        callData.put("toolSpecCount", toolSpecs.size());
        callData.put("toolSpecNames", toolSpecs.stream().map(ToolSpecification::name).toList());
        callData.put("executedToolNames", ctx.getToolResults().stream()
                .map(ConversationContext.ToolResultRecord::toolName).toList());
        callData.put("inputPreview", extractInputPreview(messages));
        callData.put("tokenCount", ctx.getTokenCount());
        callData.put("truncated", ctx.isTruncated());
        ctx.emitAgentThinking("llm_call", callData);

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .toolSpecifications(toolSpecs)
                .build();

        if (isStreaming) {
            executeWithToolsStreaming(ctx, model, chatRequest);
        } else {
            executeWithToolsSync(ctx, model, chatRequest);
        }
    }

    /**
     * 同步路径：使用 ChatModel.chat()（兼容非流式 Agent 调用）
     */
    private void executeWithToolsSync(ConversationContext ctx, String model, ChatRequest chatRequest) {
        ChatModel chatModel = aiServiceFactory.getChatModel(model);
        ChatResponse response = chatModel.chat(chatRequest);
        AiMessage aiMessage = response.aiMessage();
        storeAiMessage(ctx, aiMessage);
    }

    /**
     * 流式路径：使用 StreamingChatModel.chat() + StreamingChatResponseHandler
     * onPartialResponse 实时推送 token 到 SSE，onCompleteResponse 交付完整 AiMessage。
     * 通过 CountDownLatch 保持同步语义，确保 Agent 循环按序执行。
     */
    private void executeWithToolsStreaming(ConversationContext ctx, String model, ChatRequest chatRequest) {
        StreamingChatModel streamingModel = aiServiceFactory.getStreamingChatModel(model);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicReference<AiMessage> aiMessageRef = new AtomicReference<>();
        StringBuilder fullResponse = new StringBuilder();
        SseEmitter emitter = (SseEmitter) ctx.getSseEmitter();

        streamingModel.chat(chatRequest, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                if (partialResponse == null || partialResponse.isEmpty()) {
                    return;
                }
                fullResponse.append(partialResponse);
                if (emitter != null) {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("message")
                                .data("{\"content\": \"" + JsonUtils.escapeJson(partialResponse) + "\"}"));
                    } catch (Exception e) {
                        log.debug("Failed to send agent streaming SSE: {}", e.getMessage());
                    }
                }
            }

            @Override
            public void onCompleteResponse(ChatResponse chatResponse) {
                AiMessage aiMessage = chatResponse.aiMessage();
                aiMessageRef.set(aiMessage);
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                log.error("[ModelRouting][Agent] Streaming error: {}", error.getMessage());
                errorRef.set(error);
                latch.countDown();
            }
        });

        try {
            if (!latch.await(10, TimeUnit.MINUTES)) {
                throw new RuntimeException("Agent streaming timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Agent streaming interrupted", e);
        }

        Throwable error = errorRef.get();
        if (error != null) {
            throw new RuntimeException("Agent streaming failed", error);
        }

        AiMessage aiMessage = aiMessageRef.get();
        if (aiMessage == null) {
            log.warn("[ModelRouting][Agent] No AI message received from streaming");
            ctx.setLlmResponse("");
            return;
        }

        // 推送最终响应（如果有完整文本且已通过 partial 推送过，可跳过）
        String fullText = fullResponse.toString();
        if (!fullText.isEmpty() && emitter != null) {
            // 文本已通过 partial 推送，此处不再重复推送
        }

        storeAiMessage(ctx, aiMessage);
    }

    /**
     * 存储 AiMessage 到 agentState 并更新 llmResponse
     */
    private void storeAiMessage(ConversationContext ctx, AiMessage aiMessage) {
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
            // 推送 Agent 思考过程：LLM 返回最终文本回复（无工具调用 → 循环将结束）
            Map<String, Object> finalData = new LinkedHashMap<>();
            finalData.put("text", text != null ? text : "");
            finalData.put("length", text != null ? text.length() : 0);
            ctx.emitAgentThinking("final_response", finalData);
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
            String text = null;
            if (msg instanceof dev.langchain4j.data.message.SystemMessage sysMsg) {
                role = "SYSTEM";
                text = sysMsg.text();
            } else if (msg instanceof dev.langchain4j.data.message.UserMessage userMsg) {
                role = "USER";
                text = userMsg.singleText();
            } else if (msg instanceof dev.langchain4j.data.message.AiMessage aiMsg) {
                role = "AI";
                text = aiMsg.text();
            } else {
                role = "UNKNOWN";
            }
            sb.append("║  [").append(i + 1).append("/").append(messages.size())
                    .append("] ").append(role).append(":\n");
            if (text == null) {
                // AiMessage 可能只有 toolExecutionRequests 而 text 为 null
                sb.append("║  ").append("[(no text content)]").append("\n");
            } else {
                sb.append("║  ").append(text.replace("\n", "\n║  ")).append("\n");
            }
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
            List<ChatMessage> messages = resolveMessagesForImageAwareRequest(
                    ctx.getAssembledMessages(), ctx.getImageUrls(), modelId);
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
            // capability 前置过滤：模型不具备 IMAGE_IN 时不传 imageUrls，
            // 改为在 UserMessage 追加纯文本提示，避免 LangChain4j 序列化出
            // "image_url" content 导致纯文本模型 API 拒绝请求。
            List<ChatMessage> messages = ctx.getAssembledMessages();
            List<String> imageUrls = ctx.getImageUrls();
            boolean hasVision = modelConfigService.getCapabilities(
                    config.getName() + ":" + actualModelId).contains(ModelCapability.IMAGE_IN);
            List<String> effectiveImageUrls = hasVision ? imageUrls : List.of();
            List<ChatMessage> effectiveMessages = messages;
            if (!hasVision && imageUrls != null && !imageUrls.isEmpty()) {
                log.warn("[ModelRouting] Model {}:{} does NOT support IMAGE_IN, skipping {} image(s)",
                        config.getName(), actualModelId, imageUrls.size());
                effectiveMessages = annotateMissingVisionInUserMessage(messages, imageUrls, false);
            }
            openAICompatibleClient.streamChatCompletion(
                    actualModelId, config.getBaseUrl(), config.getApiKey(),
                    effectiveMessages, effectiveImageUrls, emitter,
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
        List<String> imageUrls = ctx.getImageUrls();
        boolean hasVision = modelConfigService.getCapabilities(model)
                .contains(ModelCapability.IMAGE_IN);
        List<ChatMessage> effectiveMessages = messages;
        if (!hasVision && imageUrls != null && !imageUrls.isEmpty()) {
            log.warn("[ModelRouting] Ollama model {} does NOT support IMAGE_IN, skipping {} image(s)",
                    model, imageUrls.size());
            effectiveMessages = annotateMissingVisionInUserMessage(messages, imageUrls, false);
        }
        List<String> effectiveImageUrls = hasVision ? imageUrls : List.of();

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
                if (effectiveImageUrls != null && !effectiveImageUrls.isEmpty()) {
                    ollamaClient.streamGenerateWithImages(effectiveMessages, effectiveImageUrls,
                            callback, model);
                } else {
                    ollamaClient.streamGenerate(effectiveMessages, callback, model);
                }
                ctx.setLlmResponse(fullResponse.toString());
                pipelineExecutor.executePostProcessing(ctx);
            } catch (Exception e) {
                log.error("Ollama streaming failed: {}", e.getMessage());
                failStreaming(ctx, emitter, "Ollama请求失败: " + e.getMessage());
            }
        } else {
            String response;
            if (effectiveImageUrls != null && !effectiveImageUrls.isEmpty()) {
                response = ollamaClient.generateWithImages(effectiveMessages, effectiveImageUrls, model);
            } else {
                response = ollamaClient.generate(effectiveMessages, model);
            }
            ctx.setLlmResponse(response);
        }
    }

    /**
     * 按模型 IMAGE_IN capability 决定图片处理方式：
     * <ul>
     * <li>支持视觉：通过 {@link OpenAICompatibleClient#attachImagesToLastUserMessage}
     * 把 imageUrls 转为 ImageContent 附加到最后一条 UserMessage；
     * <li>不支持视觉：通过 {@link #annotateMissingVisionInUserMessage}
     * 在 UserMessage 末尾追加纯文本提示，且不再向外传递 imageUrls，
     * 避免 LangChain4j 序列化出 "image_url" content 被纯文本模型 API 拒绝。
     * </ul>
     *
     * <p>
     * 返回值设计为 messages 本身（已含图片或已追加注释），调用侧直接把
     * {@code null} / {@code List.of()} 作为 imageUrls 参数传入即可。
     */
    private List<ChatMessage> resolveMessagesForImageAwareRequest(
            List<ChatMessage> messages, List<String> imageUrls, String modelId) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return messages;
        }
        boolean hasVision = modelConfigService.getCapabilities(modelId)
                .contains(ModelCapability.IMAGE_IN);
        if (hasVision) {
            return openAICompatibleClient.attachImagesToLastUserMessage(messages, imageUrls);
        }
        log.warn("[ModelRouting] Model {} does NOT support IMAGE_IN, skipping {} image(s)",
                modelId, imageUrls.size());
        return annotateMissingVisionInUserMessage(messages, imageUrls, false);
    }

    /**
     * 提取本轮 LLM 输入的简短预览（最后一条非系统消息的文本，最多 120 字符），
     * 供 Agent 思考面板展示"LLM 此刻在基于什么内容思考"。
     *
     * <p>第一轮通常是用户消息；后续轮次则是上一条工具执行结果消息，
     * 能直观看到模型拿到工具返回后继续推理。
     */
    private String extractInputPreview(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            String text = null;
            if (msg instanceof dev.langchain4j.data.message.UserMessage userMsg) {
                text = userMsg.singleText();
            } else if (msg instanceof dev.langchain4j.data.message.ToolExecutionResultMessage toolMsg) {
                text = toolMsg.text();
            }
            if (text != null && !text.isBlank()) {
                text = text.replaceAll("\\s+", " ").trim();
                return text.length() > 120 ? text.substring(0, 120) + "…" : text;
            }
        }
        return null;
    }

    /**
     * 当模型不具备 IMAGE_IN 能力但用户上传了图片时，在最后一条 UserMessage 末尾追加
     * 一段纯文本注释。
     *
     * <p>
     * 行为分两种模式：
     * <ul>
     * <li><b>Agent 模式</b>（{@code suggestTool=true}）：列出图片 URL 并提示 LLM
     * 调用 {@code analyzeImage} 工具识别图片内容，让纯文本主模型也能通过
     * function calling 间接获得视觉能力。</li>
     * <li><b>非 Agent 模式</b>：提示模型告知用户切换到支持视觉的模型，
     * 不要假装看到了图片。</li>
     * </ul>
     *
     * <p>
     * 返回新列表，不修改入参（与 attachImagesToLastUserMessage 保持一致的不可变语义）。
     */
    private List<ChatMessage> annotateMissingVisionInUserMessage(List<ChatMessage> messages,
            List<String> imageUrls, boolean suggestTool) {
        if (messages == null || messages.isEmpty() || imageUrls == null || imageUrls.isEmpty()) {
            return messages;
        }
        int lastUserIdx = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage) {
                lastUserIdx = i;
                break;
            }
        }
        if (lastUserIdx == -1) {
            return messages;
        }

        UserMessage original = (UserMessage) messages.get(lastUserIdx);
        String originalText = original.singleText();

        StringBuilder note = new StringBuilder("\n\n[系统提示：用户上传了 ")
                .append(imageUrls.size())
                .append(" 张图片，但你当前使用的模型不具备视觉（IMAGE_IN）能力，")
                .append("无法直接看到图片内容。");

        if (suggestTool) {
            note.append("你可以调用 analyzeImage 工具来识别图片内容，")
                    .append("工具会委托具备视觉能力的模型分析图片并返回文本描述。")
                    .append("调用时把下方 URL 作为 imageUrl 参数传入，")
                    .append("把用户的具体问题作为 question 参数传入。")
                    .append("图片 URL 列表：");
            for (int i = 0; i < imageUrls.size(); i++) {
                note.append("\n  ").append(i + 1).append(". ").append(imageUrls.get(i));
            }
        } else {
            note.append("请告知用户切换到支持视觉的模型（如 gpt-4o、llava、qwen2.5-vl 等）后重试。");
        }
        note.append("不要假装自己看到了图片。]");

        String newText = (originalText == null ? "" : originalText) + note;
        UserMessage replacement = UserMessage.from(newText);
        List<ChatMessage> result = new java.util.ArrayList<>(messages);
        result.set(lastUserIdx, replacement);
        return result;
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
