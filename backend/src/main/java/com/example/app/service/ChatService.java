package com.example.app.service;

import com.example.app.client.OllamaClient;
import com.example.app.client.OpenAICompatibleClient;
import com.example.app.config.WebSearchConfig;
import com.example.app.dto.ChatRequest;
import com.example.app.dto.ChatResponse;
import com.example.app.dto.MemoryDTO;
import com.example.app.dto.WebSearchResult;
import com.example.app.entity.Message;
import com.example.app.entity.ModelConfig;
import com.example.app.repository.MessageRepository;
import com.example.app.service.ModelConfigService;
import com.example.app.service.WebSearchService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
     * 聊天流程编排服务，负责记忆管理和消息组装
     */
    private final ChatWorkflowService chatWorkflowService;

    /**
     * 消息持久化服务，负责将消息保存到数据库
     */
    private final MessagePersistenceService messagePersistenceService;

    /**
     * 自动记忆提取器，对话完成后自动提取值得保存的信息
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
     * 消息数据访问层
     */
    private final MessageRepository messageRepository;

    /**
     * OpenAI 兼容客户端，用于调用云端模型
     */
    private final OpenAICompatibleClient openAICompatibleClient;

    /**
     * 模型配置服务，用于查询自定义模型配置
     */
    private final ModelConfigService modelConfigService;

    /**
     * 事务模板，用于手动控制事务边界
     */
    private final TransactionTemplate transactionTemplate;

    /**
     * JSON 序列化/反序列化
     */
    private final ObjectMapper objectMapper;

    /**
     * 处理用户聊天请求，生成 AI 响应
     * 
     * 执行流程：
     * 1. 获取或创建对话 ID
     * 2. 检索短期记忆（对话历史）
     * 3. 检索长期记忆（基于语义相似度）
     * 4. 组装消息为 LLM 格式
     * 5. 调用 LLM 生成响应
     * 6. 更新短期记忆
     * 7. 持久化消息
     * 8. 尝试提取新记忆
     * 
     * @param request 聊天请求，包含消息内容、模型选择、用户ID等
     * @return 聊天响应，包含 AI 回复内容和消息元数据
     */
    public ChatResponse generateResponse(ChatRequest request) {
        // 1. 获取或创建对话 ID
        String conversationId = chatWorkflowService.getOrCreateConversationId(request);
        String userMessage = request.getMessage();
        String model = request.getModel();
        String userId = request.getUserId() != null ? request.getUserId() : "default";

        // 2. 检索短期记忆（对话历史）
        List<ChatMessage> shortTermMemory = chatWorkflowService.getShortTermMemory(conversationId);

        // 3. 检索长期记忆（基于语义相似度召回）
        List<MemoryDTO> longTermMemory = chatWorkflowService.recallLongTermMemory(userId, userMessage, 5);
        log.debug("Recalled {} long-term memories for user {}", longTermMemory.size(), userId);

        // 4. 网络搜索
        String searchContext = null;
        if (request.isWebSearch() && webSearchConfig.isEnabled()) {
            try {
                WebSearchResult searchResult = webSearchService.search(userMessage);
                if (!searchResult.getSnippets().isEmpty()) {
                    searchContext = searchResult.getSnippets().stream()
                            .map(s -> "- [" + s.getTitle() + "](" + s.getUrl() + "): " + s.getSnippet())
                            .collect(Collectors.joining("\n"));
                }
            } catch (Exception e) {
                log.warn("Web search failed: {}", e.getMessage());
            }
        }

        // 5. 查询用户语言偏好，组装消息为 LLM 可理解的格式
        String language = userProfileService.getLanguage(userId);
        List<ChatMessage> messages = chatWorkflowService.assembleMessages(shortTermMemory, longTermMemory, userMessage,
                language, searchContext);

        // 5. 调用 LLM 生成响应
        String aiResponse = ollamaClient.generate(messages, model);

        // 6. 更新短期记忆
        chatWorkflowService.updateShortTermMemory(conversationId, userMessage, aiResponse);

        // 7. 持久化消息到数据库
        String aiMessageId = messagePersistenceService.saveMessages(conversationId, userMessage, aiResponse,
                request.getImageUrls());

        // 8. 异步尝试从对话中提取新记忆
        autoMemoryExtractor.tryExtract(conversationId, userId);

        // 9. 尝试生成标题
        String generatedTitle = tryGenerateTitle(conversationId, userId, userMessage, aiResponse, model);

        return ChatResponse.builder()
                .messageId(aiMessageId)
                .content(aiResponse)
                .role("assistant")
                .conversationId(conversationId)
                .title(generatedTitle)
                .build();
    }

    private String tryGenerateTitle(String conversationId, String userId, String userMessage,
            String aiResponse, String model) {
        try {
            var setting = userSettingService.getOrCreate(userId);
            if (!setting.getAutoTitle())
                return null;

            var conv = conversationService.getConversation(conversationId);
            if (conv == null || !"新对话".equals(conv.getTitle()))
                return null;

            String title = titleGenerationService.generateTitle(userMessage, aiResponse, model);
            if (title.isBlank())
                return null;

            conversationService.updateConversation(conversationId, title, null);
            log.info("[CHAT] Auto-generated title '{}' for conversation {}", title, conversationId);
            return title;
        } catch (Exception e) {
            log.warn("[CHAT] Title generation failed: {}", e.getMessage());
            return null;
        }
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

        autoMemoryExtractor.tryExtract(conversationId, userId);

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
