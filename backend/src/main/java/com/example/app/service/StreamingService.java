package com.example.app.service;

import com.example.app.client.OllamaClient;
import com.example.app.client.OpenAICompatibleClient;
import com.example.app.dto.ChatRequest;
import com.example.app.entity.ModelConfig;
import com.example.app.util.JsonUtils;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
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

    public SseEmitter streamResponse(ChatRequest request) {
        SseEmitter emitter = new SseEmitter(300000L);

        emitter.onCompletion(() -> {
            System.out.println("SSE emitter completed");
        });

        emitter.onTimeout(() -> {
            System.out.println("SSE emitter timeout");
            emitter.complete();
        });

        emitter.onError(e -> {
            System.out.println("SSE emitter error: " + e.getMessage());
        });

        String conversationId = request.getConversationId();

        if (conversationId == null || conversationId.isBlank()) {
            conversationId = conversationService.createConversation("新对话").getId();
        }

        final String finalConversationId = conversationId;
        final String userMessage = request.getMessage();
        final String aiMessageId = UUID.randomUUID().toString();
        final List<String> imageUrls = request.getImageUrls();

        memoryService.updateMemoryWithUserMessage(finalConversationId, userMessage);
        messagePersistenceService.saveUserMessage(finalConversationId, userMessage, imageUrls);

        final String model = request.getModel();

        executorService.execute(() -> {
            StringBuilder fullResponse = new StringBuilder();
            final boolean[] completed = { false };

            try {
                ModelConfig customConfig = modelConfigService.getConfigByModelId(model);

                if (customConfig != null) {
                    log.info("Using custom model config: {}", customConfig.getName());
                    String actualModelId = model.substring(customConfig.getName().length() + 1);
                    openAICompatibleClient.streamChatCompletion(
                            actualModelId,
                            customConfig.getBaseUrl(),
                            customConfig.getApiKey(),
                            userMessage,
                            emitter);
                    return;
                }

                List<ChatMessage> context = memoryService.getMemoryContext(finalConversationId);
                List<ChatMessage> messages = new ArrayList<>(context);
                messages.add(UserMessage.from(userMessage));

                boolean hasImages = imageUrls != null && !imageUrls.isEmpty();

                if (hasImages) {
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

                memoryService.updateMemoryWithAiMessage(finalConversationId, fullResponse.toString());
                messagePersistenceService.saveAiMessage(finalConversationId, aiMessageId, fullResponse.toString());

                try {
                    emitter.send(SseEmitter.event().name("done").data("{\"messageId\": \"" + aiMessageId + "\"}"));
                } catch (Exception e) {
                    return;
                }

                emitter.complete();

            } catch (Exception e) {
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
