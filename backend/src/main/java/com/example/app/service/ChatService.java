package com.example.app.service;

import com.example.app.client.OllamaClient;
import com.example.app.client.OpenAICompatibleClient;
import com.example.app.dto.ChatRequest;
import com.example.app.dto.ChatResponse;
import com.example.app.dto.MemoryDTO;
import com.example.app.entity.Message;
import com.example.app.entity.ModelConfig;
import com.example.app.pipeline.ContextPipelineExecutor;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.repository.MessageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 聊天服务类，负责处理同步聊天请求的完整流程
 * 
 * <功能说明>
 * - 核心职责：协调对话管理、记忆检索、LLM调用、消息持久化等环节
 * - 设计模式：编排器模式（Orchestrator Pattern），将多个服务组合成完整流程
 * - 依赖关系：依赖
 * OllamaClient、ChatWorkflowService、MessagePersistenceService、AutoMemoryExtractor
 * 
 * <使用场景>
 * - 同步消息响应：用户发送消息后等待完整回复
 * - 记忆增强对话：自动融合短期和长期记忆
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    /**
     * Ollama 客户端，用于调用本地 LLM 模型
     */
    private final OllamaClient ollamaClient;

    /**
     * 聊天流程编排服务，用于对话创建和重新生成
     */
    private final ChatWorkflowService chatWorkflowService;

    /**
     * 自动记忆提取器，重新生成时使用
     */
    private final AutoMemoryExtractor autoMemoryExtractor;

    /**
     * 用户配置服务，重新生成时使用
     */
    private final UserProfileService userProfileService;

    /**
     * 对话服务，重新生成时使用
     */
    private final ConversationService conversationService;

    /**
     * 消息数据访问层，重新生成时使用
     */
    private final MessageRepository messageRepository;

    /**
     * OpenAI 兼容客户端，重新生成时使用
     */
    private final OpenAICompatibleClient openAICompatibleClient;

    /**
     * 模型配置服务，重新生成时使用
     */
    private final ModelConfigService modelConfigService;

    /**
     * 事务模板，用于手动控制事务边界
     */
    private final TransactionTemplate transactionTemplate;

    /**
     * Context Pipeline 执行引擎 — 替代原有的逐步编排逻辑
     */
    private final ContextPipelineExecutor pipelineExecutor;

    /**
     * JSON 序列化/反序列化
     */
    private final ObjectMapper objectMapper;

    /**
     * 处理用户聊天请求，生成 AI 响应。
     *
     * 执行流程已迁移到 Context Pipeline：
     * 预处理 → 组装 → 模型路由 → 后处理，全部由 pipelineExecutor.execute() 驱动。
     *
     * @param request 聊天请求
     * @return 聊天响应
     */
    public ChatResponse generateResponse(ChatRequest request) {
        String conversationId = chatWorkflowService.getOrCreateConversationId(request);

        ConversationContext ctx = ConversationContext.fromRequest(request);
        ctx.setConversationId(conversationId);
        ctx.setPipelineType(ConversationContext.PipelineType.SIMPLE_CHAT);
        ctx.setMultimodal(request.isMultimodal());

        pipelineExecutor.execute(ctx);

        return ChatResponse.builder()
                .messageId(ctx.getAiMessageId())
                .content(ctx.getLlmResponse())
                .role("assistant")
                .conversationId(ctx.getConversationId())
                .title(ctx.getGeneratedTitle())
                .images(ctx.getArtifacts() != null
                        ? ctx.getArtifacts().stream()
                                .filter(a -> "image".equals(a.type()))
                                .map(a -> a.url())
                                .toList()
                        : null)
                .artifacts(ctx.getArtifacts())
                .build();
    }

    /**
     * 重新生成指定消息的响应
     *
     * @param conversationId 对话ID
     * @param messageId      要重新生成的消息ID（必须是AI消息）
     * @param model          模型名称
     * @param userId         用户ID
     * @return 重新生成的响应
     */
    public ChatResponse regenerateResponse(String conversationId, String messageId, String model, String userId) {
        log.info("[CHAT] Regenerating response for message {} in conversation {}", messageId, conversationId);

        Message targetMessage = messageRepository.findById(messageId).orElse(null);
        if (targetMessage == null) {
            throw new IllegalArgumentException("消息不存在: " + messageId);
        }

        if (!targetMessage.getConversationId().equals(conversationId)) {
            throw new IllegalArgumentException(
                    "消息不属于指定对话: messageId=" + messageId + ", conversationId=" + conversationId);
        }

        if (!"assistant".equals(targetMessage.getRole())) {
            throw new IllegalArgumentException("只能重新生成AI消息，当前消息角色: " + targetMessage.getRole());
        }

        List<Message> messagesBefore = messageRepository.findByConversationIdAndTimestampLessThanOrderByTimestampAsc(
                conversationId, targetMessage.getTimestamp());

        Message precedingUserMessage = null;
        for (int i = messagesBefore.size() - 1; i >= 0; i--) {
            if ("user".equals(messagesBefore.get(i).getRole())) {
                precedingUserMessage = messagesBefore.get(i);
                break;
            }
        }

        if (precedingUserMessage == null) {
            throw new IllegalArgumentException("未找到对应的用户消息");
        }

        String userMessage = precedingUserMessage.getContent();
        List<String> imageUrls = parseImageUrls(precedingUserMessage.getImages());

        // 事务内：删除后续消息 + 清除短期记忆缓存
        transactionTemplate.executeWithoutResult(status -> {
            messageRepository.deleteByConversationIdAndTimestampGreaterThan(
                    conversationId, targetMessage.getTimestamp());
            log.info("[CHAT] Deleted messages after {} in conversation {}", messageId, conversationId);
        });

        chatWorkflowService.clearShortTermMemory(conversationId);

        List<ChatMessage> shortTermMemory = chatWorkflowService.getShortTermMemory(conversationId);
        List<MemoryDTO> longTermMemory = chatWorkflowService.recallLongTermMemory(userId, userMessage, 5);
        log.debug("Recalled {} long-term memories for user {}", longTermMemory.size(), userId);

        String language = userProfileService.getLanguage(userId);
        List<ChatMessage> messages = chatWorkflowService.assembleMessages(shortTermMemory, longTermMemory, userMessage,
                language, null);

        String userMessageWithImages = buildMessageWithImages(userMessage, imageUrls);

        String aiResponse;
        ModelConfig customConfig = modelConfigService.getConfigByModelId(model);
        if (customConfig != null) {
            String actualModelId = model.substring(customConfig.getName().length() + 1);
            String systemPrompt = """
                    You are a helpful assistant. Answer the user's question in a friendly and natural way.
                    %s
                    """.formatted(language != null && !language.isEmpty() ? "Please respond in " + language + "." : "");
            aiResponse = openAICompatibleClient.chatCompletion(
                    actualModelId,
                    customConfig.getBaseUrl(),
                    customConfig.getApiKey(),
                    systemPrompt,
                    userMessageWithImages);
        } else {
            aiResponse = ollamaClient.generate(messages, model);
        }

        chatWorkflowService.updateShortTermMemory(conversationId, userMessage, aiResponse);

        transactionTemplate.executeWithoutResult(status -> {
            targetMessage.setContent(aiResponse);
            targetMessage.setTimestamp(LocalDateTime.now());
            messageRepository.save(targetMessage);
            log.info("[CHAT] Updated message {} with new content", messageId);
        });

        autoMemoryExtractor.tryExtract(conversationId, userId, model);

        return ChatResponse.builder()
                .messageId(messageId)
                .content(aiResponse)
                .role("assistant")
                .conversationId(conversationId)
                .build();
    }

    private List<String> parseImageUrls(String imagesJson) {
        if (imagesJson == null || imagesJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(imagesJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse image URLs from JSON: {}", imagesJson, e);
            return List.of();
        }
    }

    private String buildMessageWithImages(String userMessage, List<String> imageUrls) {
        if (imageUrls.isEmpty()) {
            return userMessage;
        }
        StringBuilder sb = new StringBuilder(userMessage);
        sb.append("\n\n[用户上传的图片]");
        for (String url : imageUrls) {
            sb.append("\n- ").append(url);
        }
        return sb.toString();
    }
}
