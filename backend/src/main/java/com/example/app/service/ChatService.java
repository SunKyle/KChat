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
import dev.langchain4j.data.message.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
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
    @Transactional
    public ChatResponse regenerateResponse(String conversationId, String messageId, String model, String userId) {
        log.info("[CHAT] Regenerating response for message {} in conversation {}", messageId, conversationId);

        // 1. 获取目标消息
        Message targetMessage = messageRepository.findById(messageId).orElse(null);
        if (targetMessage == null) {
            throw new IllegalArgumentException("消息不存在: " + messageId);
        }

        // 2. 验证目标消息是AI消息
        if (!"assistant".equals(targetMessage.getRole())) {
            throw new IllegalArgumentException("只能重新生成AI消息，当前消息角色: " + targetMessage.getRole());
        }

        // 3. 查找该消息之前的最后一条用户消息
        List<Message> messagesBefore = messageRepository.findByConversationIdAndTimestampLessThanOrderByTimestampAsc(
                conversationId, targetMessage.getTimestamp());

        String userMessage = null;
        for (int i = messagesBefore.size() - 1; i >= 0; i--) {
            if ("user".equals(messagesBefore.get(i).getRole())) {
                userMessage = messagesBefore.get(i).getContent();
                break;
            }
        }

        if (userMessage == null) {
            throw new IllegalArgumentException("未找到对应的用户消息");
        }

        // 4. 删除目标消息之后的所有消息（保留目标消息以便更新）
        messageRepository.deleteByConversationIdAndTimestampGreaterThan(
                conversationId, targetMessage.getTimestamp());
        log.info("[CHAT] Deleted messages after {} in conversation {}", messageId, conversationId);

        // 5. 获取短期记忆（对话历史）
        List<ChatMessage> shortTermMemory = chatWorkflowService.getShortTermMemory(conversationId);

        // 6. 检索长期记忆
        List<MemoryDTO> longTermMemory = chatWorkflowService.recallLongTermMemory(userId, userMessage, 5);
        log.debug("Recalled {} long-term memories for user {}", longTermMemory.size(), userId);

        // 7. 查询用户语言偏好，组装消息为 LLM 可理解的格式
        String language = userProfileService.getLanguage(userId);
        List<ChatMessage> messages = chatWorkflowService.assembleMessages(shortTermMemory, longTermMemory, userMessage,
                language, null);

        // 8. 调用 LLM 生成响应（支持 OpenAI 和 Ollama 模型）
        String aiResponse;
        ModelConfig customConfig = modelConfigService.getConfigByModelId(model);
        if (customConfig != null) {
            // 自定义/云端模型 (OpenAI, Anthropic, etc.)
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
                    userMessage);
        } else {
            // Ollama 本地模型
            aiResponse = ollamaClient.generate(messages, model);
        }

        // 9. 更新短期记忆
        chatWorkflowService.updateShortTermMemory(conversationId, userMessage, aiResponse);

        // 10. 更新原消息内容（使用相同的消息ID）
        targetMessage.setContent(aiResponse);
        targetMessage.setTimestamp(LocalDateTime.now());
        messageRepository.save(targetMessage);
        log.info("[CHAT] Updated message {} with new content", messageId);

        // 11. 异步尝试从对话中提取新记忆
        autoMemoryExtractor.tryExtract(conversationId, userId);

        return ChatResponse.builder()
                .messageId(messageId) // 返回原消息ID，便于前端更新
                .content(aiResponse)
                .role("assistant")
                .conversationId(conversationId)
                .build();
    }
}
