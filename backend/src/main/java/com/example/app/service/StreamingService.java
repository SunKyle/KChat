package com.example.app.service;

import com.example.app.client.OllamaClient;
import com.example.app.client.OpenAICompatibleClient;
import com.example.app.dto.ChatRequest;
import com.example.app.dto.MemoryDTO;
import com.example.app.entity.ModelConfig;
import com.example.app.util.JsonUtils;
import com.example.app.util.PromptAssembler;
import dev.langchain4j.data.message.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
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
    private final MemoryService memoryService;
    private final MessagePersistenceService messagePersistenceService;
    private final ConversationService conversationService;
    private final ExecutorService executorService;
    private final PromptAssembler promptAssembler;
    private final AutoMemoryExtractor autoMemoryExtractor;

    public SseEmitter streamResponse(ChatRequest request) {
        long startTime = System.currentTimeMillis();
        SseEmitter emitter = new SseEmitter(300000L);

        emitter.onCompletion(() -> {
            log.info("[STREAM] SSE emitter completed");
        });

        emitter.onTimeout(() -> {
            log.warn("[STREAM] SSE emitter timeout");
            emitter.complete();
        });

        emitter.onError(e -> {
            log.error("[STREAM] SSE emitter error: {}", e.getMessage());
        });

        String conversationId = request.getConversationId();

        if (conversationId == null || conversationId.isBlank()) {
            conversationId = conversationService.createConversation("新对话").getId();
            log.info("[STREAM] Created new conversation: {}", conversationId);
        } else {
            log.info("[STREAM] Using existing conversation: {}", conversationId);
        }

        final String finalConversationId = conversationId;
        final String userMessage = request.getMessage();
        final String aiMessageId = UUID.randomUUID().toString();
        final List<String> imageUrls = request.getImageUrls();
        final String userId = request.getUserId() != null ? request.getUserId() : "default";

        log.info("[STREAM] ===== Start Processing Request ===== ");
        log.info("[STREAM] Conversation: {}, User: {}, Model: {}", conversationId, userId, request.getModel());
        log.info("[STREAM] User message length: {} chars", userMessage.length());
        log.info("[STREAM] Has images: {}", imageUrls != null && !imageUrls.isEmpty());

        log.info("[STREAM] Step 1/5: Updating short-term memory with user message");
        memoryService.updateMemoryWithUserMessage(finalConversationId, userMessage);

        log.info("[STREAM] Step 2/5: Saving user message to database");
        messagePersistenceService.saveUserMessage(finalConversationId, userMessage, imageUrls);

        final String model = request.getModel();

        log.info("[STREAM] Step 3/5: Recalling long-term memory");
        final List<MemoryDTO> longTermMemory = memoryService.recallLongTermMemory(userId, userMessage, 5);
        log.info("[STREAM] Recalled {} long-term memory items for user {}", longTermMemory.size(), userId);

        executorService.execute(() -> {
            StringBuilder fullResponse = new StringBuilder();
            final boolean[] completed = { false };

            try {
                long llmStartTime = System.currentTimeMillis();
                log.info("[STREAM] Step 4/5: Starting LLM generation...");

                ModelConfig customConfig = modelConfigService.getConfigByModelId(model);

                if (customConfig != null) {
                    log.info("[STREAM] Using custom model config: {}", customConfig.getName());

                    try {
                        String actualModelId = model.substring(customConfig.getName().length() + 1);

                        if (openAICompatibleClient.isImageModel(actualModelId)) {
                            log.info("[STREAM] Detected image generation model: {}", actualModelId);

                            openAICompatibleClient.generateImage(
                                    actualModelId,
                                    customConfig.getBaseUrl(),
                                    customConfig.getApiKey(),
                                    userMessage,
                                    emitter,
                                    imageContent -> {
                                        try {
                                            long llmEndTime = System.currentTimeMillis();
                                            log.info("[STREAM] LLM generation completed in {}ms",
                                                    llmEndTime - llmStartTime);

                                            log.info("[STREAM] Step 5/5: Finalizing response");
                                            memoryService.updateMemoryWithAiMessage(finalConversationId, imageContent);
                                            messagePersistenceService.saveAiMessage(finalConversationId, aiMessageId,
                                                    imageContent);
                                            emitter.send(SseEmitter.event().name("done")
                                                    .data("{\"messageId\": \"" + aiMessageId + "\"}"));
                                            emitter.complete();

                                            long totalTime = System.currentTimeMillis() - startTime;
                                            log.info("[STREAM] ===== Request Completed in {}ms ===== ", totalTime);
                                        } catch (Exception e) {
                                            log.error("[STREAM] Failed to finalize image generation response", e);
                                        }
                                    });
                        } else {
                            openAICompatibleClient.streamChatCompletion(
                                    actualModelId,
                                    customConfig.getBaseUrl(),
                                    customConfig.getApiKey(),
                                    userMessage,
                                    imageUrls,
                                    emitter,
                                    chunk -> fullResponse.append(chunk),
                                    () -> {
                                        try {
                                            long llmEndTime = System.currentTimeMillis();
                                            log.info("[STREAM] LLM generation completed in {}ms",
                                                    llmEndTime - llmStartTime);

                                            log.info("[STREAM] Step 5/5: Finalizing response");
                                            memoryService.updateMemoryWithAiMessage(finalConversationId,
                                                    fullResponse.toString());
                                            messagePersistenceService.saveAiMessage(finalConversationId, aiMessageId,
                                                    fullResponse.toString());

                                            executorService.execute(() -> {
                                                log.info("[STREAM] Starting async memory extraction");
                                                autoMemoryExtractor.tryExtract(finalConversationId, userId);
                                            });

                                            emitter.send(SseEmitter.event().name("done")
                                                    .data("{\"messageId\": \"" + aiMessageId + "\"}"));
                                            emitter.complete();

                                            long totalTime = System.currentTimeMillis() - startTime;
                                            log.info("[STREAM] ===== Request Completed in {}ms ===== ", totalTime);
                                        } catch (Exception e) {
                                            log.error("[STREAM] Failed to finalize custom model response", e);
                                        }
                                    });
                        }
                    } catch (StringIndexOutOfBoundsException e) {
                        log.error("[STREAM] Invalid model ID format: {}", model, e);
                        emitter.completeWithError(new RuntimeException("无效的模型ID格式: " + model));
                    } catch (Exception e) {
                        log.error("[STREAM] Failed to process custom model request", e);
                        if (!completed[0]) {
                            try {
                                emitter.completeWithError(e);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    return;
                }

                List<ChatMessage> shortTermMemory = memoryService.getMemoryContext(finalConversationId);
                List<ChatMessage> messages = promptAssembler.assemble(shortTermMemory, longTermMemory, userMessage);
                log.info("[STREAM] Assembled {} messages for LLM ({} short-term + {} long-term)",
                        messages.size(), shortTermMemory.size(), longTermMemory.size());

                boolean hasImages = imageUrls != null && !imageUrls.isEmpty();

                if (hasImages) {
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

                if (completed[0])
                    return;

                long llmEndTime = System.currentTimeMillis();
                log.info("[STREAM] LLM generation completed in {}ms", llmEndTime - llmStartTime);

                log.info("[STREAM] Step 5/5: Finalizing response");
                memoryService.updateMemoryWithAiMessage(finalConversationId, fullResponse.toString());
                messagePersistenceService.saveAiMessage(finalConversationId, aiMessageId, fullResponse.toString());
                log.info("[STREAM] AI response saved: {} chars", fullResponse.length());

                executorService.execute(() -> {
                    log.info("[STREAM] Starting async memory extraction");
                    autoMemoryExtractor.tryExtract(finalConversationId, userId);
                });

                try {
                    emitter.send(SseEmitter.event().name("done").data("{\"messageId\": \"" + aiMessageId + "\"}"));
                    log.info("[STREAM] Sent 'done' event to client");
                } catch (Exception e) {
                    log.error("[STREAM] Failed to send 'done' event", e);
                    return;
                }

                emitter.complete();

                long totalTime = System.currentTimeMillis() - startTime;
                log.info("[STREAM] ===== Request Completed in {}ms ===== ", totalTime);

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
}