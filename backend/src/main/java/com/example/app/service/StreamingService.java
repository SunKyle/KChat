package com.example.app.service;

import com.example.app.client.OllamaClient;
import com.example.app.client.OpenAICompatibleClient;
import com.example.app.dto.ChatRequest;
import com.example.app.dto.MemoryDTO;
import com.example.app.entity.ModelConfig;
import com.example.app.util.JsonUtils;
import dev.langchain4j.data.message.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

@Service
@RequiredArgsConstructor
@Slf4j
public class StreamingService {

    private final OllamaClient ollamaClient;
    private final OpenAICompatibleClient openAICompatibleClient;
    private final ModelConfigService modelConfigService;
    private final ChatWorkflowService chatWorkflowService;
    private final MessagePersistenceService messagePersistenceService;
    private final ExecutorService executorService;
    private final AutoMemoryExtractor autoMemoryExtractor;

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

        log.info("[STREAM] ===== Start Processing Request ===== ");
        log.info("[STREAM] Conversation: {}, User: {}, Model: {}", conversationId, userId, model);
        log.info("[STREAM] User message length: {} chars", userMessage.length());

        chatWorkflowService.updateShortTermMemoryWithUserMessage(finalConversationId, userMessage);
        messagePersistenceService.saveUserMessage(finalConversationId, userMessage, imageUrls);

        final List<MemoryDTO> longTermMemory = chatWorkflowService.recallLongTermMemory(userId, userMessage, 5);
        log.info("[STREAM] Recalled {} long-term memory items", longTermMemory.size());

        executorService.execute(() -> {
            StringBuilder fullResponse = new StringBuilder();
            final boolean[] completed = { false };

            try {
                long llmStartTime = System.currentTimeMillis();
                log.info("[STREAM] Starting LLM generation...");

                ModelConfig customConfig = modelConfigService.getConfigByModelId(model);

                if (customConfig != null) {
                    handleCustomModel(customConfig, model, userMessage, imageUrls, emitter,
                            finalConversationId, aiMessageId, userId, fullResponse, completed, llmStartTime, startTime);
                    return;
                }

                List<ChatMessage> shortTermMemory = chatWorkflowService.getShortTermMemory(finalConversationId);
                List<ChatMessage> messages = chatWorkflowService.assembleMessages(
                        shortTermMemory, longTermMemory, userMessage);

                boolean hasImages = imageUrls != null && !imageUrls.isEmpty();
                streamOllamaResponse(messages, imageUrls, model, emitter, fullResponse, completed);

                if (completed[0])
                    return;

                long llmEndTime = System.currentTimeMillis();
                log.info("[STREAM] LLM generation completed in {}ms", llmEndTime - llmStartTime);

                finalizeResponse(finalConversationId, fullResponse.toString(), aiMessageId,
                        userId, emitter, llmStartTime, startTime);

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

    private void handleCustomModel(ModelConfig config, String modelId, String userMessage,
            List<String> imageUrls, SseEmitter emitter, String conversationId, String aiMessageId,
            String userId, StringBuilder fullResponse, boolean[] completed, long llmStartTime, long startTime) {

        try {
            String actualModelId = modelId.substring(config.getName().length() + 1);

            if (openAICompatibleClient.isImageModel(actualModelId)) {
                log.info("[STREAM] Detected image generation model: {}", actualModelId);
                openAICompatibleClient.generateImage(
                        actualModelId, config.getBaseUrl(), config.getApiKey(), userMessage, emitter,
                        imageContent -> finalizeImageResponse(conversationId, imageContent, aiMessageId,
                                emitter, llmStartTime, startTime));
            } else {
                openAICompatibleClient.streamChatCompletion(
                        actualModelId, config.getBaseUrl(), config.getApiKey(), userMessage,
                        imageUrls, emitter, fullResponse::append,
                        () -> finalizeResponse(conversationId, fullResponse.toString(), aiMessageId,
                                userId, emitter, llmStartTime, startTime));
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

    private void finalizeResponse(String conversationId, String content, String aiMessageId,
            String userId, SseEmitter emitter, long llmStartTime, long startTime) {
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

            emitter.send(SseEmitter.event().name("done").data("{\"messageId\": \"" + aiMessageId + "\"}"));
            log.info("[STREAM] Sent 'done' event to client");
            emitter.complete();

            long totalTime = System.currentTimeMillis() - startTime;
            log.info("[STREAM] ===== Request Completed in {}ms ===== ", totalTime);
        } catch (Exception e) {
            log.error("[STREAM] Failed to finalize response", e);
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
