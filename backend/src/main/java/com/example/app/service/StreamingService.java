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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * 流式响应服务
 *
 * 核心职责：
 * - 通过 SSE（Server-Sent Events）实现实时响应推送
 * - 支持 Ollama 本地模型和自定义远程模型（OpenAI 兼容）
 * - 支持文本聊天和图片生成两种模式
 * - 异步处理，不阻塞 HTTP 请求线程
 *
 * 并发设计：
 * - 提交任务到 executorService 异步执行，立即返回 SseEmitter
 * - 使用 final 变量传递上下文到匿名内部类
 * - 通过 boolean[] completed 标志防止重复完成
 */
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

    /**
     * 流式处理聊天请求并返回 SSE 响应
     *
     * 处理流程：
     * 1. 创建/获取对话会话
     * 2. 更新短期记忆并保存用户消息
     * 3. 语义召回长期记忆
     * 4. 提交异步任务执行 LLM 生成
     * 5. 通过 SSE 逐块推送响应
     * 6. 完成后保存完整响应并触发记忆提取
     *
     * 异常处理：
     * - 设置 5 分钟超时（300000ms）防止连接长时间占用
     * - 通过 completed 标志避免重复完成 emitter
     * - 异常时调用 completeWithError 通知客户端
     *
     * @param request 聊天请求，包含消息、对话ID、模型、图片URL等
     * @return SseEmitter 用于流式发送响应的发射器
     */
    public SseEmitter streamResponse(ChatRequest request) {
        // 记录请求开始时间
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

        // 声明为 final 以便在匿名内部类中访问
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

        /**
         * 将 LLM 生成任务提交到线程池异步执行
         * 设计原因：
         * - 避免阻塞 HTTP 请求线程，提高系统吞吐量
         * - SSE 连接可能持续较长时间，异步执行不会占用 Servlet 线程
         */
        executorService.execute(() -> {
            StringBuilder fullResponse = new StringBuilder();
            // 使用数组包装 boolean 以便在匿名内部类中修改
            final boolean[] completed = { false };

            try {
                long llmStartTime = System.currentTimeMillis();
                log.info("[STREAM] Step 4/5: Starting LLM generation...");

                /**
                 * 尝试获取自定义模型配置
                 * 模型ID格式：{configName}:{actualModelId}
                 * 例如：openai:gpt-4, anthropic:claude-3
                 */
                ModelConfig customConfig = modelConfigService.getConfigByModelId(model);

                if (customConfig != null) {
                    log.info("[STREAM] Using custom model config: {}", customConfig.getName());

                    try {
                        // 提取实际的模型ID（冒号后面的部分）
                        String actualModelId = model.substring(customConfig.getName().length() + 1);

                        /**
                         * 判断是否为图片生成模型
                         * 如果是，调用图片生成接口而非文本聊天接口
                         */
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

                /**
                 * 使用 Ollama 本地模型进行流式生成
                 * 先组装消息：短期记忆 + 长期记忆 + 当前输入
                 */
                List<ChatMessage> shortTermMemory = memoryService.getMemoryContext(finalConversationId);
                List<ChatMessage> messages = promptAssembler.assemble(shortTermMemory, longTermMemory, userMessage);
                log.info("[STREAM] Assembled {} messages for LLM ({} short-term + {} long-term)",
                        messages.size(), shortTermMemory.size(), longTermMemory.size());

                boolean hasImages = imageUrls != null && !imageUrls.isEmpty();

                /**
                 * 根据是否包含图片选择不同的生成方法
                 * - 有图片：使用 streamGenerateWithImages
                 * - 无图片：使用 streamGenerate
                 *
                 * 流式回调处理：
                 * 1. 检查 completed 标志，避免重复处理
                 * 2. 累积完整响应到 fullResponse
                 * 3. 通过 SSE 发送消息块到客户端
                 * 4. 发送失败时标记 completed 为 true
                 */
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

                /**
                 * 响应完成后的收尾工作：
                 * 1. 更新短期记忆（添加 AI 回复）
                 * 2. 持久化 AI 消息到数据库
                 * 3. 异步触发长期记忆提取（不阻塞响应）
                 * 4. 发送 'done' 事件通知客户端
                 * 5. 完成 SSE 连接
                 */
                log.info("[STREAM] Step 5/5: Finalizing response");
                memoryService.updateMemoryWithAiMessage(finalConversationId, fullResponse.toString());
                messagePersistenceService.saveAiMessage(finalConversationId, aiMessageId, fullResponse.toString());
                log.info("[STREAM] AI response saved: {} chars", fullResponse.length());

                /**
                 * 异步触发记忆提取
                 * 设计原因：
                 * - 记忆提取调用 LLM，耗时较长
                 * - 不应阻塞客户端收到完整响应
                 * - 用户体验优先，知识积累后置
                 */
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

        /**
         * 立即返回 SseEmitter，HTTP 响应立即建立连接
         * 实际的 LLM 生成在后台线程中执行
         */
        return emitter;
    }
}