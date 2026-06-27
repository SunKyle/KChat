package com.example.app.service;

import com.example.app.client.OllamaClient;
import com.example.app.client.OpenAICompatibleClient;
import com.example.app.config.WebSearchConfig;
import com.example.app.dto.ChatRequest;
import com.example.app.dto.MemoryDTO;
import com.example.app.dto.WebSearchResult;
import com.example.app.dto.WebSearchResult.SearchSnippet;
import com.example.app.entity.ModelConfig;
import com.example.app.service.WebSearchService;
import com.example.app.util.JsonUtils;
import dev.langchain4j.data.message.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * 流式响应服务，负责处理 SSE 流式消息传输
 * 
 * <功能说明>
 * - 核心职责：处理流式聊天请求，支持 Ollama 本地模型和自定义模型
 * - 设计模式：异步处理模式，使用 ExecutorService 异步处理请求
 * - 依赖关系：依赖 OllamaClient、OpenAICompatibleClient、ModelConfigService、ChatWorkflowService、MessagePersistenceService、AutoMemoryExtractor、ExecutorService
 * 
 * <支持的模型类型>
 * - Ollama 本地模型：通过 OllamaClient 调用
 * - 自定义模型：通过 OpenAICompatibleClient 调用，支持文本和图像生成
 * 
 * <执行流程>
 * 1. 创建 SSE Emitter
 * 2. 获取/创建对话 ID
 * 3. 更新短期记忆和保存用户消息
 * 4. 召回长期记忆
 * 5. 异步调用 LLM 生成响应
 * 6. 流式推送响应片段
 * 7. 完成后更新记忆和持久化消息
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StreamingService {

    /**
     * Ollama 客户端，用于调用本地 LLM 模型
     */
    private final OllamaClient ollamaClient;

    /**
     * OpenAI 兼容客户端，用于调用自定义模型
     */
    private final OpenAICompatibleClient openAICompatibleClient;

    /**
     * 模型配置服务，用于查询自定义模型配置
     */
    private final ModelConfigService modelConfigService;

    /**
     * 聊天流程编排服务，负责记忆管理和消息组装
     */
    private final ChatWorkflowService chatWorkflowService;

    /**
     * 消息持久化服务，负责消息保存
     */
    private final MessagePersistenceService messagePersistenceService;

    /**
     * 异步执行器，用于异步处理流式请求
     */
    private final ExecutorService executorService;

    /**
     * 自动记忆提取器，对话完成后自动提取记忆
     */
    private final AutoMemoryExtractor autoMemoryExtractor;

    /**
     * 用户配置服务，用于获取语言偏好
     */
    private final UserProfileService userProfileService;

    /**
     * 用户设置服务
     */
    private final UserSettingService userSettingService;

    /**
     * 对话服务
     */
    private final ConversationService conversationService;

    /**
     * 标题生成服务
     */
    private final TitleGenerationService titleGenerationService;

    /**
     * 网络搜索服务
     */
    private final WebSearchService webSearchService;

    /**
     * 网络搜索配置
     */
    private final WebSearchConfig webSearchConfig;

    /**
     * 处理流式聊天请求
     * 
     * @param request 聊天请求
     * @return SSE Emitter，用于流式推送响应
     */
    public SseEmitter streamResponse(ChatRequest request) {
        long startTime = System.currentTimeMillis();
        SseEmitter emitter = new SseEmitter(300000L);

        emitter.onCompletion(() -> log.info("[STREAM] SSE emitter completed"));
        emitter.onTimeout(() -> {
            log.warn("[STREAM] SSE emitter timeout");
            emitter.complete();
        });
        emitter.onError(e -> log.error("[STREAM] SSE emitter error: {}", e.getMessage()));

        String conversationId = chatWorkflowService.getOrCreateConversationId(request);
        final String finalConversationId = conversationId;
        final String userMessage = request.getMessage();
        final String aiMessageId = UUID.randomUUID().toString();
        final List<String> imageUrls = request.getImageUrls();
        final String userId = request.getUserId() != null ? request.getUserId() : "default";
        final String model = request.getModel();
        log.info("===================================================================================== ");
        log.info("[STREAM] ===== Start Processing Request ===== ");
        log.info("[STREAM] Conversation: {}, User: {}, Model: {}", conversationId, userId, model);
        log.info("[STREAM] User message length: {} chars", userMessage.length());

        chatWorkflowService.updateShortTermMemoryWithUserMessage(finalConversationId, userMessage);
        messagePersistenceService.saveUserMessage(finalConversationId, userMessage, imageUrls);

        final List<MemoryDTO> longTermMemory = chatWorkflowService.recallLongTermMemory(userId, userMessage, 5);
        log.info("[STREAM] Recalled {} long-term memory items", longTermMemory.size());

        final String userLanguage = userProfileService.getLanguage(userId);
        log.info("[STREAM] User language preference: {}", userLanguage);

        executorService.execute(() -> {
            StringBuilder fullResponse = new StringBuilder();
            final boolean[] completed = { false };

            try {
                long llmStartTime = System.currentTimeMillis();
                log.info("[STREAM] Starting LLM generation...");

                // 网络搜索
                String searchContext = null;
                WebSearchResult searchResultObj = null;
                if (request.isWebSearch() && webSearchConfig.isEnabled()) {
                    log.info("[STREAM] Web search enabled, querying: {}", userMessage);
                    try {
                        searchResultObj = webSearchService.search(userMessage);
                        if (searchResultObj.getSnippets() != null && !searchResultObj.getSnippets().isEmpty()) {
                            searchContext = searchResultObj.getSnippets().stream()
                                    .map(s -> "- [" + s.getTitle() + "](" + s.getUrl() + "): " + s.getSnippet())
                                    .collect(Collectors.joining("\n"));
                        }

                        // Always send search results to frontend
                        try {
                            String resultsJson = new com.fasterxml.jackson.databind.ObjectMapper()
                                    .writeValueAsString(searchResultObj);
                            emitter.send(SseEmitter.event().name("search_results")
                                    .data(resultsJson));
                        } catch (Exception e) {
                            log.warn("[STREAM] Failed to send search results: {}", e.getMessage());
                        }
                    } catch (Exception e) {
                        log.warn("[STREAM] Web search failed: {}", e.getMessage());
                        // Send error status to frontend
                        try {
                            WebSearchResult errorResult = WebSearchResult.builder()
                                    .query(userMessage)
                                    .snippets(List.of())
                                    .timestamp(System.currentTimeMillis())
                                    .status("error")
                                    .errorMessage(e.getMessage())
                                    .build();
                            String resultsJson = new com.fasterxml.jackson.databind.ObjectMapper()
                                    .writeValueAsString(errorResult);
                            emitter.send(SseEmitter.event().name("search_results")
                                    .data(resultsJson));
                        } catch (Exception ex) {
                            log.warn("[STREAM] Failed to send search error: {}", ex.getMessage());
                        }
                    }
                }

                ModelConfig customConfig = modelConfigService.getConfigByModelId(model);

                if (customConfig != null) {
                    String timeContext = searchContext != null
                            ? "\n当前时间：" + java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Shanghai"))
                                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss EEEE"))
                            : "";
                    String messageWithContext = searchContext != null
                            ? userMessage + timeContext + "\n\n[网络搜索上下文]\n" + searchContext
                            : userMessage;
                    handleCustomModel(customConfig, model, messageWithContext, imageUrls, emitter,
                            finalConversationId, aiMessageId, userId, fullResponse, completed, llmStartTime, startTime);
                    return;
                }

                List<ChatMessage> shortTermMemory = chatWorkflowService.getShortTermMemory(finalConversationId);
                List<ChatMessage> messages = chatWorkflowService.assembleMessages(
                        shortTermMemory, longTermMemory, userMessage, userLanguage, searchContext);

                boolean hasImages = imageUrls != null && !imageUrls.isEmpty();
                streamOllamaResponse(messages, imageUrls, model, emitter, fullResponse, completed);

                if (completed[0])
                    return;

                long llmEndTime = System.currentTimeMillis();
                log.info("[STREAM] LLM generation completed in {}ms", llmEndTime - llmStartTime);

                finalizeResponse(finalConversationId, fullResponse.toString(), aiMessageId,
                        userId, model, userMessage, emitter, llmStartTime, startTime);

            } catch (Exception e) {
                log.error("[STREAM] Error processing request", e);
                if (!completed[0]) {
                    try {
                        emitter.completeWithError(e);
                    } catch (Exception ignored) {
                    }
                }
            }
        });

        return emitter;
    }

    /**
     * 处理自定义模型请求
     * 
     * @param config 模型配置
     * @param modelId 模型 ID
     * @param userMessage 用户消息
     * @param imageUrls 图片 URL 列表
     * @param emitter SSE Emitter
     * @param conversationId 对话 ID
     * @param aiMessageId AI 消息 ID
     * @param userId 用户 ID
     * @param fullResponse 完整响应内容
     * @param completed 完成标志
     * @param llmStartTime LLM 开始时间
     * @param startTime 请求开始时间
     */
    private void handleCustomModel(ModelConfig config, String modelId, String userMessage,
            List<String> imageUrls, SseEmitter emitter, String conversationId, String aiMessageId,
            String userId, StringBuilder fullResponse, boolean[] completed, long llmStartTime, long startTime) {

        try {
            String actualModelId = modelId.substring(config.getName().length() + 1);

            if (openAICompatibleClient.isImageModel(actualModelId)) {
                log.info("[STREAM] Detected image generation model: {}", actualModelId);
                if (openAICompatibleClient.isStableDiffusionModel(actualModelId)) {
                    openAICompatibleClient.generateImageSdWebui(
                            actualModelId, config.getBaseUrl(), config.getApiKey(),
                            userMessage, imageUrls, emitter,
                            imageContent -> finalizeImageResponse(conversationId, imageContent, aiMessageId,
                                    emitter, llmStartTime, startTime));
                } else {
                    openAICompatibleClient.generateImage(
                            actualModelId, config.getBaseUrl(), config.getApiKey(),
                            userMessage, imageUrls, emitter,
                            imageContent -> finalizeImageResponse(conversationId, imageContent, aiMessageId,
                                    emitter, llmStartTime, startTime));
                }
            } else {
                openAICompatibleClient.streamChatCompletion(
                        actualModelId, config.getBaseUrl(), config.getApiKey(), userMessage,
                        imageUrls, emitter, fullResponse::append,
                        () -> finalizeResponse(conversationId, fullResponse.toString(), aiMessageId,
                                userId, modelId, userMessage, emitter, llmStartTime, startTime));
            }
        } catch (StringIndexOutOfBoundsException e) {
            log.error("[STREAM] Invalid model ID format: {}", modelId, e);
            emitter.completeWithError(new RuntimeException("无效的模型ID格式: " + modelId));
        } catch (Exception e) {
            log.error("[STREAM] Failed to process custom model request", e);
            if (!completed[0]) {
                try {
                    emitter.completeWithError(e);
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * 流式传输 Ollama 响应
     * 
     * @param messages 消息列表
     * @param imageUrls 图片 URL 列表
     * @param model 模型名称
     * @param emitter SSE Emitter
     * @param fullResponse 完整响应内容
     * @param completed 完成标志
     */
    private void streamOllamaResponse(List<ChatMessage> messages, List<String> imageUrls,
            String model, SseEmitter emitter, StringBuilder fullResponse, boolean[] completed) {

        if (imageUrls != null && !imageUrls.isEmpty()) {
            log.info("[STREAM] Streaming response with images...");
            ollamaClient.streamGenerateWithImages(messages, imageUrls, chunk -> {
                if (completed[0])
                    return;
                try {
                    fullResponse.append(chunk);
                    emitter.send(SseEmitter.event().name("message")
                            .data("{\"content\": \"" + JsonUtils.escapeJson(chunk) + "\"}"));
                } catch (Exception e) {
                    completed[0] = true;
                }
            }, model);
        } else {
            log.info("[STREAM] Streaming response...");
            ollamaClient.streamGenerate(messages, chunk -> {
                if (completed[0])
                    return;
                try {
                    fullResponse.append(chunk);
                    emitter.send(SseEmitter.event().name("message")
                            .data("{\"content\": \"" + JsonUtils.escapeJson(chunk) + "\"}"));
                } catch (Exception e) {
                    completed[0] = true;
                }
            }, model);
        }
    }

    /**
     * 完成文本响应处理
     * 
     * @param conversationId 对话 ID
     * @param content 响应内容
     * @param aiMessageId AI 消息 ID
     * @param userId 用户 ID
     * @param emitter SSE Emitter
     * @param llmStartTime LLM 开始时间
     * @param startTime 请求开始时间
     */
    private void finalizeResponse(String conversationId, String content, String aiMessageId,
            String userId, String model, String userMessage, SseEmitter emitter, long llmStartTime, long startTime) {
        try {
            long llmEndTime = System.currentTimeMillis();
            log.info("[STREAM] LLM generation completed in {}ms", llmEndTime - llmStartTime);

            chatWorkflowService.updateShortTermMemoryWithAiMessage(conversationId, content);
            messagePersistenceService.saveAiMessage(conversationId, aiMessageId, content);
            log.info("[STREAM] AI response saved: {} chars", content.length());

            executorService.execute(() -> {
                log.info("[STREAM] Starting async memory extraction");
                autoMemoryExtractor.tryExtract(conversationId, userId);
            });

            String generatedTitle = tryGenerateTitle(conversationId, userId, userMessage, content, model);

            String doneData = "{\"messageId\": \"" + aiMessageId + "\""
                    + (generatedTitle != null ? ", \"title\": \"" + JsonUtils.escapeJson(generatedTitle) + "\"" : "")
                    + "}";
            emitter.send(SseEmitter.event().name("done").data(doneData));
            log.info("[STREAM] Sent 'done' event to client");
            emitter.complete();

            long totalTime = System.currentTimeMillis() - startTime;
            log.info("[STREAM] ===== Request Completed in {}ms ===== ", totalTime);
        } catch (Exception e) {
            log.error("[STREAM] Failed to finalize response", e);
        }
    }

    /**
     * 完成图像响应处理
     * 
     * @param conversationId 对话 ID
     * @param imageContent 图像内容（Base64 编码）
     * @param aiMessageId AI 消息 ID
     * @param emitter SSE Emitter
     * @param llmStartTime LLM 开始时间
     * @param startTime 请求开始时间
     */
    private String tryGenerateTitle(String conversationId, String userId, String userMessage,
            String aiResponse, String model) {
        try {
            var setting = userSettingService.getOrCreate(userId);
            if (!setting.getAutoTitle()) return null;

            var conv = conversationService.getConversation(conversationId);
            if (conv == null || !"新对话".equals(conv.getTitle())) return null;

            String title = titleGenerationService.generateTitle(userMessage, aiResponse, model);
            if (title.isBlank()) return null;

            conversationService.updateConversation(conversationId, title, null);
            log.info("[STREAM] Auto-generated title '{}' for conversation {}", title, conversationId);
            return title;
        } catch (Exception e) {
            log.warn("[STREAM] Title generation failed: {}", e.getMessage());
            return null;
        }
    }

    private void finalizeImageResponse(String conversationId, String imageContent, String aiMessageId,
            SseEmitter emitter, long llmStartTime, long startTime) {
        try {
            long llmEndTime = System.currentTimeMillis();
            log.info("[STREAM] LLM generation completed in {}ms", llmEndTime - llmStartTime);

            chatWorkflowService.updateShortTermMemoryWithAiMessage(conversationId, imageContent);
            messagePersistenceService.saveAiMessage(conversationId, aiMessageId, imageContent);
            emitter.send(SseEmitter.event().name("done").data("{\"messageId\": \"" + aiMessageId + "\"}"));
            emitter.complete();

            long totalTime = System.currentTimeMillis() - startTime;
            log.info("[STREAM] ===== Request Completed in {}ms ===== ", totalTime);
        } catch (Exception e) {
            log.error("[STREAM] Failed to finalize image generation response", e);
        }
    }
}
